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
import com.keepbooking.restaurant.dto.CompanyDto;
import com.keepbooking.restaurant.service.CompanyService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin: companies", description = "Super admin — company management")
@RestController
@RequestMapping("/api/v1/admin/companies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCompanyController {

    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    @Operation(summary = "List all companies")
    @GetMapping
    public ResponseEntity<PageResponse<CompanyDto>> getAllCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(companyService.getAllCompanies(pageable));
    }

    @Operation(summary = "Block a company")
    @PatchMapping("/{id}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        companyService.setBlocked(id, true);
        auditLogService.record(admin.getId(), "COMPANY_BLOCKED", "Company", id, null);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unblock a company")
    @PatchMapping("/{id}/unblock")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        companyService.setBlocked(id, false);
        auditLogService.record(admin.getId(), "COMPANY_UNBLOCKED", "Company", id, null);
        return ResponseEntity.noContent().build();
    }
}
