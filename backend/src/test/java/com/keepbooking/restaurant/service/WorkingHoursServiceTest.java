package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.WorkingHoursDto;
import com.keepbooking.restaurant.dto.WorkingHoursItemRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;
import com.keepbooking.user.model.User;

@ExtendWith(MockitoExtension.class)
class WorkingHoursServiceTest {

    @Mock
    private WorkingHoursRepository workingHoursRepository;
    @Mock
    private RestaurantRepository restaurantRepository;

    private WorkingHoursService workingHoursService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESTAURANT_ID = 10L;

    @BeforeEach
    void setUp() {
        workingHoursService = new WorkingHoursService(workingHoursRepository, restaurantRepository);
    }

    private Restaurant restaurantOwnedBy(Long ownerId) {
        Company company = Company.builder().id(1L).owner(User.builder().id(ownerId).build()).name("Co").build();
        return Restaurant.builder().id(RESTAURANT_ID).company(company).name("Test Restaurant").build();
    }

    private WorkingHoursItemRequest item(int dayOfWeek, Boolean isDayOff) {
        WorkingHoursItemRequest item = new WorkingHoursItemRequest();
        item.setDayOfWeek(dayOfWeek);
        item.setOpenTime(LocalTime.of(9, 0));
        item.setCloseTime(LocalTime.of(22, 0));
        item.setIsDayOff(isDayOff);
        return item;
    }

    @Test
    void replaceWeekThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(item(1, false))))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void replaceWeekThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> workingHoursService.replaceWeek(OTHER_USER_ID, RESTAURANT_ID, List.of(item(1, false))))
                .isInstanceOf(AccessDeniedException.class);

        verify(workingHoursRepository, never()).deleteByRestaurantId(RESTAURANT_ID);
        verify(workingHoursRepository, never()).saveAll(anyList());
    }

    @Test
    void replaceWeekDeletesExistingEntriesBeforeSavingNewOnes() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(workingHoursRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(item(1, false), item(2, true)));

        verify(workingHoursRepository).deleteByRestaurantId(RESTAURANT_ID);
        verify(workingHoursRepository).saveAll(anyList());
    }

    @Test
    void replaceWeekDefaultsIsDayOffToFalseWhenNull() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(workingHoursRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<WorkingHoursDto> result = workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(item(1, null)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsDayOff()).isFalse();
    }

    @Test
    void replaceWeekPreservesIsDayOffTrueWhenSet() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(workingHoursRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<WorkingHoursDto> result = workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(item(7, true)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsDayOff()).isTrue();
        assertThat(result.get(0).getDayOfWeek()).isEqualTo(7);
    }

    @Test
    void listByRestaurantMapsAllEntriesOrderedByRepository() {
        Restaurant restaurant = restaurantOwnedBy(OWNER_ID);
        WorkingHours monday = WorkingHours.builder().id(1L).restaurant(restaurant).dayOfWeek(1)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0)).isDayOff(false).build();
        when(workingHoursRepository.findByRestaurantIdOrderByDayOfWeek(RESTAURANT_ID)).thenReturn(List.of(monday));

        List<WorkingHoursDto> result = workingHoursService.listByRestaurant(RESTAURANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDayOfWeek()).isEqualTo(1);
    }
}
