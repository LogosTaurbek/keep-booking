package com.keepbooking.booking.dto;

import com.keepbooking.booking.model.BookingStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBookingStatusRequest {

    @NotNull
    private BookingStatus status;

    private String cancelReason;
}
