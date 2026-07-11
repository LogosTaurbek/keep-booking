package com.keepbooking.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.keepbooking.notification.model.DeviceToken;
import com.keepbooking.notification.repository.DeviceTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// FirebaseMessaging bean only exists when app.firebase.enabled=true (see FirebaseConfig) — everywhere
// else this degrades to a no-op so in-app notifications keep working without FCM credentials.
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final Optional<FirebaseMessaging> firebaseMessaging;

    @Transactional
    public void send(Long userId, String title, String body, Map<String, String> data) {
        if (firebaseMessaging.isEmpty()) {
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .addAllTokens(tokens.stream().map(DeviceToken::getToken).toList())
                .build();

        try {
            BatchResponse response = firebaseMessaging.get().sendEachForMulticast(message);
            if (response.getFailureCount() > 0) {
                removeUnregisteredTokens(tokens, response.getResponses());
            }
        } catch (FirebaseMessagingException e) {
            log.warn("Failed to send push notification batch for user {}", userId, e);
        }
    }

    // A token stops being valid when the app is uninstalled or the FCM registration expires;
    // FCM reports this per-token instead of failing the whole batch, so we prune just those.
    private void removeUnregisteredTokens(List<DeviceToken> tokens, List<SendResponse> responses) {
        List<String> invalid = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (!sendResponse.isSuccessful()
                    && sendResponse.getException() != null
                    && sendResponse.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                invalid.add(tokens.get(i).getToken());
            }
        }
        if (!invalid.isEmpty()) {
            deviceTokenRepository.deleteAllByTokenIn(invalid);
        }
    }
}
