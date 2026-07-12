package com.keepbooking.waitlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.notification.model.NotificationType;
import com.keepbooking.notification.service.NotificationService;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;
import com.keepbooking.waitlist.dto.CreateWaitlistEntryRequest;
import com.keepbooking.waitlist.dto.WaitlistEntryDto;
import com.keepbooking.waitlist.model.WaitlistEntry;
import com.keepbooking.waitlist.model.WaitlistStatus;
import com.keepbooking.waitlist.repository.WaitlistRepository;

/**
 * tz2.txt §11.4 / §25: joining is idempotent per slot, and a freed booking notifies only the
 * single longest-waiting matching entry (not a broadcast to everyone on the list).
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository waitlistRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private WaitlistService waitlistService;

    private static final Long USER_ID = 1L;
    private static final Long RESTAURANT_ID = 10L;
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(waitlistRepository, restaurantRepository, userRepository, notificationService);
    }

    private Restaurant activeRestaurant() {
        return Restaurant.builder().id(RESTAURANT_ID).name("Test Restaurant")
                .status(RestaurantStatus.ACTIVE).build();
    }

    private CreateWaitlistEntryRequest request() {
        CreateWaitlistEntryRequest r = new CreateWaitlistEntryRequest();
        r.setRestaurantId(RESTAURANT_ID);
        r.setBookingDate(TOMORROW);
        r.setTimeFrom(LocalTime.of(19, 0));
        r.setTimeTo(LocalTime.of(20, 0));
        r.setGuestCount(2);
        return r;
    }

    @Test
    void joinThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitlistService.join(USER_ID, request()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void joinThrowsWhenRequestedTimeIsInThePast() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(activeRestaurant()));
        CreateWaitlistEntryRequest r = request();
        r.setBookingDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> waitlistService.join(USER_ID, r))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_INVALID_TIME);
    }

    @Test
    void joinIsIdempotentAndReturnsExistingActiveEntry() {
        Restaurant restaurant = activeRestaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        WaitlistEntry existing = WaitlistEntry.builder().id(5L).restaurant(restaurant)
                .user(User.builder().id(USER_ID).build()).bookingDate(TOMORROW)
                .timeFrom(LocalTime.of(19, 0)).timeTo(LocalTime.of(20, 0)).guestCount(2)
                .status(WaitlistStatus.ACTIVE).build();
        when(waitlistRepository.findByUserIdAndRestaurantIdAndBookingDateAndTimeFromAndTimeToAndStatus(
                eq(USER_ID), eq(RESTAURANT_ID), eq(TOMORROW), eq(LocalTime.of(19, 0)), eq(LocalTime.of(20, 0)),
                eq(WaitlistStatus.ACTIVE))).thenReturn(Optional.of(existing));

        WaitlistEntryDto dto = waitlistService.join(USER_ID, request());

        assertThat(dto.getId()).isEqualTo(5L);
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void joinSavesNewEntryWhenNoneExists() {
        Restaurant restaurant = activeRestaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(waitlistRepository.findByUserIdAndRestaurantIdAndBookingDateAndTimeFromAndTimeToAndStatus(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());
        when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> {
            WaitlistEntry e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });

        WaitlistEntryDto dto = waitlistService.join(USER_ID, request());

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getStatus()).isEqualTo(WaitlistStatus.ACTIVE);
    }

    @Test
    void leaveThrowsWhenEntryNotFoundOrNotOwned() {
        when(waitlistRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waitlistService.leave(USER_ID, 1L))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WAITLIST_ENTRY_NOT_FOUND);
    }

    @Test
    void leaveMarksEntryCancelled() {
        WaitlistEntry entry = WaitlistEntry.builder().id(1L).status(WaitlistStatus.ACTIVE).build();
        when(waitlistRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(entry));

        waitlistService.leave(USER_ID, 1L);

        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        verify(waitlistRepository).save(entry);
    }

    private Booking freedBooking(RestaurantTable table) {
        Restaurant restaurant = activeRestaurant();
        return Booking.builder().id(99L).restaurant(restaurant).table(table)
                .bookingDate(TOMORROW).timeFrom(LocalTime.of(19, 0)).timeTo(LocalTime.of(20, 0)).build();
    }

    @Test
    void notifyTableFreedDoesNothingWhenNoMatchingEntries() {
        RestaurantTable table = RestaurantTable.builder().id(1L)
                .hall(Hall.builder().id(1L).build()).capacity(4).build();
        when(waitlistRepository.findMatchingActive(eq(RESTAURANT_ID), eq(TOMORROW), eq(LocalTime.of(19, 0)),
                eq(LocalTime.of(20, 0)), eq(4), any(PageRequest.class))).thenReturn(List.of());

        waitlistService.notifyTableFreed(freedBooking(table));

        verify(notificationService, never()).notifyUser(any(), any(), anyString(), anyString());
    }

    @Test
    void notifyTableFreedNotifiesOnlyEarliestMatchingEntrySkippingBelowMinCapacity() {
        RestaurantTable table = RestaurantTable.builder().id(1L)
                .hall(Hall.builder().id(1L).build()).capacity(4).minCapacity(2).build();
        WaitlistEntry tooSmallParty = WaitlistEntry.builder().id(1L)
                .user(User.builder().id(2L).build()).guestCount(1).status(WaitlistStatus.ACTIVE).build();
        WaitlistEntry fittingParty = WaitlistEntry.builder().id(2L)
                .user(User.builder().id(3L).build()).guestCount(3).status(WaitlistStatus.ACTIVE).build();
        when(waitlistRepository.findMatchingActive(eq(RESTAURANT_ID), eq(TOMORROW), eq(LocalTime.of(19, 0)),
                eq(LocalTime.of(20, 0)), eq(4), any(PageRequest.class)))
                .thenReturn(List.of(tooSmallParty, fittingParty));

        waitlistService.notifyTableFreed(freedBooking(table));

        assertThat(fittingParty.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
        assertThat(fittingParty.getNotifiedAt()).isNotNull();
        assertThat(tooSmallParty.getStatus()).isEqualTo(WaitlistStatus.ACTIVE);
        verify(notificationService).notifyUser(eq(fittingParty.getUser()), eq(NotificationType.WAITLIST_TABLE_AVAILABLE),
                anyString(), anyString());
        verify(notificationService, never()).notifyUser(eq(tooSmallParty.getUser()), any(), any(), any());
    }
}
