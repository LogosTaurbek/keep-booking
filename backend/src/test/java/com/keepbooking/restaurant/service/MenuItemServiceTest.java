package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.CreateMenuItemRequest;
import com.keepbooking.restaurant.dto.MenuItemDto;
import com.keepbooking.restaurant.dto.UpdateMenuItemRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.MenuItem;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.MenuItemRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.User;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private RestaurantRepository restaurantRepository;

    private MenuItemService menuItemService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESTAURANT_ID = 10L;
    private static final Long ITEM_ID = 100L;

    @BeforeEach
    void setUp() {
        menuItemService = new MenuItemService(menuItemRepository, restaurantRepository);
    }

    private Restaurant restaurantOwnedBy(Long ownerId) {
        Company company = Company.builder().id(1L).owner(User.builder().id(ownerId).build()).name("Co").build();
        return Restaurant.builder().id(RESTAURANT_ID).company(company).name("Test Restaurant").build();
    }

    private MenuItem itemIn(Restaurant restaurant) {
        return MenuItem.builder().id(ITEM_ID).restaurant(restaurant).name("Pizza")
                .price(new BigDecimal("10.00")).position(0).build();
    }

    private CreateMenuItemRequest createRequest() {
        CreateMenuItemRequest request = new CreateMenuItemRequest();
        request.setRestaurantId(RESTAURANT_ID);
        request.setName("Pizza");
        request.setPrice(new BigDecimal("10.00"));
        return request;
    }

    @Test
    void createThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.create(OWNER_ID, createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void createThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> menuItemService.create(OTHER_USER_ID, createRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void createDefaultsPositionToZeroWhenNotProvided() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> {
            MenuItem m = inv.getArgument(0);
            m.setId(ITEM_ID);
            return m;
        });

        MenuItemDto dto = menuItemService.create(OWNER_ID, createRequest());

        assertThat(dto.getPosition()).isZero();
        assertThat(dto.getName()).isEqualTo("Pizza");
    }

    @Test
    void updateThrowsWhenItemNotFound() {
        when(menuItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.update(OWNER_ID, ITEM_ID, new UpdateMenuItemRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MENU_ITEM_NOT_FOUND);
    }

    @Test
    void updateThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        MenuItem item = itemIn(restaurantOwnedBy(OWNER_ID));
        when(menuItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> menuItemService.update(OTHER_USER_ID, ITEM_ID, new UpdateMenuItemRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void updateOnlyChangesProvidedFieldsLeavingOthersUntouched() {
        MenuItem item = itemIn(restaurantOwnedBy(OWNER_ID));
        item.setDescription("Old description");
        when(menuItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMenuItemRequest request = new UpdateMenuItemRequest();
        request.setName("Pizza Margherita");
        // description/price intentionally left null -> should stay unchanged

        MenuItemDto dto = menuItemService.update(OWNER_ID, ITEM_ID, request);

        assertThat(dto.getName()).isEqualTo("Pizza Margherita");
        assertThat(dto.getDescription()).isEqualTo("Old description");
        assertThat(dto.getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void deleteThrowsWhenItemNotFound() {
        when(menuItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.delete(OWNER_ID, ITEM_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MENU_ITEM_NOT_FOUND);
    }

    @Test
    void deleteThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        MenuItem item = itemIn(restaurantOwnedBy(OWNER_ID));
        when(menuItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> menuItemService.delete(OTHER_USER_ID, ITEM_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(menuItemRepository, never()).delete(any());
    }

    @Test
    void deleteSucceedsForOwner() {
        MenuItem item = itemIn(restaurantOwnedBy(OWNER_ID));
        when(menuItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        menuItemService.delete(OWNER_ID, ITEM_ID);

        verify(menuItemRepository).delete(item);
    }
}
