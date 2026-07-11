package com.keepbooking.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.notification.model.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserId(Long userId);

    Optional<DeviceToken> findByToken(String token);

    void deleteByTokenAndUserId(String token, Long userId);

    void deleteAllByTokenIn(Collection<String> tokens);
}
