package com.keepbooking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.notification.model.Notification;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.repository.NotificationRepository;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.user.model.User;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long NOTIFICATION_ID = 100L;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    private User user(Long id) {
        return User.builder().id(id).build();
    }

    private Notification notificationFor(Long ownerId) {
        return Notification.builder().id(NOTIFICATION_ID).user(user(ownerId))
                .type(NotificationType.BOOKING_CONFIRMED).title("t").message("m").isRead(false).build();
    }

    @Test
    void notifyBookingStatusChangeSavesNotificationForBookingOwner() {
        User owner = user(USER_ID);
        Booking booking = Booking.builder().id(1L).user(owner)
                .restaurant(Restaurant.builder().id(1L).build()).build();

        notificationService.notifyBookingStatusChange(booking, NotificationType.BOOKING_CONFIRMED, "Confirmed", "Your booking is confirmed");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsReadThrowsWhenNotificationNotFound() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAsReadThrowsWhenActorIsNotTheOwner() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notificationFor(USER_ID)));

        assertThatThrownBy(() -> notificationService.markAsRead(OTHER_USER_ID, NOTIFICATION_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadSetsIsReadTrueForOwner() {
        Notification notification = notificationFor(USER_ID);
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(USER_ID, NOTIFICATION_ID);

        assertThat(notification.getIsRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAllAsReadDelegatesToRepositoryBulkUpdate() {
        notificationService.markAllAsRead(USER_ID);

        verify(notificationRepository, times(1)).markAllAsRead(USER_ID);
    }
}
