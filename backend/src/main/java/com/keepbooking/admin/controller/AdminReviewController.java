package com.keepbooking.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.review.service.ReviewService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin: reviews", description = "Super admin — review moderation")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final AuditLogService auditLogService;

    @Operation(summary = "Delete a review (recalculates the restaurant's rating)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        reviewService.delete(id);
        auditLogService.record(admin.getId(), "REVIEW_DELETED", "Review", id, null);
        return ResponseEntity.noContent().build();
    }
}
