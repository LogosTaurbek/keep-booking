package com.keepbooking.booking.controller;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.booking.dto.BookingDto;
import com.keepbooking.booking.dto.CreateBookingRequest;
import com.keepbooking.booking.dto.UpdateBookingStatusRequest;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.service.BookingService;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Bookings", description = "Table reservations")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a booking", description = "Pass an Idempotency-Key header to safely retry on network errors")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingDto> create(@AuthenticationPrincipal User user,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                              @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(user.getId(), request, idempotencyKey));
    }

    @Operation(summary = "Get my bookings (optionally filter by status, e.g. COMPLETED for visit history)")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<BookingDto>> getMyBookings(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("bookingDate").descending());
        return ResponseEntity.ok(bookingService.getMyBookings(user.getId(), status, pageable));
    }

    @Operation(summary = "Get restaurant bookings (owner only), optionally filtered by booking date range")
    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<BookingDto>> getRestaurantBookings(
            @AuthenticationPrincipal User user,
            @PathVariable Long restaurantId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("bookingDate").descending());
        return ResponseEntity.ok(bookingService.getRestaurantBookings(user.getId(), restaurantId, from, to, pageable));
    }

    @Operation(summary = "Update booking status (confirm / cancel / complete)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingDto> updateStatus(@PathVariable Long id,
                                                    @AuthenticationPrincipal User user,
                                                    @Valid @RequestBody UpdateBookingStatusRequest request) {
        return ResponseEntity.ok(bookingService.updateStatus(id, user.getId(), request));
    }
}
