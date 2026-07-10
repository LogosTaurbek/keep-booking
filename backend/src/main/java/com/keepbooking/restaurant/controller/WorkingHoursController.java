package com.keepbooking.restaurant.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.restaurant.dto.WorkingHoursDto;
import com.keepbooking.restaurant.dto.WorkingHoursItemRequest;
import com.keepbooking.restaurant.service.WorkingHoursService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Working hours", description = "Restaurant weekly schedule")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/working-hours")
@RequiredArgsConstructor
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;

    @Operation(summary = "Get restaurant weekly schedule (public)")
    @GetMapping
    public ResponseEntity<List<WorkingHoursDto>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(workingHoursService.listByRestaurant(restaurantId));
    }

    @Operation(summary = "Replace restaurant weekly schedule")
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<WorkingHoursDto>> replace(@AuthenticationPrincipal User user,
                                                           @PathVariable Long restaurantId,
                                                           @Valid @RequestBody List<@Valid WorkingHoursItemRequest> items) {
        return ResponseEntity.ok(workingHoursService.replaceWeek(user.getId(), restaurantId, items));
    }
}
