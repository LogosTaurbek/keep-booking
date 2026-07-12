package com.keepbooking.notification.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.keepbooking.notification.model.NotificationOutbox;
import com.keepbooking.notification.repository.NotificationOutboxRepository;

import lombok.RequiredArgsConstructor;

/**
 * Polls PENDING notification outbox events due for (re)delivery and hands each one to
 * {@link NotificationOutboxProcessor} (tz2.txt §14). A plain scheduled poll rather than a broker
 * is deliberate for this MVP stage - tz2.txt §2 explicitly allows "@Async + DB-outbox" before a
 * broker is introduced.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxWorker {

    private static final int BATCH_SIZE = 50;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxProcessor processor;

    @Scheduled(fixedDelayString = "${app.notification.outbox-interval-ms:30000}")
    public void processPending() {
        List<NotificationOutbox> batch = outboxRepository.findBatchToProcess(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        batch.forEach(processor::processOne);
    }
}
