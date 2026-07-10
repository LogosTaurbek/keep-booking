package com.keepbooking.reference.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.keepbooking.reference.dto.CityDto;
import com.keepbooking.reference.dto.CountryDto;
import com.keepbooking.reference.dto.CuisineDto;
import com.keepbooking.reference.repository.CityRepository;
import com.keepbooking.reference.repository.CountryRepository;
import com.keepbooking.reference.repository.CuisineRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reference", description = "Countries, cities, cuisines (public, cached)")
@RestController
@RequiredArgsConstructor
public class ReferenceController {

    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final CuisineRepository cuisineRepository;

    @Operation(summary = "Get all countries")
    @GetMapping("/api/v1/countries")
    public ResponseEntity<List<CountryDto>> getCountries() {
        List<CountryDto> result = countryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CountryDto.builder().id(c.getId()).name(c.getName()).code(c.getCode()).build())
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get cities by country")
    @GetMapping("/api/v1/countries/{countryId}/cities")
    public ResponseEntity<List<CityDto>> getCitiesByCountry(@PathVariable Long countryId) {
        List<CityDto> result = cityRepository.findByCountryIdOrderByNameAsc(countryId).stream()
                .map(c -> CityDto.builder()
                        .id(c.getId())
                        .countryId(countryId)
                        .name(c.getName())
                        .latitude(c.getLatitude())
                        .longitude(c.getLongitude())
                        .timezone(c.getTimezone())
                        .build())
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all cities")
    @GetMapping("/api/v1/cities")
    public ResponseEntity<List<CityDto>> getAllCities() {
        List<CityDto> result = cityRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CityDto.builder()
                        .id(c.getId())
                        .countryId(c.getCountry() != null ? c.getCountry().getId() : null)
                        .name(c.getName())
                        .latitude(c.getLatitude())
                        .longitude(c.getLongitude())
                        .timezone(c.getTimezone())
                        .build())
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all cuisines")
    @GetMapping("/api/v1/cuisines")
    public ResponseEntity<List<CuisineDto>> getCuisines() {
        List<CuisineDto> result = cuisineRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CuisineDto.builder().id(c.getId()).name(c.getName()).slug(c.getSlug()).build())
                .toList();
        return ResponseEntity.ok(result);
    }
}
