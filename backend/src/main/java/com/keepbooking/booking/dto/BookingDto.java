package com.keepbooking.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.keepbooking.booking.model.BookingStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
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
