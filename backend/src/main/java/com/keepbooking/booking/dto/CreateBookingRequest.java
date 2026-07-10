package com.keepbooking.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBookingRequest {

    @NotNull
    private Long tableId;

    @NotNull
    @FutureOrPresent
    private LocalDate bookingDate;

    @NotNull
    private LocalTime timeFrom;

    @NotNull
    private LocalTime timeTo;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer guestCount;

    @Size(max = 1000)
    private String comment;
}
