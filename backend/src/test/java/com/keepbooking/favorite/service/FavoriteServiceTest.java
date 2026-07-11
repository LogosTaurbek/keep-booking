package com.keepbooking.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.favorite.model.Favorite;
import com.keepbooking.favorite.repository.FavoriteRepository;
import com.keepbooking.restaurant.dto.RestaurantDto;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private UserRepository userRepository;

    private FavoriteService favoriteService;

    private static final Long USER_ID = 1L;
    private static final Long RESTAURANT_ID = 10L;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository, restaurantRepository, userRepository);
    }

    private Restaurant restaurant() {
        Company company = Company.builder().id(1L).name("Co").build();
        return Restaurant.builder().id(RESTAURANT_ID).company(company).name("Test Restaurant").build();
    }

    @Test
    void addIsIdempotentWhenAlreadyFavorited() {
        when(favoriteRepository.findByUserIdAndRestaurantId(USER_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(Favorite.builder().id(1L).build()));

        favoriteService.add(USER_ID, RESTAURANT_ID);

        verify(restaurantRepository, never()).findById(any());
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addThrowsWhenRestaurantNotFound() {
        when(favoriteRepository.findByUserIdAndRestaurantId(USER_ID, RESTAURANT_ID)).thenReturn(Optional.empty());
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.add(USER_ID, RESTAURANT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void addSavesNewFavoriteWhenNotAlreadyFavorited() {
        when(favoriteRepository.findByUserIdAndRestaurantId(USER_ID, RESTAURANT_ID)).thenReturn(Optional.empty());
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant()));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());

        favoriteService.add(USER_ID, RESTAURANT_ID);

        verify(favoriteRepository).save(any(Favorite.class));
    }

    @Test
    void removeIsIdempotentWhenNotFavorited() {
        when(favoriteRepository.findByUserIdAndRestaurantId(USER_ID, RESTAURANT_ID)).thenReturn(Optional.empty());

        favoriteService.remove(USER_ID, RESTAURANT_ID);

        verify(favoriteRepository, never()).delete(any());
    }

    @Test
    void removeDeletesExistingFavorite() {
        Favorite favorite = Favorite.builder().id(1L).build();
        when(favoriteRepository.findByUserIdAndRestaurantId(USER_ID, RESTAURANT_ID)).thenReturn(Optional.of(favorite));

        favoriteService.remove(USER_ID, RESTAURANT_ID);

        verify(favoriteRepository).delete(favorite);
    }

    @Test
    void getMyFavoritesMapsToRestaurantDtos() {
        Favorite favorite = Favorite.builder().id(1L).restaurant(restaurant()).build();
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(java.util.List.of(favorite));

        java.util.List<RestaurantDto> result = favoriteService.getMyFavorites(USER_ID);

        assertThat(result).extracting(RestaurantDto::getId).containsExactly(RESTAURANT_ID);
    }
}
