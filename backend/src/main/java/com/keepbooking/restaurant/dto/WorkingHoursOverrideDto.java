package com.keepbooking.restaurant.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkingHoursOverrideDto {
    private Long id;
    private Long restaurantId;
    private LocalDate date;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean isClosed;
}
