package com.keepbooking.restaurant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.restaurant.dto.CompanyDto;
import com.keepbooking.restaurant.dto.CreateCompanyRequest;
import com.keepbooking.restaurant.service.CompanyService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Companies", description = "Restaurant company management")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    @Operation(summary = "Register a new company")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CompanyDto> create(@AuthenticationPrincipal User user,
                                              @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(user.getId(), request));
    }

    @Operation(summary = "Get my companies")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CompanyDto>> getMyCompanies(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(companyService.getMyCompanies(user.getId()));
    }
}
