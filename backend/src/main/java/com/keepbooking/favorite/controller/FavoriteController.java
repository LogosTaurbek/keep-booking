package com.keepbooking.favorite.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.favorite.dto.AddFavoriteRequest;
import com.keepbooking.favorite.service.FavoriteService;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Favorites", description = "User's favorite restaurants")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "Get my favorite restaurants")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RestaurantDto>> getMyFavorites(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(favoriteService.getMyFavorites(user.getId()));
    }

    @Operation(summary = "Add a restaurant to favorites (idempotent)")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> add(@AuthenticationPrincipal User user,
                                     @Valid @RequestBody AddFavoriteRequest request) {
        favoriteService.add(user.getId(), request.getRestaurantId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Remove a restaurant from favorites (idempotent)")
    @DeleteMapping("/{restaurantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal User user, @PathVariable Long restaurantId) {
        favoriteService.remove(user.getId(), restaurantId);
        return ResponseEntity.noContent().build();
    }
}
