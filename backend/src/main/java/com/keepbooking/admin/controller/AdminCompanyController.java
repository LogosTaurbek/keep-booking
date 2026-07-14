package com.keepbooking.admin.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.restaurant.dto.CompanyDto;
import com.keepbooking.restaurant.dto.CreateCompanyRequest;
import com.keepbooking.restaurant.service.CompanyService;
import com.keepbooking.user.dto.AssignAdminRequest;
import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "Create a company on behalf of a client (support-assisted onboarding)")
    @PostMapping
    public ResponseEntity<CompanyDto> create(@AuthenticationPrincipal User admin,
                                              @Valid @RequestBody CreateCompanyRequest request) {
        CompanyDto company = companyService.createOnBehalf(request);
        auditLogService.record(admin.getId(), "COMPANY_CREATED_BY_ADMIN", "Company", company.getId(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(company);
    }

    @Operation(summary = "List this company's admins")
    @GetMapping("/{id}/admins")
    public ResponseEntity<List<UserProfileDto>> getAdmins(@AuthenticationPrincipal User admin,
                                                            @PathVariable Long id) {
        return ResponseEntity.ok(companyService.getAdmins(admin.getId(), id));
    }

    @Operation(summary = "Attach an already-registered user as this company's admin")
    @PostMapping("/{id}/admins")
    public ResponseEntity<UserProfileDto> assignAdmin(@AuthenticationPrincipal User admin,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody AssignAdminRequest request) {
        UserProfileDto target = companyService.assignAdmin(admin.getId(), id, request.getEmail());
        auditLogService.record(admin.getId(), "COMPANY_ADMIN_ASSIGNED", "Company", id, request.getEmail());
        return ResponseEntity.ok(target);
    }

    @Operation(summary = "Revoke a company admin")
    @DeleteMapping("/{id}/admins/{userId}")
    public ResponseEntity<Void> revokeAdmin(@AuthenticationPrincipal User admin,
                                             @PathVariable Long id,
                                             @PathVariable Long userId) {
        companyService.revokeAdmin(admin.getId(), id, userId);
        auditLogService.record(admin.getId(), "COMPANY_ADMIN_REVOKED", "Company", id, userId.toString());
        return ResponseEntity.noContent().build();
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
