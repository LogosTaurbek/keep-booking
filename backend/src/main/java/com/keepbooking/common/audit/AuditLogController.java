package com.keepbooking.common.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Audit log", description = "Admin-only audit trail")
@RestController
@RequestMapping("/api/v1/audit-log")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Get audit log entries for an entity (super admin only)")
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<AuditLogDto>> getForEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        var result = auditLogService.getForEntity(entityType, entityId, pageable)
                .map(AuditLogDto::from);
        return ResponseEntity.ok(PageResponse.of(result));
    }
}
