package com.keepbooking.restaurant.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
import com.keepbooking.restaurant.dto.UpdateRestaurantRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.RestaurantSpecifications;

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

    @Cacheable(value = "restaurantCards", key = "#restaurantId")
    @Transactional(readOnly = true)
    public RestaurantDto getById(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        return toDto(restaurant);
    }

    @Cacheable(value = "restaurantSearch",
            key = "'search:' + #cityId + ':' + #name + ':' + #cuisineSlug + ':' + #minRating "
                    + "+ ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    @Transactional(readOnly = true)
    public PageResponse<RestaurantDto> search(Long cityId, String name, String cuisineSlug,
                                               BigDecimal minRating, Pageable pageable) {
        Specification<Restaurant> spec = Specification
                .allOf(RestaurantSpecifications.isActive())
                .and(RestaurantSpecifications.hasCityId(cityId))
                .and(RestaurantSpecifications.nameContains(name))
                .and(RestaurantSpecifications.hasCuisineSlug(cuisineSlug))
                .and(RestaurantSpecifications.hasMinRating(minRating));

        Page<Restaurant> page = restaurantRepository.findAll(spec, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantDto> findNearby(double lat, double lng, double radiusKm, Pageable pageable) {
        Page<Restaurant> page = restaurantRepository.findNearby(lat, lng, radiusKm * 1000, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public List<RestaurantDto> getMyRestaurants(Long userId) {
        return companyRepository.findByOwnerId(userId).stream()
                .flatMap(c -> restaurantRepository.findByCompanyId(c.getId()).stream())
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantDto> getByStatus(RestaurantStatus status, Pageable pageable) {
        Page<Restaurant> page = restaurantRepository.findByStatus(status, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Caching(evict = {
            @CacheEvict(value = "restaurantCards", key = "#restaurantId"),
            @CacheEvict(value = "restaurantSearch", allEntries = true)
    })
    @Transactional
    public RestaurantDto update(Long userId, Long restaurantId, UpdateRestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }

        if (request.getName() != null) restaurant.setName(request.getName());
        if (request.getDescription() != null) restaurant.setDescription(request.getDescription());
        if (request.getAddress() != null) restaurant.setAddress(request.getAddress());
        if (request.getCityId() != null) {
            City city = cityRepository.findById(request.getCityId()).orElse(null);
            restaurant.setCity(city);
        }
        if (request.getLatitude() != null) restaurant.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) restaurant.setLongitude(request.getLongitude());
        if (request.getTimezone() != null) restaurant.setTimezone(request.getTimezone());
        if (request.getAvgCheck() != null) restaurant.setAvgCheck(request.getAvgCheck());
        if (request.getCuisineIds() != null) {
            restaurant.setCuisines(new HashSet<>(cuisineRepository.findAllById(request.getCuisineIds())));
        }

        return toDto(restaurantRepository.save(restaurant));
    }

    @Caching(evict = {
            @CacheEvict(value = "restaurantCards", key = "#restaurantId"),
            @CacheEvict(value = "restaurantSearch", allEntries = true)
    })
    @Transactional
    public RestaurantDto moderate(Long restaurantId, RestaurantStatus newStatus) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        restaurant.setStatus(newStatus);
        return toDto(restaurantRepository.save(restaurant));
    }

    /**
     * Invalidates the cached card and the whole search cache for a restaurant whose displayed
     * fields (e.g. rating/reviewsCount) changed via a different module - callers must invoke this
     * through the injected {@code RestaurantService} bean, not from within this class, since
     * {@code @CacheEvict} only fires on calls that go through the Spring proxy.
     */
    @Caching(evict = {
            @CacheEvict(value = "restaurantCards", key = "#restaurantId"),
            @CacheEvict(value = "restaurantSearch", allEntries = true)
    })
    public void evictCaches(Long restaurantId) {
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
