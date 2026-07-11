package com.keepbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.common.config.AppProperties;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.service.NotificationService;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.user.model.User;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class BookingSchedulerServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    private AppProperties appProperties;
    private BookingSchedulerService schedulerService;

    private static final Long BOOKING_ID = 100L;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getBooking().setPendingTimeoutMs(15 * 60 * 1000L);
        schedulerService = new BookingSchedulerService(bookingRepository, appProperties, notificationService,
                auditLogService, new SimpleMeterRegistry());
    }

    private Booking pendingBooking() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Test Restaurant").build();
        return Booking.builder().id(BOOKING_ID).user(User.builder().id(1L).build()).restaurant(restaurant)
                .bookingDate(LocalDate.now()).timeFrom(LocalTime.of(19, 0)).timeTo(LocalTime.of(21, 0))
                .status(BookingStatus.PENDING).build();
    }

    private Booking confirmedBooking() {
        Restaurant restaurant = Restaurant.builder().id(1L).name("Test Restaurant").build();
        return Booking.builder().id(BOOKING_ID).user(User.builder().id(1L).build()).restaurant(restaurant)
                .bookingDate(LocalDate.now()).timeFrom(LocalTime.of(19, 0)).timeTo(LocalTime.of(21, 0))
                .status(BookingStatus.CONFIRMED).build();
    }

    @Test
    void autoCancelExpiredPendingDoesNothingWhenNoneAreExpired() {
        when(bookingRepository.findByStatusAndCreatedAtBefore(org.mockito.ArgumentMatchers.eq(BookingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        schedulerService.autoCancelExpiredPending();

        verify(bookingRepository, never()).saveAll(anyList());
        verify(notificationService, never()).notifyBookingStatusChange(any(), any(), any(), any());
    }

    @Test
    void autoCancelExpiredPendingCancelsAndNotifiesForEachExpiredBooking() {
        Booking booking = pendingBooking();
        when(bookingRepository.findByStatusAndCreatedAtBefore(org.mockito.ArgumentMatchers.eq(BookingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(booking));
        when(bookingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCancelExpiredPending();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelReason()).contains("Auto-cancelled");
        verify(notificationService).notifyBookingStatusChange(org.mockito.ArgumentMatchers.eq(booking),
                org.mockito.ArgumentMatchers.eq(NotificationType.BOOKING_CANCELLED), any(), any());
        verify(auditLogService).record(null, "BOOKING_AUTO_CANCELLED", "Booking", BOOKING_ID, "PENDING -> CANCELLED (timeout)");
    }

    @Test
    void autoCompletePastBookingsDoesNothingWhenNoneArePast() {
        when(bookingRepository.findConfirmedPastEnd(any(LocalDate.class), any(LocalTime.class))).thenReturn(List.of());

        schedulerService.autoCompletePastBookings();

        verify(bookingRepository, never()).saveAll(anyList());
        verify(notificationService, never()).notifyBookingStatusChange(any(), any(), any(), any());
    }

    @Test
    void autoCompletePastBookingsCompletesAndNotifiesForEachPastBooking() {
        Booking booking = confirmedBooking();
        when(bookingRepository.findConfirmedPastEnd(any(LocalDate.class), any(LocalTime.class))).thenReturn(List.of(booking));
        when(bookingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCompletePastBookings();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(notificationService).notifyBookingStatusChange(org.mockito.ArgumentMatchers.eq(booking),
                org.mockito.ArgumentMatchers.eq(NotificationType.BOOKING_COMPLETED), any(), any());
        verify(auditLogService, times(1)).record(null, "BOOKING_AUTO_COMPLETED", "Booking", BOOKING_ID, "CONFIRMED -> COMPLETED (auto)");
    }
}
