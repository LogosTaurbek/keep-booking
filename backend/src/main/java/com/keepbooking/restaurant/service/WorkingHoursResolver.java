package com.keepbooking.restaurant.service;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.model.WorkingHoursOverride;
import com.keepbooking.restaurant.repository.WorkingHoursOverrideRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the effective opening hours for a restaurant on a given date: a per-date override
 * (tz2.txt §8, holidays/special days) takes full precedence over the weekly schedule.
 */
@Service
@RequiredArgsConstructor
public class WorkingHoursResolver {

    private final WorkingHoursRepository workingHoursRepository;
    private final WorkingHoursOverrideRepository overrideRepository;

    @Transactional(readOnly = true)
    public EffectiveHours resolve(Long restaurantId, LocalDate date) {
        return overrideRepository.findByRestaurantIdAndDate(restaurantId, date)
                .map(this::fromOverride)
                .orElseGet(() -> workingHoursRepository
                        .findByRestaurantIdAndDayOfWeek(restaurantId, date.getDayOfWeek().getValue())
                        .map(this::fromWeekly)
                        .orElseGet(EffectiveHours::alwaysClosed));
    }

    /**
     * Whether [from, to) falls within the restaurant's opening hours on {@code date}. A booking's
     * date/from/to never cross midnight themselves, so the previous day's hours are also checked
     * in case that day runs an overnight schedule (closeTime before openTime) bleeding into today.
     */
    @Transactional(readOnly = true)
    public boolean isOpenAt(Long restaurantId, LocalDate date, LocalTime from, LocalTime to) {
        EffectiveHours today = resolve(restaurantId, date);
        if (fitsWithinHours(today, from, to, false)) {
            return true;
        }
        EffectiveHours yesterday = resolve(restaurantId, date.minusDays(1));
        return fitsWithinHours(yesterday, from, to, true);
    }

    private boolean fitsWithinHours(EffectiveHours hours, LocalTime from, LocalTime to, boolean carriedFromPreviousDay) {
        if (hours.closed() || hours.openTime() == null || hours.closeTime() == null) {
            return false;
        }
        if (carriedFromPreviousDay) {
            // Only the early-morning leftover of an overnight schedule that started the day before applies.
            return hours.isOvernight() && !to.isAfter(hours.closeTime());
        }
        if (hours.isOvernight()) {
            // Evening portion of an overnight schedule: starts after opening, runs until midnight.
            return !from.isBefore(hours.openTime());
        }
        return !from.isBefore(hours.openTime()) && !to.isAfter(hours.closeTime());
    }

    private EffectiveHours fromOverride(WorkingHoursOverride o) {
        return new EffectiveHours(o.getOpenTime(), o.getCloseTime(), Boolean.TRUE.equals(o.getIsClosed()));
    }

    private EffectiveHours fromWeekly(WorkingHours wh) {
        return new EffectiveHours(wh.getOpenTime(), wh.getCloseTime(), Boolean.TRUE.equals(wh.getIsDayOff()));
    }
}
