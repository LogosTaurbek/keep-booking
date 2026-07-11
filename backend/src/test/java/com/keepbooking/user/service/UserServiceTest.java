package com.keepbooking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.reference.model.City;
import com.keepbooking.reference.repository.CityRepository;
import com.keepbooking.user.dto.UpdateProfileRequest;
import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.mapper.UserMapperImpl;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserStatus;
import com.keepbooking.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CityRepository cityRepository;

    private UserService userService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, cityRepository, new UserMapperImpl());
    }

    private User activeUser() {
        return User.builder().id(USER_ID).email("user@test.com").firstname("Old").lastname("Name")
                .status(UserStatus.ACTIVE).build();
    }

    @Test
    void getProfileThrowsWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getProfileThrowsWhenUserWasSoftDeleted() {
        User deleted = activeUser();
        deleted.setDeletedAt(Instant.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> userService.getProfile(USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateProfileOnlyChangesProvidedFieldsLeavingOthersUntouched() {
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstname("New");
        // lastname/phone/language/timezone/cityId intentionally left null -> should stay unchanged

        UserProfileDto dto = userService.updateProfile(USER_ID, request);

        assertThat(dto.getFirstname()).isEqualTo("New");
        assertThat(dto.getLastname()).isEqualTo("Name");
    }

    @Test
    void updateProfileThrowsWhenCityNotFound() {
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setCityId(99L);

        assertThatThrownBy(() -> userService.updateProfile(USER_ID, request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateProfileSetsCityWhenCityIdProvidedAndFound() {
        User user = activeUser();
        City city = new City();
        city.setId(3L);
        city.setName("Almaty");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(cityRepository.findById(3L)).thenReturn(Optional.of(city));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setCityId(3L);

        UserProfileDto dto = userService.updateProfile(USER_ID, request);

        assertThat(dto.getCityId()).isEqualTo(3L);
        assertThat(dto.getCityName()).isEqualTo("Almaty");
    }

    @Test
    void deleteAccountAnonymizesEmailAndMarksDeleted() {
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteAccount(USER_ID);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("user@test.com.deleted." + USER_ID);
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void setBlockedThrowsWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setBlocked(USER_ID, true))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void setBlockedTrueSetsStatusBlocked() {
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.setBlocked(USER_ID, true);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    void setBlockedFalseSetsStatusActive() {
        User user = activeUser();
        user.setStatus(UserStatus.BLOCKED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.setBlocked(USER_ID, false);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
