package com.keepbooking.restaurant.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.restaurant.dto.MapRestaurantDto;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MapService {

    private final RestaurantRepository restaurantRepository;
    private final AvailabilityService availabilityService;

    @Cacheable(value = "mapRestaurants",
            key = "'bbox:' + #minLat + ':' + #maxLat + ':' + #minLng + ':' + #maxLng + ':' + #guests + ':' + #limit")
    @Transactional(readOnly = true)
    public List<MapRestaurantDto> getRestaurantsInBoundingBox(BigDecimal minLat, BigDecimal maxLat,
                                                                BigDecimal minLng, BigDecimal maxLng,
                                                                int guests, int limit) {
        Page<Restaurant> page = restaurantRepository.findInBoundingBox(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, limit));
        return page.getContent().stream().map(r -> toDto(r, guests)).toList();
    }

    @Cacheable(value = "mapRestaurants",
            key = "'radius:' + #lat + ':' + #lng + ':' + #radiusKm + ':' + #guests + ':' + #limit")
    @Transactional(readOnly = true)
    public List<MapRestaurantDto> getRestaurantsNearby(double lat, double lng, double radiusKm,
                                                         int guests, int limit) {
        Page<Restaurant> page = restaurantRepository.findNearby(
                lat, lng, radiusKm * 1000, PageRequest.of(0, limit));
        return page.getContent().stream().map(r -> toDto(r, guests)).toList();
    }

    private MapRestaurantDto toDto(Restaurant r, int guests) {
        return MapRestaurantDto.builder()
                .id(r.getId())
                .name(r.getName())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .status(r.getStatus())
                .rating(r.getRating())
                .hasFreeTablesNow(availabilityService.hasFreeTablesNow(r, guests))
                .build();
    }
}
