package com.keepbooking.waitlist.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.user.model.User;
import com.keepbooking.waitlist.dto.CreateWaitlistEntryRequest;
import com.keepbooking.waitlist.dto.WaitlistEntryDto;
import com.keepbooking.waitlist.service.WaitlistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Waitlist", description = "Join a waitlist for a restaurant/date/time slot and get notified when a table opens up")
@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "Join the waitlist for a restaurant/date/time slot (idempotent)")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WaitlistEntryDto> join(@AuthenticationPrincipal User user,
                                                  @Valid @RequestBody CreateWaitlistEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.join(user.getId(), request));
    }

    @Operation(summary = "Leave the waitlist")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal User user, @PathVariable Long id) {
        waitlistService.leave(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "My waitlist entries")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<WaitlistEntryDto>> getMyWaitlist(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(waitlistService.getMyWaitlist(user.getId(), pageable));
    }
}
