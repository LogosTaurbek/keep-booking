package com.keepbooking.favorite.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.favorite.model.Favorite;
import com.keepbooking.favorite.repository.FavoriteRepository;
import com.keepbooking.reference.model.Cuisine;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Transactional
    public void add(Long userId, Long restaurantId) {
        if (favoriteRepository.findByUserIdAndRestaurantId(userId, restaurantId).isPresent()) {
            return;
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));

        Favorite favorite = Favorite.builder()
                .user(userRepository.getReferenceById(userId))
                .restaurant(restaurant)
                .build();
        favoriteRepository.save(favorite);
    }

    @Transactional(readOnly = true)
    public List<RestaurantDto> getMyFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> toDto(f.getRestaurant()))
                .toList();
    }

    @Transactional
    public void remove(Long userId, Long restaurantId) {
        favoriteRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .ifPresent(favoriteRepository::delete);
    }

    private RestaurantDto toDto(Restaurant r) {
        return RestaurantDto.builder()
                .id(r.getId())
                .companyId(r.getCompany().getId())
                .name(r.getName())
                .description(r.getDescription())
                .address(r.getAddress())
                .cityId(r.getCity() != null ? r.getCity().getId() : null)
                .cityName(r.getCity() != null ? r.getCity().getName() : null)
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .timezone(r.getTimezone())
                .rating(r.getRating())
                .reviewsCount(r.getReviewsCount())
                .avgCheck(r.getAvgCheck())
                .status(r.getStatus())
                .cuisineSlugs(r.getCuisines().stream().map(Cuisine::getSlug).collect(Collectors.toSet()))
                .build();
    }
}
