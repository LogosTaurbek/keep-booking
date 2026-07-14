package com.keepbooking.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.repository.UserRepository;

/**
 * The one place that exercises the full SUPER_ADMIN / COMPANY_ADMIN / RESTAURANT_ADMIN scope
 * matrix (tz2.txt role redesign) - individual services only need a thin delegation test each,
 * since the actual authorization logic lives here.
 */
@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private UserRepository userRepository;

    private AccessControlService accessControlService;

    private static final Long COMPANY_ID = 10L;
    private static final Long OTHER_COMPANY_ID = 99L;
    private static final Long RESTAURANT_ID = 20L;
    private static final Long OTHER_RESTAURANT_ID = 21L;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlService(userRepository);
    }

    private Restaurant restaurantInCompany(Long restaurantId, Long companyId) {
        Company company = Company.builder().id(companyId).name("Co").build();
        return Restaurant.builder().id(restaurantId).company(company).name("R").build();
    }

    private User actor(Long id, UserRole role, Long companyId, Long restaurantId) {
        return User.builder().id(id).role(role).companyId(companyId).restaurantId(restaurantId).build();
    }

    @Test
    void superAdminCanManageAnyCompany() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor(1L, UserRole.ROLE_SUPER_ADMIN, null, null)));

        assertThat(accessControlService.canManageCompany(1L, COMPANY_ID)).isTrue();
        assertThat(accessControlService.canManageCompany(1L, OTHER_COMPANY_ID)).isTrue();
    }

    @Test
    void superAdminCanManageAnyRestaurant() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor(1L, UserRole.ROLE_SUPER_ADMIN, null, null)));

        assertThat(accessControlService.canManageRestaurant(1L, restaurantInCompany(RESTAURANT_ID, COMPANY_ID))).isTrue();
    }

    @Test
    void companyAdminCanManageOwnCompanyButNotAnother() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(actor(2L, UserRole.ROLE_COMPANY_ADMIN, COMPANY_ID, null)));

        assertThat(accessControlService.canManageCompany(2L, COMPANY_ID)).isTrue();
        assertThat(accessControlService.canManageCompany(2L, OTHER_COMPANY_ID)).isFalse();
    }

    @Test
    void companyAdminCanManageAnyRestaurantInOwnCompany() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(actor(2L, UserRole.ROLE_COMPANY_ADMIN, COMPANY_ID, null)));

        assertThat(accessControlService.canManageRestaurant(2L, restaurantInCompany(RESTAURANT_ID, COMPANY_ID))).isTrue();
        assertThat(accessControlService.canManageRestaurant(2L, restaurantInCompany(OTHER_RESTAURANT_ID, COMPANY_ID))).isTrue();
    }

    @Test
    void companyAdminCannotManageRestaurantInAnotherCompany() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(actor(2L, UserRole.ROLE_COMPANY_ADMIN, COMPANY_ID, null)));

        assertThat(accessControlService.canManageRestaurant(2L, restaurantInCompany(RESTAURANT_ID, OTHER_COMPANY_ID))).isFalse();
    }

    @Test
    void companyAdminCannotManageOtherCompaniesEvenViaRestaurantId() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(accessControlService.canManageCompany(2L, COMPANY_ID)).isFalse();
    }

    @Test
    void restaurantAdminCanManageOnlyTheirOwnRestaurant() {
        when(userRepository.findById(3L))
                .thenReturn(Optional.of(actor(3L, UserRole.ROLE_RESTAURANT_ADMIN, COMPANY_ID, RESTAURANT_ID)));

        assertThat(accessControlService.canManageRestaurant(3L, restaurantInCompany(RESTAURANT_ID, COMPANY_ID))).isTrue();
        assertThat(accessControlService.canManageRestaurant(3L, restaurantInCompany(OTHER_RESTAURANT_ID, COMPANY_ID))).isFalse();
    }

    @Test
    void restaurantAdminCannotManageTheCompanyItself() {
        when(userRepository.findById(3L))
                .thenReturn(Optional.of(actor(3L, UserRole.ROLE_RESTAURANT_ADMIN, COMPANY_ID, RESTAURANT_ID)));

        assertThat(accessControlService.canManageCompany(3L, COMPANY_ID)).isFalse();
    }

    @Test
    void plainUserCanManageNeitherCompanyNorRestaurant() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(actor(4L, UserRole.ROLE_USER, null, null)));

        assertThat(accessControlService.canManageCompany(4L, COMPANY_ID)).isFalse();
        assertThat(accessControlService.canManageRestaurant(4L, restaurantInCompany(RESTAURANT_ID, COMPANY_ID))).isFalse();
    }

    @Test
    void unknownActorCanManageNothing() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(accessControlService.canManageCompany(999L, COMPANY_ID)).isFalse();
        assertThat(accessControlService.canManageRestaurant(999L, restaurantInCompany(RESTAURANT_ID, COMPANY_ID))).isFalse();
    }

    @Test
    void verifyCanManageCompanyThrowsWhenNotAllowed() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(actor(4L, UserRole.ROLE_USER, null, null)));

        assertThatThrownBy(() -> accessControlService.verifyCanManageCompany(4L, COMPANY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifyCanManageRestaurantThrowsWhenNotAllowed() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(actor(4L, UserRole.ROLE_USER, null, null)));

        assertThatThrownBy(() -> accessControlService.verifyCanManageRestaurant(4L, restaurantInCompany(RESTAURANT_ID, COMPANY_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
