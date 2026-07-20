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
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.common.security.AccessControlService;
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
import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.mapper.UserMapper;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.repository.UserRepository;

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
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    private RestaurantService restaurantService;

    private static final Long OWNER_ID = 1L;
    private static final Long COMPANY_ID = 5L;
    private static final Long RESTAURANT_ID = 10L;

    @BeforeEach
    void setUp() {
        restaurantService = new RestaurantService(restaurantRepository, companyRepository, cityRepository,
                cuisineRepository, userRepository, userMapper, new AccessControlService(userRepository));
    }

    private void stubCompanyAdmin() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(
                User.builder().id(OWNER_ID).role(UserRole.ROLE_COMPANY_ADMIN).companyId(COMPANY_ID).build()));
    }

    private Company company() {
        return Company.builder().id(COMPANY_ID).name("Co").build();
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
    void createThrowsAccessDeniedWhenActorDoesNotOwnTheCompany() {
        assertThatThrownBy(() -> restaurantService.create(999L, createRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createThrowsWhenCompanyNotFound() {
        stubCompanyAdmin();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.create(OWNER_ID, createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    void createDefaultsTimezoneToUtcWhenNotProvided() {
        stubCompanyAdmin();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
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
    void getMyRestaurantsReturnsEmptyWhenActorHasNoCompany() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(
                User.builder().id(OWNER_ID).role(UserRole.ROLE_USER).build()));

        List<RestaurantDto> result = restaurantService.getMyRestaurants(OWNER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getMyRestaurantsReturnsAllRestaurantsInTheCompanyForACompanyAdmin() {
        stubCompanyAdmin();
        when(restaurantRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(restaurant()));

        List<RestaurantDto> result = restaurantService.getMyRestaurants(OWNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    void getMyRestaurantsReturnsOnlyTheAssignedRestaurantForARestaurantAdmin() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(
                User.builder().id(OWNER_ID).role(UserRole.ROLE_RESTAURANT_ADMIN)
                        .companyId(COMPANY_ID).restaurantId(RESTAURANT_ID).build()));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));

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

    @Test
    void moderateWithReasonSetsRejectionReasonOnHidden() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        RestaurantDto dto = restaurantService.moderate(RESTAURANT_ID, RestaurantStatus.HIDDEN, "duplicate listing");

        assertThat(dto.getStatus()).isEqualTo(RestaurantStatus.HIDDEN);
        assertThat(dto.getRejectionReason()).isEqualTo("duplicate listing");
        assertThat(restaurant.getRejectionReason()).isEqualTo("duplicate listing");
    }

    @Test
    void moderateWithoutReasonClearsStaleRejectionReasonOnApprove() {
        Restaurant restaurant = restaurant();
        restaurant.setRejectionReason("incomplete details");
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        RestaurantDto dto = restaurantService.moderate(RESTAURANT_ID, RestaurantStatus.ACTIVE);

        assertThat(dto.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(dto.getRejectionReason()).isNull();
        assertThat(restaurant.getRejectionReason()).isNull();
    }

    @Test
    void updateThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.update(OWNER_ID, RESTAURANT_ID, new UpdateRestaurantRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void updateThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> restaurantService.update(999L, RESTAURANT_ID, new UpdateRestaurantRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateAppliesOnlyNonNullFields() {
        stubCompanyAdmin();
        Restaurant restaurant = restaurant();
        restaurant.setDescription("Old description");
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRestaurantRequest request = new UpdateRestaurantRequest();
        request.setName("Renamed Restaurant");

        RestaurantDto dto = restaurantService.update(OWNER_ID, RESTAURANT_ID, request);

        assertThat(dto.getName()).isEqualTo("Renamed Restaurant");
        assertThat(dto.getDescription()).isEqualTo("Old description");
    }

    @Test
    void assignAdminThrowsAccessDeniedWhenActorDoesNotManageTheCompany() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> restaurantService.assignAdmin(999L, RESTAURANT_ID, "target@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignAdminPromotesTargetUserToRestaurantAdmin() {
        stubCompanyAdmin();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));
        User target = User.builder().id(50L).email("target@test.com").role(UserRole.ROLE_USER).build();
        when(userRepository.findByEmailAndDeletedAtIsNull("target@test.com")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenReturn(UserProfileDto.builder().id(50L).build());

        restaurantService.assignAdmin(OWNER_ID, RESTAURANT_ID, "target@test.com");

        assertThat(target.getRole()).isEqualTo(UserRole.ROLE_RESTAURANT_ADMIN);
        assertThat(target.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(target.getRestaurantId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    void getAdminsThrowsAccessDeniedWhenActorDoesNotManageTheCompany() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> restaurantService.getAdmins(999L, RESTAURANT_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAdminsReturnsRestaurantAdminsForTheOwner() {
        stubCompanyAdmin();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));
        User admin = User.builder().id(50L).role(UserRole.ROLE_RESTAURANT_ADMIN)
                .companyId(COMPANY_ID).restaurantId(RESTAURANT_ID).build();
        when(userRepository.findByRestaurantIdAndRole(RESTAURANT_ID, UserRole.ROLE_RESTAURANT_ADMIN)).thenReturn(List.of(admin));
        when(userMapper.toDto(admin)).thenReturn(UserProfileDto.builder().id(50L).build());

        var result = restaurantService.getAdmins(OWNER_ID, RESTAURANT_ID);

        assertThat(result).extracting(UserProfileDto::getId).containsExactly(50L);
    }

    @Test
    void revokeAdminThrowsWhenTargetIsNotAnAdminOfThisRestaurant() {
        stubCompanyAdmin();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));
        User target = User.builder().id(50L).role(UserRole.ROLE_USER).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> restaurantService.revokeAdmin(OWNER_ID, RESTAURANT_ID, 50L))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void revokeAdminResetsTargetToPlainUser() {
        stubCompanyAdmin();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));
        User target = User.builder().id(50L).role(UserRole.ROLE_RESTAURANT_ADMIN)
                .companyId(COMPANY_ID).restaurantId(RESTAURANT_ID).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        restaurantService.revokeAdmin(OWNER_ID, RESTAURANT_ID, 50L);

        assertThat(target.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(target.getCompanyId()).isNull();
        assertThat(target.getRestaurantId()).isNull();
    }
}
