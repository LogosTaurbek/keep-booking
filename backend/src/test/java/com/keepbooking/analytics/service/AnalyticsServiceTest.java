package com.keepbooking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.analytics.dto.RestaurantAnalyticsDto;
import com.keepbooking.analytics.repository.RestaurantDailyHourStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyTableStatsRepository;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;

/**
 * tz2.txt §15/§25: AnalyticsService reads from the daily-rollup read model (status sums, hour/
 * table breakdowns), not the live bookings table - AnalyticsRefreshWorkerTest covers how those
 * rollups themselves get populated.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RestaurantDailyStatsRepository dailyStatsRepository;
    @Mock
    private RestaurantDailyHourStatsRepository hourStatsRepository;
    @Mock
    private RestaurantDailyTableStatsRepository tableStatsRepository;

    private AnalyticsService analyticsService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESTAURANT_ID = 10L;
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(restaurantRepository, bookingRepository,
                dailyStatsRepository, hourStatsRepository, tableStatsRepository);
    }

    private Restaurant restaurantOwnedBy(Long ownerId) {
        Company company = Company.builder().id(1L).owner(User.builder().id(ownerId).build()).name("Co").build();
        return Restaurant.builder().id(RESTAURANT_ID).company(company).name("Test Restaurant").build();
    }

    @Test
    void throwsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsService.getRestaurantAnalytics(OWNER_ID, RESTAURANT_ID, FROM, TO))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void throwsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> analyticsService.getRestaurantAnalytics(OTHER_USER_ID, RESTAURANT_ID, FROM, TO))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throwsValidationErrorWhenFromIsAfterTo() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> analyticsService.getRestaurantAnalytics(OWNER_ID, RESTAURANT_ID, TO, FROM))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void aggregatesStatusCountsAndComputesConfirmationRate() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        // pending, confirmed, rejected, cancelled, completed, noShow
        when(dailyStatsRepository.sumStatusCounts(eq(RESTAURANT_ID), eq(FROM), eq(TO)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 3L, 1L, 1L, 4L, 1L}));
        when(hourStatsRepository.findPopularHours(eq(RESTAURANT_ID), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(List.of(new Object[]{19, 5L}, new Object[]{20, 3L}));
        when(tableStatsRepository.findPopularTables(eq(RESTAURANT_ID), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{100L, "A1", 4L}));
        when(bookingRepository.countDistinctGuestsForRestaurant(RESTAURANT_ID, FROM, TO)).thenReturn(9L);

        RestaurantAnalyticsDto dto = analyticsService.getRestaurantAnalytics(OWNER_ID, RESTAURANT_ID, FROM, TO);

        assertThat(dto.getTotalBookings()).isEqualTo(12);
        assertThat(dto.getPendingBookings()).isEqualTo(2);
        assertThat(dto.getConfirmedBookings()).isEqualTo(3);
        assertThat(dto.getRejectedBookings()).isEqualTo(1);
        assertThat(dto.getCancelledBookings()).isEqualTo(1);
        assertThat(dto.getCompletedBookings()).isEqualTo(4);
        assertThat(dto.getNoShowBookings()).isEqualTo(1);
        // left PENDING = 10, reached-confirmed (CONFIRMED+COMPLETED+NO_SHOW) = 8 -> 0.8
        assertThat(dto.getConfirmationRate()).isEqualTo(0.8);
        assertThat(dto.getUniqueGuests()).isEqualTo(9);
        assertThat(dto.getPopularHours()).hasSize(2);
        assertThat(dto.getPopularHours().get(0).getHour()).isEqualTo(19);
        assertThat(dto.getPopularHours().get(0).getCount()).isEqualTo(5);
        assertThat(dto.getPopularTables()).hasSize(1);
        assertThat(dto.getPopularTables().get(0).getTableNumber()).isEqualTo("A1");
    }

    @Test
    void confirmationRateIsZeroWhenAllBookingsAreStillPending() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(dailyStatsRepository.sumStatusCounts(eq(RESTAURANT_ID), eq(FROM), eq(TO)))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 0L, 0L, 0L, 0L, 0L}));
        when(hourStatsRepository.findPopularHours(eq(RESTAURANT_ID), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(List.of());
        when(tableStatsRepository.findPopularTables(eq(RESTAURANT_ID), eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(List.of());
        when(bookingRepository.countDistinctGuestsForRestaurant(RESTAURANT_ID, FROM, TO)).thenReturn(3L);

        RestaurantAnalyticsDto dto = analyticsService.getRestaurantAnalytics(OWNER_ID, RESTAURANT_ID, FROM, TO);

        assertThat(dto.getTotalBookings()).isEqualTo(5);
        assertThat(dto.getConfirmationRate()).isEqualTo(0.0);
    }
}
