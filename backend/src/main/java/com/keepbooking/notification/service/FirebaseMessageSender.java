package com.keepbooking.notification.service;

import org.springframework.stereotype.Component;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;

import io.github.resilience4j.retry.annotation.Retry;

/**
 * Isolated in its own bean so {@code @Retry} goes through the Spring proxy - annotating a method
 * that {@link PushNotificationService} called on itself would silently never retry (Spring AOP
 * doesn't intercept self-invocation). Retry policy: see {@code resilience4j.retry.instances.pushNotification}.
 */
@Component
public class FirebaseMessageSender {

    @Retry(name = "pushNotification")
    public BatchResponse send(FirebaseMessaging firebaseMessaging, MulticastMessage message) throws FirebaseMessagingException {
        return firebaseMessaging.sendEachForMulticast(message);
    }
}
