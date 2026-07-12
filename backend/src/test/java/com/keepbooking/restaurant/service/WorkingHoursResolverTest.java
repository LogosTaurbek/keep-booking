package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.model.WorkingHoursOverride;
import com.keepbooking.restaurant.repository.WorkingHoursOverrideRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;

/**
 * tz2.txt §8: per-date overrides (holidays) take precedence over the weekly schedule,
 * and an overnight schedule (closeTime before openTime) must be checked against both
 * the requested day and the previous day it may carry over from.
 */
@ExtendWith(MockitoExtension.class)
class WorkingHoursResolverTest {

    @Mock
    private WorkingHoursRepository workingHoursRepository;
    @Mock
    private WorkingHoursOverrideRepository overrideRepository;

    private WorkingHoursResolver resolver;

    private static final Long RESTAURANT_ID = 1L;
    // A Friday, so date.minusDays(1) is deterministically Thursday for the carry-over tests.
    private static final LocalDate FRIDAY = LocalDate.of(2026, 12, 4);
    private static final LocalDate THURSDAY = FRIDAY.minusDays(1);

    @BeforeEach
    void setUp() {
        resolver = new WorkingHoursResolver(workingHoursRepository, overrideRepository);
        lenient().when(overrideRepository.findByRestaurantIdAndDate(RESTAURANT_ID, FRIDAY)).thenReturn(Optional.empty());
        lenient().when(overrideRepository.findByRestaurantIdAndDate(RESTAURANT_ID, THURSDAY)).thenReturn(Optional.empty());
        lenient().when(workingHoursRepository.findByRestaurantIdAndDayOfWeek(RESTAURANT_ID, THURSDAY.getDayOfWeek().getValue()))
                .thenReturn(Optional.empty());
    }

    private void weeklyHours(LocalDate date, LocalTime open, LocalTime close, boolean dayOff) {
        WorkingHours wh = WorkingHours.builder().dayOfWeek(date.getDayOfWeek().getValue())
                .openTime(open).closeTime(close).isDayOff(dayOff).build();
        when(workingHoursRepository.findByRestaurantIdAndDayOfWeek(RESTAURANT_ID, date.getDayOfWeek().getValue()))
                .thenReturn(Optional.of(wh));
    }

    @Test
    void resolveFallsBackToWeeklyScheduleWhenNoOverrideExists() {
        weeklyHours(FRIDAY, LocalTime.of(9, 0), LocalTime.of(22, 0), false);

        EffectiveHours hours = resolver.resolve(RESTAURANT_ID, FRIDAY);

        assertThat(hours.closed()).isFalse();
        assertThat(hours.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(hours.closeTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void resolveReturnsClosedWhenNoWeeklyEntryAndNoOverride() {
        EffectiveHours hours = resolver.resolve(RESTAURANT_ID, FRIDAY);

        assertThat(hours.closed()).isTrue();
    }

    @Test
    void resolvePrefersOverrideOverWeeklySchedule() {
        // Weekly schedule is intentionally left unstubbed: resolve() must short-circuit on the
        // override and never even consult it.
        WorkingHoursOverride holiday = WorkingHoursOverride.builder()
                .restaurant(Restaurant.builder().id(RESTAURANT_ID).build())
                .date(FRIDAY).isClosed(true).build();
        when(overrideRepository.findByRestaurantIdAndDate(RESTAURANT_ID, FRIDAY)).thenReturn(Optional.of(holiday));

        EffectiveHours hours = resolver.resolve(RESTAURANT_ID, FRIDAY);

        assertThat(hours.closed()).isTrue();
    }

    @Test
    void isOpenAtAcceptsSlotWithinRegularHours() {
        weeklyHours(FRIDAY, LocalTime.of(9, 0), LocalTime.of(22, 0), false);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(12, 0), LocalTime.of(13, 0))).isTrue();
    }

    @Test
    void isOpenAtRejectsSlotOutsideRegularHours() {
        weeklyHours(FRIDAY, LocalTime.of(9, 0), LocalTime.of(22, 0), false);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(7, 0), LocalTime.of(8, 0))).isFalse();
    }

    @Test
    void isOpenAtRejectsWhenDayOff() {
        weeklyHours(FRIDAY, LocalTime.of(9, 0), LocalTime.of(22, 0), true);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(12, 0), LocalTime.of(13, 0))).isFalse();
    }

    @Test
    void isOpenAtAcceptsEveningPortionOfOvernightSchedule() {
        // Friday: open 18:00, closes 02:00 the next morning
        weeklyHours(FRIDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), false);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(23, 0), LocalTime.of(23, 59))).isTrue();
    }

    @Test
    void isOpenAtRejectsEveningSlotBeforeOvernightOpening() {
        weeklyHours(FRIDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), false);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(15, 0), LocalTime.of(16, 0))).isFalse();
    }

    @Test
    void isOpenAtAcceptsEarlyMorningSlotCarriedOverFromPreviousOvernightDay() {
        // Thursday: open 18:00, closes 02:00 Friday morning - a Friday 01:00-01:30 booking
        // belongs to Thursday's overnight schedule, not Friday's own (separate) weekly entry.
        weeklyHours(THURSDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), false);
        weeklyHours(FRIDAY, LocalTime.of(11, 0), LocalTime.of(15, 0), false);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(1, 0), LocalTime.of(1, 30))).isTrue();
    }

    @Test
    void isOpenAtRejectsEarlyMorningSlotPastPreviousOvernightClose() {
        weeklyHours(THURSDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), false);
        weeklyHours(FRIDAY, LocalTime.of(11, 0), LocalTime.of(15, 0), false);

        // 02:30 is after Thursday's 02:00 overnight close, and before Friday's own 11:00 opening
        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(2, 30), LocalTime.of(3, 0))).isFalse();
    }

    @Test
    void isOpenAtDoesNotCarryOverFromANonOvernightPreviousDay() {
        // Thursday closes normally at 22:00 (not overnight) - Friday's early morning must not
        // be considered "open" just because Thursday existed.
        weeklyHours(THURSDAY, LocalTime.of(9, 0), LocalTime.of(22, 0), false);
        weeklyHours(FRIDAY, LocalTime.of(11, 0), LocalTime.of(15, 0), false);

        assertThat(resolver.isOpenAt(RESTAURANT_ID, FRIDAY, LocalTime.of(1, 0), LocalTime.of(1, 30))).isFalse();
    }
}
