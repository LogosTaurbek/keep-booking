package com.keepbooking.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.notification.model.DevicePlatform;
import com.keepbooking.notification.model.DeviceToken;
import com.keepbooking.notification.repository.DeviceTokenRepository;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(Long userId, String token, DevicePlatform platform) {
        // Same token can resurface under a different account (logout/login with another user on the
        // same device) — reassign ownership rather than erroring, same idempotency spirit as Favorite add/remove.
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .orElseGet(() -> DeviceToken.builder().token(token).build());
        deviceToken.setUser(userRepository.getReferenceById(userId));
        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void unregister(Long userId, String token) {
        deviceTokenRepository.deleteByTokenAndUserId(token, userId);
    }
}
