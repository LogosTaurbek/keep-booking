package com.keepbooking.reference.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.reference.dto.CityDto;
import com.keepbooking.reference.dto.CountryDto;
import com.keepbooking.reference.dto.CuisineDto;
import com.keepbooking.reference.repository.CityRepository;
import com.keepbooking.reference.repository.CountryRepository;
import com.keepbooking.reference.repository.CuisineRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReferenceService {

    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final CuisineRepository cuisineRepository;

    @Cacheable("countries")
    @Transactional(readOnly = true)
    public List<CountryDto> getCountries() {
        return countryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CountryDto.builder().id(c.getId()).name(c.getName()).code(c.getCode()).build())
                .toList();
    }

    @Cacheable(value = "cities", key = "#countryId")
    @Transactional(readOnly = true)
    public List<CityDto> getCitiesByCountry(Long countryId) {
        return cityRepository.findByCountryIdOrderByNameAsc(countryId).stream()
                .map(c -> CityDto.builder()
                        .id(c.getId())
                        .countryId(countryId)
                        .name(c.getName())
                        .latitude(c.getLatitude())
                        .longitude(c.getLongitude())
                        .timezone(c.getTimezone())
                        .build())
                .toList();
    }

    @Cacheable(value = "cities", key = "'all'")
    @Transactional(readOnly = true)
    public List<CityDto> getAllCities() {
        return cityRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CityDto.builder()
                        .id(c.getId())
                        .countryId(c.getCountry() != null ? c.getCountry().getId() : null)
                        .name(c.getName())
                        .latitude(c.getLatitude())
                        .longitude(c.getLongitude())
                        .timezone(c.getTimezone())
                        .build())
                .toList();
    }

    @Cacheable("cuisines")
    @Transactional(readOnly = true)
    public List<CuisineDto> getCuisines() {
        return cuisineRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CuisineDto.builder().id(c.getId()).name(c.getName()).slug(c.getSlug()).build())
                .toList();
    }
}
