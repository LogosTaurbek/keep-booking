package com.keepbooking.notification.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.notification.model.NotificationOutbox;
import com.keepbooking.notification.model.OutboxStatus;
import com.keepbooking.notification.repository.NotificationOutboxRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delivers a single outbox event (tz2.txt §14): each event gets its own transaction so a slow or
 * failed Firebase call for one user doesn't hold a DB transaction open across the whole batch
 * being polled by {@link NotificationOutboxWorker}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxProcessor {

    // Fixed cap rather than unbounded retry - a permanently-broken device token or FCM outage
    // shouldn't retry forever; DEAD_LETTER makes the failure visible instead of silently looping.
    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_BACKOFF_SECONDS = 10;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final NotificationOutboxRepository outboxRepository;
    private final PushNotificationService pushNotificationService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void processOne(NotificationOutbox event) {
        try {
            Map<String, String> data = event.getBooking() != null
                    ? Map.of("type", event.getType().name(), "bookingId", String.valueOf(event.getBooking().getId()))
                    : Map.of("type", event.getType().name());
            pushNotificationService.send(event.getUser().getId(), event.getTitle(), event.getMessage(), data);
            event.setStatus(OutboxStatus.SENT);
            event.setLastError(null);
            meterRegistry.counter("notifications.outbox.delivered.total", "outcome", "sent").increment();
        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(e.getMessage());
            if (attempts >= MAX_ATTEMPTS) {
                event.setStatus(OutboxStatus.DEAD_LETTER);
                meterRegistry.counter("notifications.outbox.delivered.total", "outcome", "dead_letter").increment();
                log.warn("Notification outbox event {} moved to DEAD_LETTER after {} attempts", event.getId(), attempts, e);
            } else {
                event.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds(attempts)));
                meterRegistry.counter("notifications.outbox.delivered.total", "outcome", "retry").increment();
                log.warn("Notification outbox event {} failed (attempt {}/{}), retrying at {}",
                        event.getId(), attempts, MAX_ATTEMPTS, event.getNextAttemptAt(), e);
            }
        }
        outboxRepository.save(event);
    }

    private long backoffSeconds(int attempts) {
        return Math.min(MAX_BACKOFF_SECONDS, BASE_BACKOFF_SECONDS * (1L << (attempts - 1)));
    }
}
