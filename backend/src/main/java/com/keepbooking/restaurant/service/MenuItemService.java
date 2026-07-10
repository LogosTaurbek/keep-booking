package com.keepbooking.restaurant.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.CreateMenuItemRequest;
import com.keepbooking.restaurant.dto.MenuItemDto;
import com.keepbooking.restaurant.dto.UpdateMenuItemRequest;
import com.keepbooking.restaurant.model.MenuItem;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.MenuItemRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public MenuItemDto create(Long userId, CreateMenuItemRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        verifyOwner(restaurant, userId);

        MenuItem item = MenuItem.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .photoUrl(request.getPhotoUrl())
                .position(request.getPosition() != null ? request.getPosition() : 0)
                .build();

        return toDto(menuItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public MenuItemDto getById(Long id) {
        return toDto(findItem(id));
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> listByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdOrderByPositionAscNameAsc(restaurantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public MenuItemDto update(Long userId, Long id, UpdateMenuItemRequest request) {
        MenuItem item = findItem(id);
        verifyOwner(item.getRestaurant(), userId);

        if (request.getName() != null) item.setName(request.getName());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getPhotoUrl() != null) item.setPhotoUrl(request.getPhotoUrl());
        if (request.getIsAvailable() != null) item.setIsAvailable(request.getIsAvailable());
        if (request.getPosition() != null) item.setPosition(request.getPosition());

        return toDto(menuItemRepository.save(item));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        MenuItem item = findItem(id);
        verifyOwner(item.getRestaurant(), userId);
        menuItemRepository.delete(item);
    }

    private MenuItem findItem(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_ITEM_NOT_FOUND));
    }

    private void verifyOwner(Restaurant restaurant, Long userId) {
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }
    }

    private MenuItemDto toDto(MenuItem m) {
        return MenuItemDto.builder()
                .id(m.getId())
                .restaurantId(m.getRestaurant().getId())
                .name(m.getName())
                .description(m.getDescription())
                .price(m.getPrice())
                .category(m.getCategory())
                .photoUrl(m.getPhotoUrl())
                .isAvailable(m.getIsAvailable())
                .position(m.getPosition())
                .build();
    }
}
