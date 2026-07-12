package com.keepbooking.analytics.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RestaurantAnalyticsDto {
    private Long restaurantId;
    private LocalDate from;
    private LocalDate to;

    private long totalBookings;
    private long pendingBookings;
    private long confirmedBookings;
    private long rejectedBookings;
    private long cancelledBookings;
    private long completedBookings;
    private long noShowBookings;

    /**
     * Share of bookings that left PENDING and ended up confirmed (CONFIRMED/COMPLETED/NO_SHOW)
     * rather than REJECTED/CANCELLED. A booking CONFIRMED and later CANCELLED is counted as
     * cancelled here since booking status history isn't tracked - this is a lower-bound estimate.
     */
    private double confirmationRate;

    private long uniqueGuests;
    private List<HourCountDto> popularHours;
    private List<TableCountDto> popularTables;
}
