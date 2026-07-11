package com.keepbooking.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.admin.dto.SystemStatsDto;
import com.keepbooking.admin.service.AdminStatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin: stats", description = "Super admin — system-wide statistics")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @Operation(summary = "System-wide statistics (users, companies, restaurants, bookings)")
    @GetMapping
    public ResponseEntity<SystemStatsDto> getStats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }
}
