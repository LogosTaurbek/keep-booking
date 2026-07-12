package com.keepbooking.waitlist.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.keepbooking.waitlist.model.WaitlistStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitlistEntryDto {
    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private LocalDate bookingDate;
    private LocalTime timeFrom;
    private LocalTime timeTo;
    private Integer guestCount;
    private WaitlistStatus status;
    private Instant createdAt;
}
