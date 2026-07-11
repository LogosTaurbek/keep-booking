package com.keepbooking.admin.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.admin.dto.RejectRestaurantRequest;
import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.service.RestaurantService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin: restaurants", description = "Super admin — restaurant moderation")
@RestController
@RequestMapping("/api/v1/admin/restaurants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRestaurantController {

    private final RestaurantService restaurantService;
    private final AuditLogService auditLogService;

    @Operation(summary = "List restaurants by status (e.g. PENDING_MODERATION)")
    @GetMapping
    public ResponseEntity<PageResponse<RestaurantDto>> getByStatus(
            @RequestParam RestaurantStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(restaurantService.getByStatus(status, pageable));
    }

    @Operation(summary = "Approve a restaurant (-> ACTIVE)")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<RestaurantDto> approve(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        RestaurantDto dto = restaurantService.moderate(id, RestaurantStatus.ACTIVE);
        auditLogService.record(admin.getId(), "RESTAURANT_APPROVED", "Restaurant", id, null);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Reject a restaurant (-> HIDDEN, with reason)")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<RestaurantDto> reject(@AuthenticationPrincipal User admin, @PathVariable Long id,
                                                 @Valid @RequestBody RejectRestaurantRequest request) {
        RestaurantDto dto = restaurantService.moderate(id, RestaurantStatus.HIDDEN);
        auditLogService.record(admin.getId(), "RESTAURANT_REJECTED", "Restaurant", id, request.getReason());
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Block a restaurant")
    @PatchMapping("/{id}/block")
    public ResponseEntity<RestaurantDto> block(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        RestaurantDto dto = restaurantService.moderate(id, RestaurantStatus.BLOCKED);
        auditLogService.record(admin.getId(), "RESTAURANT_BLOCKED", "Restaurant", id, null);
        return ResponseEntity.ok(dto);
    }
}
