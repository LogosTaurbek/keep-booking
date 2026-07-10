package com.keepbooking.restaurant.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.common.storage.FileStorageService;
import com.keepbooking.restaurant.dto.RestaurantPhotoDto;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantPhoto;
import com.keepbooking.restaurant.repository.RestaurantPhotoRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RestaurantPhotoService {

    private final RestaurantPhotoRepository restaurantPhotoRepository;
    private final RestaurantRepository restaurantRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public RestaurantPhotoDto upload(Long userId, Long restaurantId, MultipartFile file) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        verifyOwner(restaurant, userId);

        String url = fileStorageService.upload(file, "restaurants/" + restaurantId);
        int nextPosition = restaurantPhotoRepository.findByRestaurantIdOrderByPositionAsc(restaurantId).size();

        RestaurantPhoto photo = RestaurantPhoto.builder()
                .restaurant(restaurant)
                .url(url)
                .position(nextPosition)
                .build();

        return toDto(restaurantPhotoRepository.save(photo));
    }

    @Transactional(readOnly = true)
    public List<RestaurantPhotoDto> listByRestaurant(Long restaurantId) {
        return restaurantPhotoRepository.findByRestaurantIdOrderByPositionAsc(restaurantId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long restaurantId, Long photoId) {
        RestaurantPhoto photo = restaurantPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_PHOTO_NOT_FOUND));
        if (!photo.getRestaurant().getId().equals(restaurantId)) {
            throw new ApiException(ErrorCode.RESTAURANT_PHOTO_NOT_FOUND);
        }
        verifyOwner(photo.getRestaurant(), userId);

        restaurantPhotoRepository.delete(photo);
        fileStorageService.delete(photo.getUrl());
    }

    private void verifyOwner(Restaurant restaurant, Long userId) {
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }
    }

    private RestaurantPhotoDto toDto(RestaurantPhoto p) {
        return RestaurantPhotoDto.builder()
                .id(p.getId())
                .restaurantId(p.getRestaurant().getId())
                .url(p.getUrl())
                .position(p.getPosition())
                .build();
    }
}
