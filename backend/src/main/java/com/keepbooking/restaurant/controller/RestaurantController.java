package com.keepbooking.restaurant.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.restaurant.dto.CreateRestaurantRequest;
import com.keepbooking.restaurant.dto.RestaurantDto;
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

    @Operation(summary = "Get active restaurants (public)")
    @GetMapping
    public ResponseEntity<PageResponse<RestaurantDto>> list(
            @RequestParam(required = false) Long cityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("rating").descending());
        return ResponseEntity.ok(restaurantService.listActive(cityId, pageable));
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
}
