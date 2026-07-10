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

import com.keepbooking.restaurant.dto.CreateMenuItemRequest;
import com.keepbooking.restaurant.dto.MenuItemDto;
import com.keepbooking.restaurant.dto.UpdateMenuItemRequest;
import com.keepbooking.restaurant.service.MenuItemService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Menu", description = "Restaurant menu management")
@RestController
@RequestMapping("/api/v1/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @Operation(summary = "List menu items for a restaurant (public)")
    @GetMapping
    public ResponseEntity<List<MenuItemDto>> list(@RequestParam Long restaurantId) {
        return ResponseEntity.ok(menuItemService.listByRestaurant(restaurantId));
    }

    @Operation(summary = "Get menu item by ID (public)")
    @GetMapping("/{id}")
    public ResponseEntity<MenuItemDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.getById(id));
    }

    @Operation(summary = "Create a menu item")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MenuItemDto> create(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.create(user.getId(), request));
    }

    @Operation(summary = "Update a menu item")
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MenuItemDto> update(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @Valid @RequestBody UpdateMenuItemRequest request) {
        return ResponseEntity.ok(menuItemService.update(user.getId(), id, request));
    }

    @Operation(summary = "Delete a menu item")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        menuItemService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
