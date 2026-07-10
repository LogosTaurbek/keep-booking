package com.keepbooking.restaurant.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.WorkingHoursDto;
import com.keepbooking.restaurant.dto.WorkingHoursItemRequest;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    private final WorkingHoursRepository workingHoursRepository;
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
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }

        workingHoursRepository.deleteByRestaurantId(restaurantId);

        List<WorkingHours> entries = items.stream()
                .map(item -> WorkingHours.builder()
                        .restaurant(restaurant)
                        .dayOfWeek(item.getDayOfWeek())
                        .openTime(item.getOpenTime())
                        .closeTime(item.getCloseTime())
                        .isDayOff(Boolean.TRUE.equals(item.getIsDayOff()))
                        .build())
                .toList();

        return workingHoursRepository.saveAll(entries).stream().map(this::toDto).toList();
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
}
