package com.keepbooking.restaurant.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.common.security.AccessControlService;
import com.keepbooking.restaurant.dto.CreateHallRequest;
import com.keepbooking.restaurant.dto.HallDto;
import com.keepbooking.restaurant.dto.UpdateHallRequest;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.HallRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HallService {

    private final HallRepository hallRepository;
    private final RestaurantRepository restaurantRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public HallDto create(Long userId, CreateHallRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        accessControlService.verifyCanManageRestaurant(userId, restaurant);

        Hall hall = Hall.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .floor(request.getFloor() != null ? request.getFloor() : 1)
                .canvasWidth(request.getCanvasWidth() != null ? request.getCanvasWidth() : 800)
                .canvasHeight(request.getCanvasHeight() != null ? request.getCanvasHeight() : 600)
                .build();

        return toDto(hallRepository.save(hall));
    }

    @Transactional(readOnly = true)
    public HallDto getById(Long hallId) {
        return toDto(findHall(hallId));
    }

    @Transactional(readOnly = true)
    public List<HallDto> listByRestaurant(Long restaurantId) {
        return hallRepository.findByRestaurantId(restaurantId).stream().map(this::toDto).toList();
    }

    @Transactional
    public HallDto update(Long userId, Long hallId, UpdateHallRequest request) {
        Hall hall = findHall(hallId);
        accessControlService.verifyCanManageRestaurant(userId, hall.getRestaurant());

        if (request.getName() != null) hall.setName(request.getName());
        if (request.getFloor() != null) hall.setFloor(request.getFloor());
        if (request.getCanvasWidth() != null) hall.setCanvasWidth(request.getCanvasWidth());
        if (request.getCanvasHeight() != null) hall.setCanvasHeight(request.getCanvasHeight());

        return toDto(hallRepository.save(hall));
    }

    @Transactional
    public void delete(Long userId, Long hallId) {
        Hall hall = findHall(hallId);
        accessControlService.verifyCanManageRestaurant(userId, hall.getRestaurant());
        hallRepository.delete(hall);
    }

    private Hall findHall(Long hallId) {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> new ApiException(ErrorCode.HALL_NOT_FOUND));
    }

    private HallDto toDto(Hall h) {
        return HallDto.builder()
                .id(h.getId())
                .restaurantId(h.getRestaurant().getId())
                .name(h.getName())
                .floor(h.getFloor())
                .canvasWidth(h.getCanvasWidth())
                .canvasHeight(h.getCanvasHeight())
                .build();
    }
}
