package com.keepbooking.restaurant.service;

import java.time.LocalTime;

/**
 * Resolved opening hours for a single restaurant/date, after applying any per-date override
 * on top of the weekly schedule. {@code closeTime} before {@code openTime} means the restaurant
 * closes after midnight (overnight schedule) - see {@link WorkingHoursResolver}.
 */
public record EffectiveHours(LocalTime openTime, LocalTime closeTime, boolean closed) {

    public static EffectiveHours alwaysClosed() {
        return new EffectiveHours(null, null, true);
    }

    public boolean isOvernight() {
        return !closed && openTime != null && closeTime != null && closeTime.isBefore(openTime);
    }
}
