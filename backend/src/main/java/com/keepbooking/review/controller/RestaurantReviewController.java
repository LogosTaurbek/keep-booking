package com.keepbooking.review.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.review.dto.ReviewDto;
import com.keepbooking.review.service.ReviewService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reviews", description = "Restaurant reviews (only after a COMPLETED booking)")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/reviews")
@RequiredArgsConstructor
public class RestaurantReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get restaurant reviews (public)")
    @GetMapping
    public ResponseEntity<PageResponse<ReviewDto>> list(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(reviewService.getByRestaurant(restaurantId, pageable));
    }

    @Operation(summary = "Get restaurant reviews for management (owner only)")
    @GetMapping("/manage")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PageResponse<ReviewDto>> listForOwner(
            @AuthenticationPrincipal User user,
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(reviewService.getByRestaurantForOwner(user.getId(), restaurantId, pageable));
    }
}
