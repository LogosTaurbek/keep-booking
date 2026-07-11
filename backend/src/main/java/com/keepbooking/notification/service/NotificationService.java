package com.keepbooking.notification.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.notification.dto.NotificationDto;
import com.keepbooking.notification.model.Notification;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public void notifyBookingStatusChange(Booking booking, NotificationType type, String title, String message) {
        Notification notification = Notification.builder()
                .user(booking.getUser())
                .type(type)
                .title(title)
                .message(message)
                .booking(booking)
                .build();
        notificationRepository.save(notification);

        pushNotificationService.send(booking.getUser().getId(), title, message,
                Map.of("type", type.name(), "bookingId", booking.getId().toString()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getMyNotifications(Long userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .bookingId(n.getBooking() != null ? n.getBooking().getId() : null)
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
