package com.keepbooking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.keepbooking.booking.model.Booking;
import com.keepbooking.notification.model.NotificationOutbox;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.model.OutboxStatus;
import com.keepbooking.notification.repository.NotificationOutboxRepository;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.user.model.User;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * tz2.txt §14: outbox delivery must retry with backoff and eventually dead-letter rather than
 * retry forever, and must mark success so a delivered event is never re-sent by the next poll.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxProcessorTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private PushNotificationService pushNotificationService;

    private NotificationOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationOutboxProcessor(outboxRepository, pushNotificationService, new SimpleMeterRegistry());
    }

    private NotificationOutbox event(int attempts) {
        Booking booking = Booking.builder().id(1L)
                .restaurant(Restaurant.builder().id(1L).build()).build();
        return NotificationOutbox.builder()
                .id(10L)
                .user(User.builder().id(1L).build())
                .booking(booking)
                .type(NotificationType.BOOKING_CONFIRMED)
                .title("t").message("m")
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .build();
    }

    @Test
    void marksEventSentOnSuccessfulDelivery() throws FirebaseMessagingException {
        NotificationOutbox event = event(0);
        doNothing().when(pushNotificationService).send(anyLong(), anyString(), anyString(), anyMap());

        processor.processOne(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(outboxRepository).save(event);
    }

    @Test
    void incrementsAttemptsAndSchedulesRetryOnFailureBelowMaxAttempts() throws FirebaseMessagingException {
        NotificationOutbox event = event(0);
        doThrow(new RuntimeException("FCM down")).when(pushNotificationService)
                .send(anyLong(), anyString(), anyString(), anyMap());

        processor.processOne(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("FCM down");
        assertThat(event.getNextAttemptAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void movesToDeadLetterAfterMaxAttemptsExhausted() throws FirebaseMessagingException {
        NotificationOutbox event = event(4);
        doThrow(new RuntimeException("FCM down")).when(pushNotificationService)
                .send(anyLong(), anyString(), anyString(), anyMap());

        processor.processOne(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(event.getAttempts()).isEqualTo(5);
    }
}
