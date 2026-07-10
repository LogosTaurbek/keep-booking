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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.restaurant.dto.BatchUpdateTablesRequest;
import com.keepbooking.restaurant.dto.CreateTableRequest;
import com.keepbooking.restaurant.dto.TableDto;
import com.keepbooking.restaurant.dto.UpdateTableRequest;
import com.keepbooking.restaurant.service.TableService;
import com.keepbooking.user.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Tables", description = "Restaurant table management")
@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @Operation(summary = "List tables for a hall (public)")
    @GetMapping
    public ResponseEntity<List<TableDto>> list(@RequestParam Long hallId) {
        return ResponseEntity.ok(tableService.listByHall(hallId));
    }

    @Operation(summary = "Get table by ID (public)")
    @GetMapping("/{id}")
    public ResponseEntity<TableDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tableService.getById(id));
    }

    @Operation(summary = "Create a table")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TableDto> create(@AuthenticationPrincipal User user,
                                            @Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.create(user.getId(), request));
    }

    @Operation(summary = "Update a table")
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TableDto> update(@AuthenticationPrincipal User user,
                                            @PathVariable Long id,
                                            @Valid @RequestBody UpdateTableRequest request) {
        return ResponseEntity.ok(tableService.update(user.getId(), id, request));
    }

    @Operation(summary = "Batch-update table positions (hall layout editor)")
    @PutMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<TableDto>> batchUpdate(@AuthenticationPrincipal User user,
                                                        @Valid @RequestBody BatchUpdateTablesRequest request) {
        return ResponseEntity.ok(tableService.batchUpdatePositions(user.getId(), request));
    }

    @Operation(summary = "Delete a table")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        tableService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
