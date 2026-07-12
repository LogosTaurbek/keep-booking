package com.keepbooking.booking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.common.config.AppProperties;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.service.NotificationService;
import com.keepbooking.waitlist.service.WaitlistService;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodic housekeeping for bookings left in a non-terminal state.
 * NO_SHOW is intentionally not auto-assigned — there's no signal to distinguish it
 * from a normal visit, so it stays a manual staff action via the status-update endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSchedulerService {

    private final BookingRepository bookingRepository;
    private final AppProperties appProperties;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;
    private final WaitlistService waitlistService;

    @Scheduled(fixedDelayString = "${app.booking.scheduler-interval-ms:60000}")
    @Transactional
    public void autoCancelExpiredPending() {
        Instant cutoff = Instant.now().minusMillis(appProperties.getBooking().getPendingTimeoutMs());
        List<Booking> expired = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);
        if (expired.isEmpty()) {
            return;
        }
        expired.forEach(b -> {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancelReason("Auto-cancelled: not confirmed within timeout");
        });
        bookingRepository.saveAll(expired);
        expired.forEach(b -> {
            notificationService.notifyBookingStatusChange(b, NotificationType.BOOKING_CANCELLED,
                    "Booking cancelled",
                    "Your booking at " + b.getRestaurant().getName() + " on " + b.getBookingDate()
                            + " was auto-cancelled — the restaurant didn't confirm it in time");
            waitlistService.notifyTableFreed(b);
            auditLogService.record(null, "BOOKING_AUTO_CANCELLED", "Booking", b.getId(), "PENDING -> CANCELLED (timeout)");
            meterRegistry.counter("bookings.status.transitions.total", "status", "CANCELLED", "trigger", "auto").increment();
        });
        log.info("Auto-cancelled {} expired PENDING bookings", expired.size());
    }

    @Scheduled(fixedDelayString = "${app.booking.scheduler-interval-ms:60000}")
    @Transactional
    public void autoCompletePastBookings() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LocalTime now = LocalTime.now(ZoneId.of("UTC"));
        List<Booking> toComplete = bookingRepository.findConfirmedPastEnd(today, now);
        if (toComplete.isEmpty()) {
            return;
        }
        toComplete.forEach(b -> b.setStatus(BookingStatus.COMPLETED));
        bookingRepository.saveAll(toComplete);
        toComplete.forEach(b -> {
            notificationService.notifyBookingStatusChange(b, NotificationType.BOOKING_COMPLETED,
                    "Visit completed",
                    "Thanks for visiting " + b.getRestaurant().getName() + "! Leave a review to share your experience");
            auditLogService.record(null, "BOOKING_AUTO_COMPLETED", "Booking", b.getId(), "CONFIRMED -> COMPLETED (auto)");
            meterRegistry.counter("bookings.status.transitions.total", "status", "COMPLETED", "trigger", "auto").increment();
        });
        log.info("Auto-completed {} past CONFIRMED bookings", toComplete.size());
    }
}
