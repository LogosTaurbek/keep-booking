package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.CreateHallRequest;
import com.keepbooking.restaurant.dto.HallDto;
import com.keepbooking.restaurant.dto.UpdateHallRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.HallRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;

/**
 * Ownership checks (tz2.txt §4: "RESTAURANT_ADMIN может редактировать только рестораны
 * своей компании") — this pattern (restaurant.company.owner.id == userId) is shared by
 * Hall/Table/MenuItem/RestaurantPhoto services; testing it thoroughly here covers the shape.
 */
@ExtendWith(MockitoExtension.class)
class HallServiceTest {

    @Mock
    private HallRepository hallRepository;
    @Mock
    private RestaurantRepository restaurantRepository;

    private HallService hallService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESTAURANT_ID = 10L;
    private static final Long HALL_ID = 100L;

    @BeforeEach
    void setUp() {
        hallService = new HallService(hallRepository, restaurantRepository);
    }

    private Restaurant restaurantOwnedBy(Long ownerId) {
        Company company = Company.builder().id(1L).owner(User.builder().id(ownerId).build()).name("Co").build();
        return Restaurant.builder().id(RESTAURANT_ID).company(company).name("Test Restaurant").build();
    }

    private CreateHallRequest createRequest() {
        CreateHallRequest request = new CreateHallRequest();
        request.setRestaurantId(RESTAURANT_ID);
        request.setName("Main Hall");
        return request;
    }

    @Test
    void createThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hallService.create(OWNER_ID, createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void createThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> hallService.create(OTHER_USER_ID, createRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(hallRepository, never()).save(any());
    }

    @Test
    void createAppliesDefaultsWhenOptionalFieldsAreNull() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(hallRepository.save(any(Hall.class))).thenAnswer(inv -> {
            Hall h = inv.getArgument(0);
            h.setId(HALL_ID);
            return h;
        });

        HallDto dto = hallService.create(OWNER_ID, createRequest());

        assertThat(dto.getFloor()).isEqualTo(1);
        assertThat(dto.getCanvasWidth()).isEqualTo(800);
        assertThat(dto.getCanvasHeight()).isEqualTo(600);
    }

    @Test
    void updateThrowsWhenHallNotFound() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hallService.update(OWNER_ID, HALL_ID, new UpdateHallRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HALL_NOT_FOUND);
    }

    @Test
    void updateThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        Hall hall = Hall.builder().id(HALL_ID).restaurant(restaurantOwnedBy(OWNER_ID)).name("Hall").build();
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> hallService.update(OTHER_USER_ID, HALL_ID, new UpdateHallRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(hallRepository, never()).save(any());
    }

    @Test
    void updateOnlyChangesProvidedFieldsLeavingOthersUntouched() {
        Hall hall = Hall.builder().id(HALL_ID).restaurant(restaurantOwnedBy(OWNER_ID))
                .name("Old Name").floor(1).canvasWidth(800).canvasHeight(600).build();
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hall));
        when(hallRepository.save(any(Hall.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateHallRequest request = new UpdateHallRequest();
        request.setName("New Name");
        // floor/canvasWidth/canvasHeight intentionally left null -> should stay unchanged

        HallDto dto = hallService.update(OWNER_ID, HALL_ID, request);

        assertThat(dto.getName()).isEqualTo("New Name");
        assertThat(dto.getFloor()).isEqualTo(1);
        assertThat(dto.getCanvasWidth()).isEqualTo(800);
        assertThat(dto.getCanvasHeight()).isEqualTo(600);
    }

    @Test
    void deleteThrowsWhenHallNotFound() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hallService.delete(OWNER_ID, HALL_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HALL_NOT_FOUND);
    }

    @Test
    void deleteThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        Hall hall = Hall.builder().id(HALL_ID).restaurant(restaurantOwnedBy(OWNER_ID)).name("Hall").build();
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> hallService.delete(OTHER_USER_ID, HALL_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(hallRepository, never()).delete(any());
    }

    @Test
    void deleteSucceedsForOwner() {
        Hall hall = Hall.builder().id(HALL_ID).restaurant(restaurantOwnedBy(OWNER_ID)).name("Hall").build();
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hall));

        hallService.delete(OWNER_ID, HALL_ID);

        verify(hallRepository).delete(hall);
    }
}
