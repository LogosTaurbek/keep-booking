package com.keepbooking.restaurant.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.restaurant.dto.TableDto;
import com.keepbooking.restaurant.service.AvailabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Availability", description = "Free table lookup for a restaurant")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @Operation(summary = "List available tables for a date/time slot (public)")
    @GetMapping
    public ResponseEntity<List<TableDto>> getAvailability(@PathVariable Long restaurantId,
                                                            @RequestParam LocalDate date,
                                                            @RequestParam LocalTime from,
                                                            @RequestParam LocalTime to,
                                                            @RequestParam Integer guests) {
        return ResponseEntity.ok(availabilityService.getAvailableTables(restaurantId, date, from, to, guests));
    }
}
