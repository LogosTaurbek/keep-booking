package com.keepbooking.notification.dto;

import java.time.Instant;

import com.keepbooking.notification.model.NotificationType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private Long bookingId;
    private Boolean isRead;
    private Instant createdAt;
}
