package com.keepbooking.booking.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The booking state machine is the backbone of the whole reservation flow — every
 * other guarantee (double-booking protection, notifications, audit log) assumes
 * these transition rules are enforced consistently.
 */
class BookingStatusTest {

    @Test
    void pendingCanTransitionToConfirmedRejectedOrCancelled() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.REJECTED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
    }

    @Test
    void pendingCannotJumpToCompletedOrNoShow() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.COMPLETED)).isFalse();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.NO_SHOW)).isFalse();
    }

    @Test
    void confirmedCanTransitionToCompletedCancelledOrNoShow() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.COMPLETED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.NO_SHOW)).isTrue();
    }

    @Test
    void confirmedCannotTransitionToRejectedOrBackToPending() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.REJECTED)).isFalse();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.PENDING)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"REJECTED", "CANCELLED", "COMPLETED", "NO_SHOW"})
    void terminalStatusesCannotTransitionAnywhere(BookingStatus terminal) {
        for (BookingStatus target : BookingStatus.values()) {
            assertThat(terminal.canTransitionTo(target))
                    .as("%s -> %s should be rejected", terminal, target)
                    .isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("terminalAndNonTerminalStatuses")
    void isTerminalMatchesExpectedSet(BookingStatus status, boolean expectedTerminal) {
        assertThat(status.isTerminal()).isEqualTo(expectedTerminal);
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> terminalAndNonTerminalStatuses() {
        Set<BookingStatus> terminal = EnumSet.of(
                BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);
        return java.util.Arrays.stream(BookingStatus.values())
                .map(s -> org.junit.jupiter.params.provider.Arguments.of(s, terminal.contains(s)));
    }
}
