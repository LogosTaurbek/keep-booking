package com.keepbooking.restaurant.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.MapRestaurantDto;
import com.keepbooking.restaurant.service.MapService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Map", description = "Restaurant pins for the map view (bounding box or radius), with a live free-tables-now flag")
@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class MapController {

    private static final int MAX_LIMIT = 500;

    private final MapService mapService;

    @Operation(summary = "Restaurants for the map (public): pass either a bounding box "
            + "(minLat/maxLat/minLng/maxLng) or a radius (lat/lng[/radiusKm])")
    @GetMapping
    public ResponseEntity<List<MapRestaurantDto>> getRestaurants(
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(defaultValue = "2") int guests,
            @RequestParam(defaultValue = "200") int limit) {

        boolean hasBbox = minLat != null && maxLat != null && minLng != null && maxLng != null;
        boolean hasRadius = lat != null && lng != null;
        if (hasBbox == hasRadius) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Provide either a bounding box (minLat/maxLat/minLng/maxLng) or a radius (lat/lng)");
        }

        int boundedLimit = Math.min(limit, MAX_LIMIT);
        List<MapRestaurantDto> restaurants = hasBbox
                ? mapService.getRestaurantsInBoundingBox(minLat, maxLat, minLng, maxLng, guests, boundedLimit)
                : mapService.getRestaurantsNearby(lat, lng, radiusKm, guests, boundedLimit);
        return ResponseEntity.ok(restaurants);
    }
}
