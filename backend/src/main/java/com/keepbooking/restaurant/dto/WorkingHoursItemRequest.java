package com.keepbooking.restaurant.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkingHoursItemRequest {

    @NotNull
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;

    private Boolean isDayOff;
}
