package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.reference.repository.CityRepository;
import com.keepbooking.reference.repository.CuisineRepository;
import com.keepbooking.restaurant.dto.CreateRestaurantRequest;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CityRepository cityRepository;
    @Mock
    private CuisineRepository cuisineRepository;

    private RestaurantService restaurantService;

    private static final Long OWNER_ID = 1L;
    private static final Long COMPANY_ID = 5L;
    private static final Long RESTAURANT_ID = 10L;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository, companyRepository, cityRepository, cuisineRepository);
    }

    private Company company() {
        return Company.builder().id(COMPANY_ID).owner(User.builder().id(OWNER_ID).build()).name("Co").build();
    }

    private Restaurant restaurant() {
        return Restaurant.builder().id(RESTAURANT_ID).company(company()).name("Test Restaurant")
                .status(RestaurantStatus.PENDING_MODERATION).build();
    }

    private CreateRestaurantRequest createRequest() {
        CreateRestaurantRequest request = new CreateRestaurantRequest();
        request.setCompanyId(COMPANY_ID);
        request.setName("Test Restaurant");
        return request;
    }

    @Test
    void createThrowsWhenActorDoesNotOwnTheCompany() {
        when(companyRepository.findByIdAndOwnerId(COMPANY_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.create(OWNER_ID, createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    void createDefaultsTimezoneToUtcWhenNotProvided() {
        when(companyRepository.findByIdAndOwnerId(COMPANY_ID, OWNER_ID)).thenReturn(Optional.of(company()));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(RESTAURANT_ID);
            return r;
        });

        RestaurantDto dto = restaurantService.create(OWNER_ID, createRequest());

        assertThat(dto.getTimezone()).isEqualTo("UTC");
        assertThat(dto.getName()).isEqualTo("Test Restaurant");
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getById(RESTAURANT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void getByIdReturnsDtoWhenFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));

        RestaurantDto dto = restaurantService.getById(RESTAURANT_ID);

        assertThat(dto.getId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    void getMyRestaurantsAggregatesAcrossAllOwnedCompanies() {
        Company companyA = company();
        Company companyB = Company.builder().id(6L).owner(User.builder().id(OWNER_ID).build()).name("Co B").build();
        when(companyRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(companyA, companyB));
        when(restaurantRepository.findByCompanyId(companyA.getId())).thenReturn(List.of(restaurant()));
        when(restaurantRepository.findByCompanyId(companyB.getId())).thenReturn(List.of());

        List<RestaurantDto> result = restaurantService.getMyRestaurants(OWNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    void getByStatusReturnsPagedResultsFilteredByStatus() {
        when(restaurantRepository.findByStatus(RestaurantStatus.PENDING_MODERATION, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(restaurant())));

        var result = restaurantService.getByStatus(RestaurantStatus.PENDING_MODERATION, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(RestaurantDto::getId).containsExactly(RESTAURANT_ID);
    }

    @Test
    void moderateThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.moderate(RESTAURANT_ID, RestaurantStatus.ACTIVE))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void moderateUpdatesStatusToApproved() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        RestaurantDto dto = restaurantService.moderate(RESTAURANT_ID, RestaurantStatus.ACTIVE);

        assertThat(dto.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
    }

    @Test
    void moderateUpdatesStatusToBlocked() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        RestaurantDto dto = restaurantService.moderate(RESTAURANT_ID, RestaurantStatus.BLOCKED);

        assertThat(dto.getStatus()).isEqualTo(RestaurantStatus.BLOCKED);
    }
}
