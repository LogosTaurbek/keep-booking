package com.keepbooking.analytics.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.analytics.dto.HourCountDto;
import com.keepbooking.analytics.dto.RestaurantAnalyticsDto;
import com.keepbooking.analytics.dto.TableCountDto;
import com.keepbooking.analytics.repository.RestaurantDailyHourStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyStatsRepository;
import com.keepbooking.analytics.repository.RestaurantDailyTableStatsRepository;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads from the analytics read model (tz2.txt §15/§25) kept fresh by
 * {@link AnalyticsRefreshWorker} - status/hour/table breakdowns are small per-day rollups summed
 * over the requested range, not a scan over every individual booking. Unique-guest count is the
 * one exception: it stays a live {@code COUNT(DISTINCT)} query, since an accurate distinct count
 * isn't reconstructable from daily rollups without a sketch structure, and that single indexed
 * aggregate was never the expensive part of the original implementation.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int TOP_N = 5;

    private final RestaurantRepository restaurantRepository;
    private final BookingRepository bookingRepository;
    private final RestaurantDailyStatsRepository dailyStatsRepository;
    private final RestaurantDailyHourStatsRepository hourStatsRepository;
    private final RestaurantDailyTableStatsRepository tableStatsRepository;

    @Transactional(readOnly = true)
    public RestaurantAnalyticsDto getRestaurantAnalytics(Long userId, Long restaurantId, LocalDate from, LocalDate to) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "'from' must not be after 'to'");
        }

        // An aggregate query with no GROUP BY always returns exactly one row (COALESCE covers the
        // no-matching-data case with zeros), so .get(0) is safe.
        Object[] sums = dailyStatsRepository.sumStatusCounts(restaurantId, from, to).get(0);
        long pending = ((Number) sums[0]).longValue();
        long confirmed = ((Number) sums[1]).longValue();
        long rejected = ((Number) sums[2]).longValue();
        long cancelled = ((Number) sums[3]).longValue();
        long completed = ((Number) sums[4]).longValue();
        long noShow = ((Number) sums[5]).longValue();
        long total = pending + confirmed + rejected + cancelled + completed + noShow;

        long leftPending = total - pending;
        double confirmationRate = leftPending == 0 ? 0.0
                : (double) (confirmed + completed + noShow) / leftPending;

        List<HourCountDto> popularHours = hourStatsRepository
                .findPopularHours(restaurantId, from, to, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new HourCountDto(((Number) row[0]).intValue(), (Long) row[1]))
                .toList();

        List<TableCountDto> popularTables = tableStatsRepository
                .findPopularTables(restaurantId, from, to, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new TableCountDto((Long) row[0], (String) row[1], (Long) row[2]))
                .toList();

        long uniqueGuests = bookingRepository.countDistinctGuestsForRestaurant(restaurantId, from, to);

        return RestaurantAnalyticsDto.builder()
                .restaurantId(restaurantId)
                .from(from)
                .to(to)
                .totalBookings(total)
                .pendingBookings(pending)
                .confirmedBookings(confirmed)
                .rejectedBookings(rejected)
                .cancelledBookings(cancelled)
                .completedBookings(completed)
                .noShowBookings(noShow)
                .confirmationRate(confirmationRate)
                .uniqueGuests(uniqueGuests)
                .popularHours(popularHours)
                .popularTables(popularTables)
                .build();
    }
}
