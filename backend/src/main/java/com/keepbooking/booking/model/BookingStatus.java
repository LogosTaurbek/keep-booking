package com.keepbooking.booking.model;

import java.util.Set;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    public boolean isTerminal() {
        return this == REJECTED || this == CANCELLED || this == COMPLETED || this == NO_SHOW;
    }

    public boolean canTransitionTo(BookingStatus next) {
        return switch (this) {
            case PENDING    -> Set.of(CONFIRMED, REJECTED, CANCELLED).contains(next);
            case CONFIRMED  -> Set.of(COMPLETED, CANCELLED, NO_SHOW).contains(next);
            default         -> false;
        };
    }
}
