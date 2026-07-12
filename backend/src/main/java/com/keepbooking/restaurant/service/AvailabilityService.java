package com.keepbooking.restaurant.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.restaurant.dto.TableDto;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final RestaurantRepository restaurantRepository;
    private final WorkingHoursResolver workingHoursResolver;
    private final TableRepository tableRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<TableDto> getAvailableTables(Long restaurantId, LocalDate date, LocalTime from, LocalTime to, Integer guests) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));

        if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new ApiException(ErrorCode.RESTAURANT_NOT_ACTIVE);
        }

        validateTimeRange(date, from, to, restaurant.getTimezone());
        validateOpenHours(restaurantId, date, from, to);

        List<RestaurantTable> candidates = tableRepository.findCandidatesForAvailability(restaurantId, guests);
        Set<Long> bookedTableIds = Set.copyOf(
                bookingRepository.findBookedTableIds(restaurantId, date, from, to));

        return candidates.stream()
                .filter(t -> !bookedTableIds.contains(t.getId()))
                .map(this::toDto)
                .toList();
    }

    private void validateTimeRange(LocalDate date, LocalTime from, LocalTime to, String restaurantTimezone) {
        if (!from.isBefore(to)) {
            throw new ApiException(ErrorCode.BOOKING_INVALID_TIME, "from must be before to");
        }
        // date/from are local to the restaurant's own timezone, not UTC - "now" must be compared in that same zone
        if (LocalDateTime.of(date, from).isBefore(LocalDateTime.now(ZoneId.of(restaurantTimezone)))) {
            throw new ApiException(ErrorCode.BOOKING_INVALID_TIME, "Requested time is in the past");
        }
    }

    private void validateOpenHours(Long restaurantId, LocalDate date, LocalTime from, LocalTime to) {
        if (!workingHoursResolver.isOpenAt(restaurantId, date, from, to)) {
            throw new ApiException(ErrorCode.BOOKING_RESTAURANT_CLOSED);
        }
    }

    private TableDto toDto(RestaurantTable t) {
        return TableDto.builder()
                .id(t.getId())
                .hallId(t.getHall().getId())
                .number(t.getNumber())
                .capacity(t.getCapacity())
                .minCapacity(t.getMinCapacity())
                .shape(t.getShape())
                .type(t.getType())
                .posX(t.getPosX())
                .posY(t.getPosY())
                .width(t.getWidth())
                .height(t.getHeight())
                .rotation(t.getRotation())
                .isVip(t.getIsVip())
                .isSofa(t.getIsSofa())
                .nearWindow(t.getNearWindow())
                .hasSocket(t.getHasSocket())
                .isSmoking(t.getIsSmoking())
                .status(t.getStatus())
                .build();
    }
}
