package com.keepbooking.analytics.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.analytics.dto.RestaurantAnalyticsDto;
import com.keepbooking.analytics.service.AnalyticsService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Analytics", description = "Per-restaurant booking analytics (owner only)")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('RESTAURANT_ADMIN', 'COMPANY_ADMIN', 'SUPER_ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Bookings, cancellations, popular hours/tables and unique guests for a date range")
    @GetMapping
    public ResponseEntity<RestaurantAnalyticsDto> getAnalytics(
            @AuthenticationPrincipal User user,
            @PathVariable Long restaurantId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(analyticsService.getRestaurantAnalytics(user.getId(), restaurantId, from, to));
    }
}
