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
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;

/**
 * Unit tests for the availability-check critical path (tz2.txt §10 / §21) —
 * status/hours/time-window validation and filtering of already-booked tables.
 * Working-hours resolution itself (weekly schedule, overrides, overnight carry-over) is
 * covered in {@link WorkingHoursResolverTest}; here it's mocked to isolate this service's logic.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private WorkingHoursResolver workingHoursResolver;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private BookingRepository bookingRepository;

    private AvailabilityService availabilityService;

    private static final Long RESTAURANT_ID = 1L;
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private Restaurant activeRestaurant() {
        return Restaurant.builder().id(RESTAURANT_ID).name("Test Restaurant").status(RestaurantStatus.ACTIVE).build();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(restaurantRepository, workingHoursResolver,
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
    void throwsWhenResolverReportsClosed() {
        Restaurant restaurant = activeRestaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursResolver.isOpenAt(RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0)))
                .thenReturn(false);

        assertThatThrownBy(() -> availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_RESTAURANT_CLOSED);
    }

    @Test
    void returnsOnlyTablesNotAlreadyBookedForTheSlot() {
        Restaurant restaurant = activeRestaurant();
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        RestaurantTable free = RestaurantTable.builder()
                .id(1L).hall(hall).number("T1").capacity(4).status(TableStatus.ACTIVE).build();
        RestaurantTable booked = RestaurantTable.builder()
                .id(2L).hall(hall).number("T2").capacity(4).status(TableStatus.ACTIVE).build();

        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursResolver.isOpenAt(RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0)))
                .thenReturn(true);
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
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        RestaurantTable booked = RestaurantTable.builder()
                .id(1L).hall(hall).number("T1").capacity(4).status(TableStatus.ACTIVE).build();

        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(java.util.Optional.of(restaurant));
        when(workingHoursResolver.isOpenAt(RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0)))
                .thenReturn(true);
        when(tableRepository.findCandidatesForAvailability(eq(RESTAURANT_ID), anyInt()))
                .thenReturn(List.of(booked));
        when(bookingRepository.findBookedTableIds(eq(RESTAURANT_ID), eq(TOMORROW), any(), any()))
                .thenReturn(List.of(booked.getId()));

        List<TableDto> available = availabilityService.getAvailableTables(
                RESTAURANT_ID, TOMORROW, LocalTime.of(12, 0), LocalTime.of(13, 0), 2);

        assertThat(available).isEmpty();
    }

    @Test
    void hasFreeTablesNowReturnsFalseWhenRestaurantNotActive() {
        Restaurant restaurant = activeRestaurant();
        restaurant.setStatus(RestaurantStatus.DRAFT);

        assertThat(availabilityService.hasFreeTablesNow(restaurant, 2)).isFalse();
    }

    @Test
    void hasFreeTablesNowReturnsFalseWhenClosedNow() {
        Restaurant restaurant = activeRestaurant();
        when(workingHoursResolver.isOpenAt(eq(RESTAURANT_ID), any(), any(), any())).thenReturn(false);

        assertThat(availabilityService.hasFreeTablesNow(restaurant, 2)).isFalse();
    }

    @Test
    void hasFreeTablesNowReturnsFalseWhenNoCandidateTables() {
        Restaurant restaurant = activeRestaurant();
        when(workingHoursResolver.isOpenAt(eq(RESTAURANT_ID), any(), any(), any())).thenReturn(true);
        when(tableRepository.findCandidatesForAvailability(eq(RESTAURANT_ID), anyInt())).thenReturn(List.of());

        assertThat(availabilityService.hasFreeTablesNow(restaurant, 2)).isFalse();
    }

    @Test
    void hasFreeTablesNowReturnsFalseWhenAllCandidatesAreBooked() {
        Restaurant restaurant = activeRestaurant();
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        RestaurantTable booked = RestaurantTable.builder()
                .id(1L).hall(hall).number("T1").capacity(4).status(TableStatus.ACTIVE).build();

        when(workingHoursResolver.isOpenAt(eq(RESTAURANT_ID), any(), any(), any())).thenReturn(true);
        when(tableRepository.findCandidatesForAvailability(eq(RESTAURANT_ID), anyInt())).thenReturn(List.of(booked));
        when(bookingRepository.findBookedTableIds(eq(RESTAURANT_ID), any(), any(), any()))
                .thenReturn(List.of(booked.getId()));

        assertThat(availabilityService.hasFreeTablesNow(restaurant, 2)).isFalse();
    }

    @Test
    void hasFreeTablesNowReturnsTrueWhenAtLeastOneCandidateIsFree() {
        Restaurant restaurant = activeRestaurant();
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        RestaurantTable free = RestaurantTable.builder()
                .id(1L).hall(hall).number("T1").capacity(4).status(TableStatus.ACTIVE).build();
        RestaurantTable booked = RestaurantTable.builder()
                .id(2L).hall(hall).number("T2").capacity(4).status(TableStatus.ACTIVE).build();

        when(workingHoursResolver.isOpenAt(eq(RESTAURANT_ID), any(), any(), any())).thenReturn(true);
        when(tableRepository.findCandidatesForAvailability(eq(RESTAURANT_ID), anyInt()))
                .thenReturn(List.of(free, booked));
        when(bookingRepository.findBookedTableIds(eq(RESTAURANT_ID), any(), any(), any()))
                .thenReturn(List.of(booked.getId()));

        assertThat(availabilityService.hasFreeTablesNow(restaurant, 2)).isTrue();
    }
}
