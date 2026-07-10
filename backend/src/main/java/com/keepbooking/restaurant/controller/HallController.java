package com.keepbooking.restaurant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.restaurant.dto.CreateHallRequest;
import com.keepbooking.restaurant.dto.HallDto;
import com.keepbooking.restaurant.dto.UpdateHallRequest;
import com.keepbooking.restaurant.service.HallService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Halls", description = "Restaurant hall management")
@RestController
@RequestMapping("/api/v1/halls")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;

    @Operation(summary = "List halls for a restaurant (public)")
    @GetMapping
    public ResponseEntity<List<HallDto>> list(@RequestParam Long restaurantId) {
        return ResponseEntity.ok(hallService.listByRestaurant(restaurantId));
    }

    @Operation(summary = "Get hall by ID (public)")
    @GetMapping("/{id}")
    public ResponseEntity<HallDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(hallService.getById(id));
    }

    @Operation(summary = "Create a hall")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<HallDto> create(@AuthenticationPrincipal User user,
                                           @Valid @RequestBody CreateHallRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hallService.create(user.getId(), request));
    }

    @Operation(summary = "Update a hall")
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<HallDto> update(@AuthenticationPrincipal User user,
                                           @PathVariable Long id,
                                           @Valid @RequestBody UpdateHallRequest request) {
        return ResponseEntity.ok(hallService.update(user.getId(), id, request));
    }

    @Operation(summary = "Delete a hall")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        hallService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
