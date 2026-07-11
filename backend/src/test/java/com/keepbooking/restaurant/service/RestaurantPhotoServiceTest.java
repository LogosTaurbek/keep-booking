package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.common.storage.FileStorageService;
import com.keepbooking.restaurant.dto.RestaurantPhotoDto;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantPhoto;
import com.keepbooking.restaurant.repository.RestaurantPhotoRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;

/**
 * delete() double-checks photo.restaurant.id == the restaurantId path param, not just
 * that the photo exists — so a valid photo id belonging to a different restaurant must
 * still 404, not leak/delete cross-restaurant. That's the one piece of logic distinct
 * from the plain Hall/Table/MenuItem owner-check shape.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantPhotoServiceTest {

    @Mock
    private RestaurantPhotoRepository restaurantPhotoRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private FileStorageService fileStorageService;

    private RestaurantPhotoService restaurantPhotoService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESTAURANT_ID = 10L;
    private static final Long OTHER_RESTAURANT_ID = 20L;
    private static final Long PHOTO_ID = 100L;

    @BeforeEach
    void setUp() {
        restaurantPhotoService = new RestaurantPhotoService(restaurantPhotoRepository, restaurantRepository, fileStorageService);
    }

    private Restaurant restaurantOwnedBy(Long id, Long ownerId) {
        Company company = Company.builder().id(1L).owner(User.builder().id(ownerId).build()).name("Co").build();
        return Restaurant.builder().id(id).company(company).name("Test Restaurant").build();
    }

    private MultipartFile file() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void uploadThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantPhotoService.upload(OWNER_ID, RESTAURANT_ID, file()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void uploadThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(RESTAURANT_ID, OWNER_ID)));

        assertThatThrownBy(() -> restaurantPhotoService.upload(OTHER_USER_ID, RESTAURANT_ID, file()))
                .isInstanceOf(AccessDeniedException.class);

        verify(restaurantPhotoRepository, never()).save(any());
    }

    @Test
    void uploadAssignsNextPositionBasedOnExistingPhotoCount() {
        Restaurant restaurant = restaurantOwnedBy(RESTAURANT_ID, OWNER_ID);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(fileStorageService.upload(any(), any())).thenReturn("https://cdn/bucket/restaurants/10/x.jpg");
        when(restaurantPhotoRepository.findByRestaurantIdOrderByPositionAsc(RESTAURANT_ID))
                .thenReturn(List.of(RestaurantPhoto.builder().id(1L).restaurant(restaurant).position(0).build(),
                        RestaurantPhoto.builder().id(2L).restaurant(restaurant).position(1).build()));
        when(restaurantPhotoRepository.save(any(RestaurantPhoto.class))).thenAnswer(inv -> {
            RestaurantPhoto p = inv.getArgument(0);
            p.setId(PHOTO_ID);
            return p;
        });

        RestaurantPhotoDto dto = restaurantPhotoService.upload(OWNER_ID, RESTAURANT_ID, file());

        assertThat(dto.getPosition()).isEqualTo(2);
        assertThat(dto.getUrl()).isEqualTo("https://cdn/bucket/restaurants/10/x.jpg");
    }

    @Test
    void deleteThrowsWhenPhotoNotFound() {
        when(restaurantPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantPhotoService.delete(OWNER_ID, RESTAURANT_ID, PHOTO_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_PHOTO_NOT_FOUND);
    }

    @Test
    void deleteThrowsNotFoundWhenPhotoBelongsToADifferentRestaurant() {
        Restaurant restaurant = restaurantOwnedBy(OTHER_RESTAURANT_ID, OWNER_ID);
        RestaurantPhoto photo = RestaurantPhoto.builder().id(PHOTO_ID).restaurant(restaurant).position(0).build();
        when(restaurantPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> restaurantPhotoService.delete(OWNER_ID, RESTAURANT_ID, PHOTO_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_PHOTO_NOT_FOUND);

        verify(restaurantPhotoRepository, never()).delete(any());
    }

    @Test
    void deleteThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        Restaurant restaurant = restaurantOwnedBy(RESTAURANT_ID, OWNER_ID);
        RestaurantPhoto photo = RestaurantPhoto.builder().id(PHOTO_ID).restaurant(restaurant).position(0).build();
        when(restaurantPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> restaurantPhotoService.delete(OTHER_USER_ID, RESTAURANT_ID, PHOTO_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(restaurantPhotoRepository, never()).delete(any());
    }

    @Test
    void deleteSucceedsForOwnerAndRemovesUnderlyingFile() {
        Restaurant restaurant = restaurantOwnedBy(RESTAURANT_ID, OWNER_ID);
        RestaurantPhoto photo = RestaurantPhoto.builder().id(PHOTO_ID).restaurant(restaurant)
                .url("https://cdn/bucket/restaurants/10/x.jpg").position(0).build();
        when(restaurantPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo));

        restaurantPhotoService.delete(OWNER_ID, RESTAURANT_ID, PHOTO_ID);

        verify(restaurantPhotoRepository).delete(photo);
        verify(fileStorageService).delete("https://cdn/bucket/restaurants/10/x.jpg");
    }
}
