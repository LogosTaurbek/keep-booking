package com.keepbooking.restaurant.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.reference.model.City;
import com.keepbooking.reference.model.Cuisine;
import com.keepbooking.reference.repository.CityRepository;
import com.keepbooking.reference.repository.CuisineRepository;
import com.keepbooking.restaurant.dto.CreateRestaurantRequest;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CompanyRepository companyRepository;
    private final CityRepository cityRepository;
    private final CuisineRepository cuisineRepository;

    @Transactional
    public RestaurantDto create(Long userId, CreateRestaurantRequest request) {
        Company company = companyRepository.findByIdAndOwnerId(request.getCompanyId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_NOT_FOUND));

        City city = request.getCityId() != null
                ? cityRepository.findById(request.getCityId()).orElse(null)
                : null;

        Set<Cuisine> cuisines = new HashSet<>();
        if (request.getCuisineIds() != null) {
            cuisines.addAll(cuisineRepository.findAllById(request.getCuisineIds()));
        }

        Restaurant restaurant = Restaurant.builder()
                .company(company)
                .name(request.getName())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(city)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                .avgCheck(request.getAvgCheck())
                .cuisines(cuisines)
                .build();

        return toDto(restaurantRepository.save(restaurant));
    }

    @Transactional(readOnly = true)
    public RestaurantDto getById(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        return toDto(restaurant);
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantDto> listActive(Long cityId, Pageable pageable) {
        Page<Restaurant> page = cityId != null
                ? restaurantRepository.findByCityIdAndStatus(cityId, RestaurantStatus.ACTIVE, pageable)
                : restaurantRepository.findByStatus(RestaurantStatus.ACTIVE, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public List<RestaurantDto> getMyRestaurants(Long userId) {
        return companyRepository.findByOwnerId(userId).stream()
                .flatMap(c -> restaurantRepository.findByCompanyId(c.getId()).stream())
                .map(this::toDto)
                .toList();
    }

    private RestaurantDto toDto(Restaurant r) {
        return RestaurantDto.builder()
                .id(r.getId())
                .companyId(r.getCompany().getId())
                .name(r.getName())
                .description(r.getDescription())
                .address(r.getAddress())
                .cityId(r.getCity() != null ? r.getCity().getId() : null)
                .cityName(r.getCity() != null ? r.getCity().getName() : null)
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .timezone(r.getTimezone())
                .rating(r.getRating())
                .reviewsCount(r.getReviewsCount())
                .avgCheck(r.getAvgCheck())
                .status(r.getStatus())
                .cuisineSlugs(r.getCuisines().stream().map(Cuisine::getSlug).collect(Collectors.toSet()))
                .build();
    }
}
