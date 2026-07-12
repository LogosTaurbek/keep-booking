package com.keepbooking.analytics.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.analytics.model.AnalyticsRefreshState;
import com.keepbooking.analytics.model.RestaurantDailyHourStats;
import com.keepbooking.analytics.model.RestaurantDailyStats;
import com.keepbooking.analytics.model.RestaurantDailyTableStats;
import com.keepbooking.analytics.repository.AnalyticsRefreshStateRepository;
import com.keepbooking.analytics.repository.RestaurantDailyHourStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyTableStatsRepository;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the analytics read model (tz2.txt §15/§25) fresh: on each cycle, finds every
 * (restaurant, date) pair touched by a booking write since the last run (via {@code Booking}'s
 * {@code updatedAt}, bumped by JPA auditing on any save) and re-aggregates just those pairs into
 * {@code restaurant_daily_*_stats}, instead of rescanning the whole {@code bookings} table.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsRefreshWorker {

    private final BookingRepository bookingRepository;
    private final RestaurantRepository restaurantRepository;
    private final TableRepository tableRepository;
    private final RestaurantDailyStatsRepository dailyStatsRepository;
    private final RestaurantDailyHourStatsRepository hourStatsRepository;
    private final RestaurantDailyTableStatsRepository tableStatsRepository;
    private final AnalyticsRefreshStateRepository refreshStateRepository;

    @Scheduled(fixedDelayString = "${app.analytics.refresh-interval-ms:900000}")
    @Transactional
    public void refresh() {
        AnalyticsRefreshState state = refreshStateRepository.findAll().stream().findFirst()
                .orElseGet(() -> refreshStateRepository.save(
                        AnalyticsRefreshState.builder().lastRefreshedAt(Instant.EPOCH).build()));

        Instant since = state.getLastRefreshedAt();
        Instant refreshStartedAt = Instant.now();

        List<Object[]> dirty = bookingRepository.findDirtyRestaurantDatesSince(since);
        for (Object[] row : dirty) {
            recomputeDay((Long) row[0], (LocalDate) row[1]);
        }

        state.setLastRefreshedAt(refreshStartedAt);
        refreshStateRepository.save(state);
        if (!dirty.isEmpty()) {
            log.info("Analytics read model refreshed for {} restaurant/date pairs", dirty.size());
        }
    }

    private void recomputeDay(Long restaurantId, LocalDate date) {
        Map<BookingStatus, Long> byStatus = bookingRepository.countByStatusForRestaurantAndDate(restaurantId, date)
                .stream().collect(Collectors.toMap(r -> (BookingStatus) r[0], r -> (Long) r[1]));

        RestaurantDailyStats stats = dailyStatsRepository.findByRestaurantIdAndStatDate(restaurantId, date)
                .orElseGet(() -> RestaurantDailyStats.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .statDate(date)
                        .build());
        stats.setPendingCount(byStatus.getOrDefault(BookingStatus.PENDING, 0L).intValue());
        stats.setConfirmedCount(byStatus.getOrDefault(BookingStatus.CONFIRMED, 0L).intValue());
        stats.setRejectedCount(byStatus.getOrDefault(BookingStatus.REJECTED, 0L).intValue());
        stats.setCancelledCount(byStatus.getOrDefault(BookingStatus.CANCELLED, 0L).intValue());
        stats.setCompletedCount(byStatus.getOrDefault(BookingStatus.COMPLETED, 0L).intValue());
        stats.setNoShowCount(byStatus.getOrDefault(BookingStatus.NO_SHOW, 0L).intValue());
        dailyStatsRepository.save(stats);

        // Hour/table breakdowns count bookings regardless of status (matches the original
        // AnalyticsService behavior) - a status-only change doesn't move these, but a delete+
        // reinsert per (restaurant, date) is simple, correct, and cheap at this granularity.
        hourStatsRepository.deleteByRestaurantIdAndStatDate(restaurantId, date);
        bookingRepository.countByHourForRestaurantAndDate(restaurantId, date).forEach(row ->
                hourStatsRepository.save(RestaurantDailyHourStats.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .statDate(date)
                        .hourOfDay(((Number) row[0]).intValue())
                        .bookingCount(((Long) row[1]).intValue())
                        .build()));

        tableStatsRepository.deleteByRestaurantIdAndStatDate(restaurantId, date);
        bookingRepository.countByTableForRestaurantAndDate(restaurantId, date).forEach(row ->
                tableStatsRepository.save(RestaurantDailyTableStats.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .statDate(date)
                        .table(tableRepository.getReferenceById((Long) row[0]))
                        .bookingCount(((Long) row[1]).intValue())
                        .build()));
    }
}
