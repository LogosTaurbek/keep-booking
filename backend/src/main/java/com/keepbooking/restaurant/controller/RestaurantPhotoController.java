package com.keepbooking.restaurant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.keepbooking.restaurant.dto.RestaurantPhotoDto;
import com.keepbooking.restaurant.service.RestaurantPhotoService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Restaurant photos", description = "Restaurant photo gallery (S3/MinIO-backed)")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/photos")
@RequiredArgsConstructor
public class RestaurantPhotoController {

    private final RestaurantPhotoService restaurantPhotoService;

    @Operation(summary = "List restaurant photos (public)")
    @GetMapping
    public ResponseEntity<List<RestaurantPhotoDto>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(restaurantPhotoService.listByRestaurant(restaurantId));
    }

    @Operation(summary = "Upload a restaurant photo (JPEG/PNG/WebP, max 5MB)")
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<RestaurantPhotoDto> upload(@AuthenticationPrincipal User user,
                                                       @PathVariable Long restaurantId,
                                                       @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantPhotoService.upload(user.getId(), restaurantId, file));
    }

    @Operation(summary = "Delete a restaurant photo")
    @DeleteMapping("/{photoId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user,
                                        @PathVariable Long restaurantId,
                                        @PathVariable Long photoId) {
        restaurantPhotoService.delete(user.getId(), restaurantId, photoId);
        return ResponseEntity.noContent().build();
    }
}
