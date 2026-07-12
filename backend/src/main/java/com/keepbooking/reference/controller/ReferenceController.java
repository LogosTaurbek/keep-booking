package com.keepbooking.reference.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.reference.dto.CityDto;
import com.keepbooking.reference.dto.CountryDto;
import com.keepbooking.reference.dto.CuisineDto;
import com.keepbooking.reference.service.ReferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reference", description = "Countries, cities, cuisines (public, cached)")
@RestController
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceService referenceService;

    @Operation(summary = "Get all countries")
    @GetMapping("/api/v1/countries")
    public ResponseEntity<List<CountryDto>> getCountries() {
        return ResponseEntity.ok(referenceService.getCountries());
    }

    @Operation(summary = "Get cities by country")
    @GetMapping("/api/v1/countries/{countryId}/cities")
    public ResponseEntity<List<CityDto>> getCitiesByCountry(@PathVariable Long countryId) {
        return ResponseEntity.ok(referenceService.getCitiesByCountry(countryId));
    }

    @Operation(summary = "Get all cities")
    @GetMapping("/api/v1/cities")
    public ResponseEntity<List<CityDto>> getAllCities() {
        return ResponseEntity.ok(referenceService.getAllCities());
    }

    @Operation(summary = "Get all cuisines")
    @GetMapping("/api/v1/cuisines")
    public ResponseEntity<List<CuisineDto>> getCuisines() {
        return ResponseEntity.ok(referenceService.getCuisines());
    }
}
