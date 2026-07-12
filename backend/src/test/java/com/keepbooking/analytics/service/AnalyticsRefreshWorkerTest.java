package com.keepbooking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.analytics.model.AnalyticsRefreshState;
import com.keepbooking.analytics.model.RestaurantDailyStats;
import com.keepbooking.analytics.repository.AnalyticsRefreshStateRepository;
import com.keepbooking.analytics.repository.RestaurantDailyHourStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyTableStatsRepository;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;

/**
 * tz2.txt §15/§25: the refresh worker recomputes only the (restaurant, date) pairs touched since
 * the last watermark, and upserts (not blindly inserts) the daily-stats row when one already
 * exists for that pair.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsRefreshWorkerTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private RestaurantDailyStatsRepository dailyStatsRepository;
    @Mock
    private RestaurantDailyHourStatsRepository hourStatsRepository;
    @Mock
    private RestaurantDailyTableStatsRepository tableStatsRepository;
    @Mock
    private AnalyticsRefreshStateRepository refreshStateRepository;

    private AnalyticsRefreshWorker worker;

    private static final Long RESTAURANT_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        worker = new AnalyticsRefreshWorker(bookingRepository, restaurantRepository, tableRepository,
                dailyStatsRepository, hourStatsRepository, tableStatsRepository, refreshStateRepository);
    }

    @Test
    void doesNothingAndStillAdvancesWatermarkWhenNothingIsDirty() {
        AnalyticsRefreshState state = AnalyticsRefreshState.builder().id(1L).lastRefreshedAt(Instant.EPOCH).build();
        when(refreshStateRepository.findAll()).thenReturn(List.of(state));
        when(bookingRepository.findDirtyRestaurantDatesSince(Instant.EPOCH)).thenReturn(List.of());

        worker.refresh();

        verify(dailyStatsRepository, never()).save(any());
        ArgumentCaptor<AnalyticsRefreshState> saved = ArgumentCaptor.forClass(AnalyticsRefreshState.class);
        verify(refreshStateRepository).save(saved.capture());
        assertThat(saved.getValue().getLastRefreshedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void createsWatermarkRowWhenNoneExistsYet() {
        when(refreshStateRepository.findAll()).thenReturn(List.of());
        AnalyticsRefreshState created = AnalyticsRefreshState.builder().id(1L).lastRefreshedAt(Instant.EPOCH).build();
        when(refreshStateRepository.save(any(AnalyticsRefreshState.class))).thenReturn(created);
        when(bookingRepository.findDirtyRestaurantDatesSince(any())).thenReturn(List.of());

        worker.refresh();

        verify(refreshStateRepository, times(2)).save(any(AnalyticsRefreshState.class));
    }

    @Test
    void recomputesAndUpsertsExistingDailyStatsForADirtyPair() {
        AnalyticsRefreshState state = AnalyticsRefreshState.builder().id(1L).lastRefreshedAt(Instant.EPOCH).build();
        when(refreshStateRepository.findAll()).thenReturn(List.of(state));
        when(bookingRepository.findDirtyRestaurantDatesSince(Instant.EPOCH))
                .thenReturn(List.<Object[]>of(new Object[]{RESTAURANT_ID, DATE}));

        RestaurantDailyStats existing = RestaurantDailyStats.builder().id(5L)
                .restaurant(Restaurant.builder().id(RESTAURANT_ID).build()).statDate(DATE)
                .pendingCount(9).build();
        when(dailyStatsRepository.findByRestaurantIdAndStatDate(RESTAURANT_ID, DATE)).thenReturn(Optional.of(existing));
        when(bookingRepository.countByStatusForRestaurantAndDate(RESTAURANT_ID, DATE)).thenReturn(List.of(
                new Object[]{BookingStatus.CONFIRMED, 3L},
                new Object[]{BookingStatus.CANCELLED, 1L}
        ));
        when(bookingRepository.countByHourForRestaurantAndDate(RESTAURANT_ID, DATE))
                .thenReturn(List.<Object[]>of(new Object[]{19, 2L}));
        when(bookingRepository.countByTableForRestaurantAndDate(RESTAURANT_ID, DATE))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 2L}));
        when(restaurantRepository.getReferenceById(RESTAURANT_ID)).thenReturn(Restaurant.builder().id(RESTAURANT_ID).build());

        worker.refresh();

        ArgumentCaptor<RestaurantDailyStats> statsCaptor = ArgumentCaptor.forClass(RestaurantDailyStats.class);
        verify(dailyStatsRepository).save(statsCaptor.capture());
        RestaurantDailyStats savedStats = statsCaptor.getValue();
        assertThat(savedStats.getId()).isEqualTo(5L);
        assertThat(savedStats.getConfirmedCount()).isEqualTo(3);
        assertThat(savedStats.getCancelledCount()).isEqualTo(1);
        // stale from before this recompute - not in the fresh countByStatusForRestaurantAndDate result
        assertThat(savedStats.getPendingCount()).isEqualTo(0);

        verify(hourStatsRepository).deleteByRestaurantIdAndStatDate(RESTAURANT_ID, DATE);
        verify(hourStatsRepository).save(any());
        verify(tableStatsRepository).deleteByRestaurantIdAndStatDate(RESTAURANT_ID, DATE);
        verify(tableStatsRepository).save(any());
    }
}
