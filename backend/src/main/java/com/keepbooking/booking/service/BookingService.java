package com.keepbooking.booking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.booking.dto.BookingDto;
import com.keepbooking.booking.dto.CreateBookingRequest;
import com.keepbooking.booking.dto.UpdateBookingStatusRequest;
import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.model.TableStatus;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RestaurantRepository restaurantRepository;
    private final TableRepository tableRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingDto create(Long userId, CreateBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        RestaurantTable table = tableRepository.findById(request.getTableId())
                .orElseThrow(() -> new ApiException(ErrorCode.TABLE_NOT_FOUND));

        if (table.getStatus() != TableStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TABLE_NOT_AVAILABLE, "Table is not available");
        }

        Restaurant restaurant = table.getHall().getRestaurant();
        if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new ApiException(ErrorCode.RESTAURANT_NOT_ACTIVE);
        }

        validateBookingTime(request.getBookingDate(), request.getTimeFrom(), request.getTimeTo());
        validateGuestCount(table, request.getGuestCount());

        // Проверка конфликта в JPQL (резервная, exclusion constraint в БД — финальная гарантия)
        if (bookingRepository.existsConflictingBooking(
                table.getId(), request.getBookingDate(),
                request.getTimeFrom(), request.getTimeTo())) {
            throw new ApiException(ErrorCode.TABLE_NOT_AVAILABLE);
        }

        Booking booking = Booking.builder()
                .restaurant(restaurant)
                .table(table)
                .user(user)
                .bookingDate(request.getBookingDate())
                .timeFrom(request.getTimeFrom())
                .timeTo(request.getTimeTo())
                .guestCount(request.getGuestCount())
                .comment(request.getComment())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        try {
            return toDto(bookingRepository.save(booking));
        } catch (DataIntegrityViolationException e) {
            // exclusion constraint сработал — двойная гарантия
            throw new ApiException(ErrorCode.TABLE_NOT_AVAILABLE);
        }
    }

    @Transactional
    public BookingDto updateStatus(Long bookingId, Long userId, UpdateBookingStatusRequest request, boolean isManager) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));

        if (!isManager && !booking.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        if (!booking.getStatus().canTransitionTo(request.getStatus())) {
            throw new ApiException(ErrorCode.BOOKING_STATUS_TRANSITION,
                    "Cannot transition from " + booking.getStatus() + " to " + request.getStatus());
        }

        User actor = userRepository.findById(userId).orElseThrow();
        BookingStatus next = request.getStatus();

        if (next == BookingStatus.CONFIRMED) booking.setConfirmedBy(actor);
        if (next == BookingStatus.CANCELLED || next == BookingStatus.REJECTED) {
            booking.setCancelledBy(actor);
            booking.setCancelReason(request.getCancelReason());
        }

        booking.setStatus(next);
        return toDto(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDto> getMyBookings(Long userId, Pageable pageable) {
        return PageResponse.of(bookingRepository.findByUserIdOrderByBookingDateDesc(userId, pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDto> getRestaurantBookings(Long restaurantId, Pageable pageable) {
        return PageResponse.of(bookingRepository.findByRestaurantIdOrderByBookingDateDesc(restaurantId, pageable).map(this::toDto));
    }

    private void validateBookingTime(LocalDate date, LocalTime from, LocalTime to) {
        if (!from.isBefore(to)) {
            throw new ApiException(ErrorCode.BOOKING_INVALID_TIME, "timeFrom must be before timeTo");
        }
        LocalDateTime start = LocalDateTime.of(date, from);
        if (start.isBefore(LocalDateTime.now(ZoneId.of("UTC")))) {
            throw new ApiException(ErrorCode.BOOKING_INVALID_TIME, "Booking time is in the past");
        }
    }

    private void validateGuestCount(RestaurantTable table, int guestCount) {
        if (guestCount > table.getCapacity()) {
            throw new ApiException(ErrorCode.BOOKING_GUEST_COUNT,
                    "Guest count " + guestCount + " exceeds table capacity " + table.getCapacity());
        }
        if (table.getMinCapacity() != null && guestCount < table.getMinCapacity()) {
            throw new ApiException(ErrorCode.BOOKING_GUEST_COUNT,
                    "Guest count " + guestCount + " is below minimum " + table.getMinCapacity());
        }
    }

    private BookingDto toDto(Booking b) {
        return BookingDto.builder()
                .id(b.getId())
                .restaurantId(b.getRestaurant().getId())
                .restaurantName(b.getRestaurant().getName())
                .tableId(b.getTable().getId())
                .tableNumber(b.getTable().getNumber())
                .userId(b.getUser().getId())
                .bookingDate(b.getBookingDate())
                .timeFrom(b.getTimeFrom())
                .timeTo(b.getTimeTo())
                .guestCount(b.getGuestCount())
                .comment(b.getComment())
                .status(b.getStatus())
                .source(b.getSource())
                .cancelReason(b.getCancelReason())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
