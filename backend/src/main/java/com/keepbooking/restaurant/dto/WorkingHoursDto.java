package com.keepbooking.restaurant.dto;

import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkingHoursDto {
    private Long id;
    private Long restaurantId;
    private Integer dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean isDayOff;
}
