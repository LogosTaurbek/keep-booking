package com.keepbooking.restaurant.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.history.service.SearchHistoryService;
import com.keepbooking.restaurant.dto.CreateRestaurantRequest;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.restaurant.dto.UpdateRestaurantRequest;
import com.keepbooking.restaurant.service.RestaurantService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Restaurants", description = "Restaurant listing and management")
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final SearchHistoryService searchHistoryService;

    @Operation(summary = "Search active restaurants (public): name, cuisine, city, min rating")
    @GetMapping
    public ResponseEntity<PageResponse<RestaurantDto>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String cuisine,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        searchHistoryService.record(user != null ? user.getId() : null, name, cuisine, cityId, minRating);
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("rating").descending());
        return ResponseEntity.ok(restaurantService.search(cityId, name, cuisine, minRating, pageable));
    }

    @Operation(summary = "Find restaurants within a radius (public, for map view)")
    @GetMapping("/nearby")
    public ResponseEntity<PageResponse<RestaurantDto>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(restaurantService.findNearby(lat, lng, radiusKm, pageable));
    }

    @Operation(summary = "Get restaurant by ID (public)")
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getById(id));
    }

    @Operation(summary = "Create a new restaurant")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RestaurantDto> create(@AuthenticationPrincipal User user,
                                                @Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.create(user.getId(), request));
    }

    @Operation(summary = "Get my restaurants")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RestaurantDto>> getMyRestaurants(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(restaurantService.getMyRestaurants(user.getId()));
    }

    @Operation(summary = "Update a restaurant I own (partial update - only non-null fields are applied)")
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RestaurantDto> update(@AuthenticationPrincipal User user,
                                                @PathVariable Long id,
                                                @Valid @RequestBody UpdateRestaurantRequest request) {
        return ResponseEntity.ok(restaurantService.update(user.getId(), id, request));
    }
}
