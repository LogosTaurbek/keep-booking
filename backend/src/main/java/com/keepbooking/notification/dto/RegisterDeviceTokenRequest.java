package com.keepbooking.notification.dto;

import com.keepbooking.notification.model.DevicePlatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDeviceTokenRequest {

    @NotBlank
    private String token;

    @NotNull
    private DevicePlatform platform;
}
