package com.keepbooking.admin.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.model.User;
import com.keepbooking.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin: users", description = "Super admin — user management")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    @Operation(summary = "List all users")
    @GetMapping
    public ResponseEntity<PageResponse<UserProfileDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @Operation(summary = "Block a user")
    @PatchMapping("/{id}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        userService.setBlocked(id, true);
        auditLogService.record(admin.getId(), "USER_BLOCKED", "User", id, null);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unblock a user")
    @PatchMapping("/{id}/unblock")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        userService.setBlocked(id, false);
        auditLogService.record(admin.getId(), "USER_UNBLOCKED", "User", id, null);
        return ResponseEntity.noContent().build();
    }
}
