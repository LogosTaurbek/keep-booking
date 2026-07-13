package com.keepbooking.restaurant.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.restaurant.dto.UpsertWorkingHoursDayRequest;
import com.keepbooking.restaurant.dto.UpsertWorkingHoursOverrideRequest;
import com.keepbooking.restaurant.dto.WorkingHoursDto;
import com.keepbooking.restaurant.dto.WorkingHoursOverrideDto;
import com.keepbooking.restaurant.service.WorkingHoursService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Tag(name = "Working hours", description = "Restaurant weekly schedule and per-date overrides (holidays)")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/working-hours")
@RequiredArgsConstructor
@Validated
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;

    @Operation(summary = "Get restaurant weekly schedule (public)")
    @GetMapping
    public ResponseEntity<List<WorkingHoursDto>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(workingHoursService.listByRestaurant(restaurantId));
    }

    @Operation(summary = "Create or replace the schedule for a single day of the week")
    @PatchMapping("/{dayOfWeek}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WorkingHoursDto> upsertDay(@AuthenticationPrincipal User user,
                                                       @PathVariable Long restaurantId,
                                                       @PathVariable @Min(1) @Max(7) Integer dayOfWeek,
                                                       @Valid @RequestBody UpsertWorkingHoursDayRequest request) {
        return ResponseEntity.ok(workingHoursService.upsertDay(user.getId(), restaurantId, dayOfWeek, request));
    }

    @Operation(summary = "Get per-date schedule overrides (holidays / special days, public)")
    @GetMapping("/overrides")
    public ResponseEntity<List<WorkingHoursOverrideDto>> listOverrides(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(workingHoursService.listOverrides(restaurantId));
    }

    @Operation(summary = "Create or replace the schedule override for a specific date")
    @PutMapping("/overrides")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WorkingHoursOverrideDto> upsertOverride(@AuthenticationPrincipal User user,
                                                                    @PathVariable Long restaurantId,
                                                                    @Valid @RequestBody UpsertWorkingHoursOverrideRequest request) {
        return ResponseEntity.ok(workingHoursService.upsertOverride(user.getId(), restaurantId, request));
    }

    @Operation(summary = "Remove the schedule override for a specific date, reverting to the weekly schedule")
    @DeleteMapping("/overrides/{date}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteOverride(@AuthenticationPrincipal User user,
                                                @PathVariable Long restaurantId,
                                                @PathVariable LocalDate date) {
        workingHoursService.deleteOverride(user.getId(), restaurantId, date);
        return ResponseEntity.noContent().build();
    }
}
