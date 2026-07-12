package com.keepbooking.restaurant.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertWorkingHoursOverrideRequest {

    @NotNull
    private LocalDate date;

    private LocalTime openTime;
    private LocalTime closeTime;

    private Boolean isClosed;
}
