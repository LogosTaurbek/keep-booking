package com.keepbooking.restaurant.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.UpsertWorkingHoursOverrideRequest;
import com.keepbooking.restaurant.dto.WorkingHoursDto;
import com.keepbooking.restaurant.dto.WorkingHoursItemRequest;
import com.keepbooking.restaurant.dto.WorkingHoursOverrideDto;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.model.WorkingHoursOverride;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.WorkingHoursOverrideRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    private final WorkingHoursRepository workingHoursRepository;
    private final WorkingHoursOverrideRepository overrideRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<WorkingHoursDto> listByRestaurant(Long restaurantId) {
        return workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(restaurantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<WorkingHoursDto> replaceWeek(Long userId, Long restaurantId, List<WorkingHoursItemRequest> items) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        verifyOwner(restaurant, userId);

        workingHoursRepository.deleteByRestaurantId(restaurantId);

        List<WorkingHours> entries = items.stream()
                .map(item -> {
                    boolean isDayOff = Boolean.TRUE.equals(item.getIsDayOff());
                    validateHours(item.getOpenTime(), item.getCloseTime(), isDayOff);
                    return WorkingHours.builder()
                            .restaurant(restaurant)
                            .dayOfWeek(item.getDayOfWeek())
                            .openTime(item.getOpenTime())
                            .closeTime(item.getCloseTime())
                            .isDayOff(isDayOff)
                            .build();
                })
                .toList();

        return workingHoursRepository.saveAll(entries).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursOverrideDto> listOverrides(Long restaurantId) {
        return overrideRepository.findByRestaurantIdOrderByDate(restaurantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public WorkingHoursOverrideDto upsertOverride(Long userId, Long restaurantId, UpsertWorkingHoursOverrideRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        verifyOwner(restaurant, userId);

        boolean isClosed = Boolean.TRUE.equals(request.getIsClosed());
        validateHours(request.getOpenTime(), request.getCloseTime(), isClosed);

        WorkingHoursOverride override = overrideRepository.findByRestaurantIdAndDate(restaurantId, request.getDate())
                .orElseGet(() -> WorkingHoursOverride.builder().restaurant(restaurant).date(request.getDate()).build());
        override.setOpenTime(request.getOpenTime());
        override.setCloseTime(request.getCloseTime());
        override.setIsClosed(isClosed);

        return toDto(overrideRepository.save(override));
    }

    @Transactional
    public void deleteOverride(Long userId, Long restaurantId, LocalDate date) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        verifyOwner(restaurant, userId);
        overrideRepository.deleteByRestaurantIdAndDate(restaurantId, date);
    }

    private void verifyOwner(Restaurant restaurant, Long userId) {
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }
    }

    /**
     * A day/date that isn't fully closed needs both times set and distinct - equal open/close
     * (or a missing one) is ambiguous (24h-open isn't a supported concept here) rather than a
     * usable schedule.
     */
    private void validateHours(LocalTime openTime, LocalTime closeTime, boolean closed) {
        if (closed) {
            return;
        }
        if (openTime == null || closeTime == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "openTime and closeTime are required unless the day is marked closed");
        }
        if (openTime.equals(closeTime)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "openTime must not equal closeTime");
        }
    }

    private WorkingHoursDto toDto(WorkingHours wh) {
        return WorkingHoursDto.builder()
                .id(wh.getId())
                .restaurantId(wh.getRestaurant().getId())
                .dayOfWeek(wh.getDayOfWeek())
                .openTime(wh.getOpenTime())
                .closeTime(wh.getCloseTime())
                .isDayOff(wh.getIsDayOff())
                .build();
    }

    private WorkingHoursOverrideDto toDto(WorkingHoursOverride o) {
        return WorkingHoursOverrideDto.builder()
                .id(o.getId())
                .restaurantId(o.getRestaurant().getId())
                .date(o.getDate())
                .openTime(o.getOpenTime())
                .closeTime(o.getCloseTime())
                .isClosed(o.getIsClosed())
                .build();
    }
}
