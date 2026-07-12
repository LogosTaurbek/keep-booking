package com.keepbooking.waitlist.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.service.NotificationService;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.repository.UserRepository;
import com.keepbooking.waitlist.dto.CreateWaitlistEntryRequest;
import com.keepbooking.waitlist.dto.WaitlistEntryDto;
import com.keepbooking.waitlist.model.WaitlistEntry;
import com.keepbooking.waitlist.model.WaitlistStatus;
import com.keepbooking.waitlist.repository.WaitlistRepository;

import lombok.RequiredArgsConstructor;

/**
 * tz2.txt §11.4 / §25 (этап 3): join a waitlist for a restaurant/date/time slot, get notified
 * once when a booking overlapping that window is cancelled or rejected.
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    // A single table opening up is one opportunity, not a broadcast - only the longest-waiting
    // matching entry is notified per freed slot, so fetching a handful covers the (rare) case
    // where the earliest one doesn't fit the freed table's minCapacity.
    private static final int CANDIDATE_LOOKUP_LIMIT = 5;

    private final WaitlistRepository waitlistRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public WaitlistEntryDto join(Long userId, CreateWaitlistEntryRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new ApiException(ErrorCode.RESTAURANT_NOT_ACTIVE);
        }
        validateSlotTime(request.getBookingDate(), request.getTimeFrom(), request.getTimeTo(), restaurant.getTimezone());

        // Idempotent, like favorites: re-joining the same slot returns the existing entry instead of erroring.
        WaitlistEntry existing = waitlistRepository
                .findByUserIdAndRestaurantIdAndBookingDateAndTimeFromAndTimeToAndStatus(
                        userId, restaurant.getId(), request.getBookingDate(),
                        request.getTimeFrom(), request.getTimeTo(), WaitlistStatus.ACTIVE)
                .orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .restaurant(restaurant)
                .user(userRepository.getReferenceById(userId))
                .bookingDate(request.getBookingDate())
                .timeFrom(request.getTimeFrom())
                .timeTo(request.getTimeTo())
                .guestCount(request.getGuestCount())
                .build();
        return toDto(waitlistRepository.save(entry));
    }

    @Transactional
    public void leave(Long userId, Long entryId) {
        WaitlistEntry entry = waitlistRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.WAITLIST_ENTRY_NOT_FOUND));
        entry.setStatus(WaitlistStatus.CANCELLED);
        waitlistRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<WaitlistEntryDto> getMyWaitlist(Long userId, Pageable pageable) {
        Page<WaitlistEntry> page = waitlistRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    /**
     * Called right after a booking transitions to CANCELLED/REJECTED (same transaction as that
     * status change) - notifies the single longest-waiting matching entry, if any.
     */
    @Transactional
    public void notifyTableFreed(Booking freedBooking) {
        RestaurantTable table = freedBooking.getTable();
        var candidates = waitlistRepository.findMatchingActive(
                freedBooking.getRestaurant().getId(), freedBooking.getBookingDate(),
                freedBooking.getTimeFrom(), freedBooking.getTimeTo(), table.getCapacity(),
                PageRequest.of(0, CANDIDATE_LOOKUP_LIMIT));

        candidates.stream()
                .filter(e -> table.getMinCapacity() == null || e.getGuestCount() >= table.getMinCapacity())
                .findFirst()
                .ifPresent(entry -> {
                    entry.setStatus(WaitlistStatus.NOTIFIED);
                    entry.setNotifiedAt(Instant.now());
                    waitlistRepository.save(entry);
                    notificationService.notifyUser(entry.getUser(), NotificationType.WAITLIST_TABLE_AVAILABLE,
                            "A table opened up!",
                            "A table for " + entry.getGuestCount() + " just opened up at "
                                    + freedBooking.getRestaurant().getName() + " on " + freedBooking.getBookingDate()
                                    + " around " + freedBooking.getTimeFrom() + " - book now before it's gone");
                });
    }

    private void validateSlotTime(LocalDate date, LocalTime from, LocalTime to, String restaurantTimezone) {
        if (!from.isBefore(to)) {
            throw new ApiException(ErrorCode.BOOKING_INVALID_TIME, "timeFrom must be before timeTo");
        }
        if (LocalDateTime.of(date, from).isBefore(LocalDateTime.now(ZoneId.of(restaurantTimezone)))) {
            throw new ApiException(ErrorCode.BOOKING_INVALID_TIME, "Requested time is in the past");
        }
    }

    private WaitlistEntryDto toDto(WaitlistEntry e) {
        return WaitlistEntryDto.builder()
                .id(e.getId())
                .restaurantId(e.getRestaurant().getId())
                .restaurantName(e.getRestaurant().getName())
                .bookingDate(e.getBookingDate())
                .timeFrom(e.getTimeFrom())
                .timeTo(e.getTimeTo())
                .guestCount(e.getGuestCount())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
