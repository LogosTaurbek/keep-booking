package com.keepbooking.restaurant.dto;

import java.time.LocalTime;

import lombok.Data;

@Data
public class UpsertWorkingHoursDayRequest {

    private LocalTime openTime;
    private LocalTime closeTime;

    private Boolean isDayOff;
}
