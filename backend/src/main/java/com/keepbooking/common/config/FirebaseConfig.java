package com.keepbooking.common.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.RequiredArgsConstructor;

// Bean only exists when app.firebase.enabled=true — most environments (local dev, CI) have no
// service account key, and PushNotificationService degrades to a no-op via Optional<FirebaseMessaging>.
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    private final AppProperties appProperties;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        try (FileInputStream serviceAccount = new FileInputStream(appProperties.getFirebase().getCredentialsPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        }
    }
}
