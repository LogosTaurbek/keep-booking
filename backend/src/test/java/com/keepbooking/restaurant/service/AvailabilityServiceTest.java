package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.TableDto;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.model.TableStatus;
import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;

/**
 * Unit tests for the availability-check critical path (tz2.txt §10 / §21) —
 * status/hours/time-window validation and filtering of already-booked tables.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private WorkingHoursRepository workingHoursRepository;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private BookingRepository bookingRepository;

    private AvailabilityService availabilityService;

    private static final Long RESTAURANT_ID = 1L;
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final int TOMORROW_DOW = TOMORROW.getDayOfWeek().getValue();

    private Restaurant activeRestaurant() {
        return Restaurant.builder().id(RESTAURANT_ID).name("Test Restaurant").status(RestaurantStatus.ACTIVE).build();
    }

    private WorkingHours openAllDay(Restaurant restaurant) {
        return WorkingHours.builder()
                .restaurant(restaurant).dayOfWeek(TOMORROW_DOW)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(23, 0)).isDayOff(false)
                .build();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(restaurantRepository, workingHoursRepository,
                tableRepository, bookingRepository);
    }

    @Test
    void throwsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void throwsWhenRestaurantNotActive() {
        Restaurant restaurant = activeRestaurant();
        restaurant.setStatus(RestaurantStatus.DRAFT);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_ACTIVE);
    }

    @Test
    void throwsWhenFromIsNotBeforeTo() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(activeRestaurant()));

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(14, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_INVALID_TIME);
    }

    @Test
    void throwsWhenRequestedDateTimeIsInThePast() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(activeRestaurant()));

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, LocalDate.now().minusDays(1), LocalTime.of(12, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_INVALID_TIME);
    }

    @Test
    void throwsWhenNoWorkingHoursEntryForRequestedDay() {
        Restaurant restaurant = activeRestaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(RESTAURANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_RESTAURANT_CLOSED);
    }

    @Test
    void throwsWhenRequestedDayIsMarkedAsDayOff() {
        Restaurant restaurant = activeRestaurant();
        WorkingHours dayOff = WorkingHours.builder()
                .restaurant(restaurant).dayOfWeek(TOMORROW_DOW).isDayOff(true).build();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(RESTAURANT_ID)).thenReturn(List.of(dayOff));

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_RESTAURANT_CLOSED);
    }

    @Test
    void throwsWhenRequestedSlotIsOutsideOpenHours() {
        Restaurant restaurant = activeRestaurant();
        WorkingHours hours = openAllDay(restaurant); // 09:00-23:00
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(RESTAURANT_ID)).thenReturn(List.of(hours));

        // requested 07:00-08:00, before opening at 09:00
        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(7, 0), LocalTime.of(8, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_RESTAURANT_CLOSED);
    }

    @Test
    void returnsOnlyTablesNotAlreadyBookedForTheSlot() {
        Restaurant restaurant = activeRestaurant();
        WorkingHours hours = openAllDay(restaurant);
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        RestaurantTable free = RestaurantTable.builder()
                .id(1L).hall(hall).number("T1").capacity(4).status(TableStatus.ACTIVE).build();
        RestaurantTable booked = RestaurantTable.builder()
                .id(2L).hall(hall).number("T2").capacity(4).status(TableStatus.ACTIVE).build();

        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(RESTAURANT_ID)).thenReturn(List.of(hours));
        when(tableRepository.findCandidatesForAvailability(eq(RESTAURANT_ID), anyInt()))
                .thenReturn(List.of(free, booked));
        when(bookingRepository.findBookedTableIds(eq(RESTAURANT_ID), eq(TOMORROW), any(), any()))
                .thenReturn(List.of(booked.getId()));

        List<TableDto> available = availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2);

        assertThat(available).extracting(TableDto::getId).containsExactly(free.getId());
    }

    @Test
    void returnsEmptyListWhenAllCandidateTablesAreBooked() {
        Restaurant restaurant = activeRestaurant();
        WorkingHours hours = openAllDay(restaurant);
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        RestaurantTable booked = RestaurantTable.builder()
                .id(1L).hall(hall).number("T1").capacity(4).status(TableStatus.ACTIVE).build();

        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(RESTAURANT_ID)).thenReturn(List.of(hours));
        when(tableRepository.findCandidatesForAvailability(eq(RESTAURANT_ID), anyInt()))
                .thenReturn(List.of(booked));
        when(bookingRepository.findBookedTableIds(eq(RESTAURANT_ID), eq(TOMORROW), any(), any()))
                .thenReturn(List.of(booked.getId()));

        List<TableDto> available = availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2);

        assertThat(available).isEmpty();
    }
}
