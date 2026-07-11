package com.keepbooking.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.keepbooking.booking.model.BookingStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The only DTO in the codebase Jackson actually deserializes (IdempotencyService.get()
 * reads it back from Redis) rather than just serializes as a response body — needs a
 * no-args constructor for that, unlike every other @Builder DTO here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Long tableId;
    private String tableNumber;
    private Long userId;
    private LocalDate bookingDate;
    private LocalTime timeFrom;
    private LocalTime timeTo;
    private Integer guestCount;
    private String comment;
    private BookingStatus status;
    private String source;
    private String cancelReason;
    private Instant createdAt;
}
