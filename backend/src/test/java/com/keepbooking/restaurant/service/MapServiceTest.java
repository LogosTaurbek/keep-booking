package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.keepbooking.restaurant.dto.MapRestaurantDto;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.repository.RestaurantRepository;

/**
 * Unit tests for the map pins endpoint (tz2.txt §12): bbox/radius restaurant lookup
 * plus the "free tables now" flag. Availability computation itself is covered in
 * {@link AvailabilityServiceTest#hasFreeTablesNowReturnsTrueWhenAtLeastOneCandidateIsFree()}
 * and friends; here it's mocked to isolate mapping/wiring.
 */
@ExtendWith(MockitoExtension.class)
class MapServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private AvailabilityService availabilityService;

    private MapService mapService;

    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        mapService = new MapService(restaurantRepository, availabilityService);
    }

    private Restaurant restaurant() {
        return Restaurant.builder()
                .id(RESTAURANT_ID)
                .name("Test Restaurant")
                .status(RestaurantStatus.ACTIVE)
                .latitude(BigDecimal.valueOf(51.5))
                .longitude(BigDecimal.valueOf(-0.1))
                .rating(BigDecimal.valueOf(4.5))
                .build();
    }

    @Test
    void boundingBoxSearchMapsRestaurantsAndFreeNowFlag() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findInBoundingBox(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(restaurant)));
        when(availabilityService.hasFreeTablesNow(restaurant, 2)).thenReturn(true);

        List<MapRestaurantDto> result = mapService.getRestaurantsInBoundingBox(
                BigDecimal.valueOf(51.0), BigDecimal.valueOf(52.0),
                BigDecimal.valueOf(-1.0), BigDecimal.valueOf(1.0), 2, 200);

        assertThat(result).hasSize(1);
        MapRestaurantDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(RESTAURANT_ID);
        assertThat(dto.getLatitude()).isEqualTo(restaurant.getLatitude());
        assertThat(dto.getLongitude()).isEqualTo(restaurant.getLongitude());
        assertThat(dto.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(dto.getRating()).isEqualTo(restaurant.getRating());
        assertThat(dto.isHasFreeTablesNow()).isTrue();
    }

    @Test
    void radiusSearchMapsRestaurantsAndFreeNowFlag() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of(restaurant)));
        when(availabilityService.hasFreeTablesNow(restaurant, 4)).thenReturn(false);

        List<MapRestaurantDto> result = mapService.getRestaurantsNearby(51.5, -0.1, 5, 4, 200);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isHasFreeTablesNow()).isFalse();
    }

    @Test
    void radiusSearchConvertsKilometersToMeters() {
        when(restaurantRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mapService.getRestaurantsNearby(51.5, -0.1, 5, 2, 200);

        org.mockito.Mockito.verify(restaurantRepository).findNearby(
                org.mockito.ArgumentMatchers.eq(51.5), org.mockito.ArgumentMatchers.eq(-0.1),
                org.mockito.ArgumentMatchers.eq(5000.0), any());
    }
}
