package com.keepbooking.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.notification.dto.RegisterDeviceTokenRequest;
import com.keepbooking.notification.service.DeviceTokenService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Device Tokens", description = "FCM device token registration for push notifications")
@RestController
@RequestMapping("/api/v1/device-tokens")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "Register a device token for push notifications")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> register(@AuthenticationPrincipal User user,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.register(user.getId(), request.getToken(), request.getPlatform());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unregister a device token (e.g. on logout)")
    @DeleteMapping("/{token}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal User user, @PathVariable String token) {
        deviceTokenService.unregister(user.getId(), token);
        return ResponseEntity.noContent().build();
    }
}
