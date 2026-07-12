package com.keepbooking.analytics.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.analytics.dto.HourCountDto;
import com.keepbooking.analytics.dto.RestaurantAnalyticsDto;
import com.keepbooking.analytics.dto.TableCountDto;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int TOP_N = 5;

    private final RestaurantRepository restaurantRepository;
    private final BookingRepository bookingRepository;

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

        Map<BookingStatus, Long> byStatus = bookingRepository.countByStatusForRestaurant(restaurantId, from, to).stream()
                .collect(Collectors.toMap(row -> (BookingStatus) row[0], row -> (Long) row[1]));

        long pending = byStatus.getOrDefault(BookingStatus.PENDING, 0L);
        long confirmed = byStatus.getOrDefault(BookingStatus.CONFIRMED, 0L);
        long rejected = byStatus.getOrDefault(BookingStatus.REJECTED, 0L);
        long cancelled = byStatus.getOrDefault(BookingStatus.CANCELLED, 0L);
        long completed = byStatus.getOrDefault(BookingStatus.COMPLETED, 0L);
        long noShow = byStatus.getOrDefault(BookingStatus.NO_SHOW, 0L);
        long total = pending + confirmed + rejected + cancelled + completed + noShow;

        long leftPending = total - pending;
        double confirmationRate = leftPending == 0 ? 0.0
                : (double) (confirmed + completed + noShow) / leftPending;

        List<HourCountDto> popularHours = bookingRepository
                .findPopularHours(restaurantId, from, to, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new HourCountDto(((Number) row[0]).intValue(), (Long) row[1]))
                .toList();

        List<TableCountDto> popularTables = bookingRepository
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
