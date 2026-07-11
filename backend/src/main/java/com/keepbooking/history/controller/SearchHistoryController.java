package com.keepbooking.history.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.history.dto.SearchHistoryDto;
import com.keepbooking.history.service.SearchHistoryService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Search history", description = "Recent restaurant searches")
@RestController
@RequestMapping("/api/v1/search-history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @Operation(summary = "Get my recent searches")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<SearchHistoryDto>> getMyHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(searchHistoryService.getMyHistory(user.getId(), pageable));
    }
}
