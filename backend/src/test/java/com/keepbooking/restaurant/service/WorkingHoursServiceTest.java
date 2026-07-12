package com.keepbooking.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.keepbooking.restaurant.dto.UpsertWorkingHoursOverrideRequest;
import com.keepbooking.restaurant.dto.WorkingHoursDto;
import com.keepbooking.restaurant.dto.WorkingHoursItemRequest;
import com.keepbooking.restaurant.dto.WorkingHoursOverrideDto;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.WorkingHours;
import com.keepbooking.restaurant.model.WorkingHoursOverride;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.WorkingHoursOverrideRepository;
import com.keepbooking.restaurant.repository.WorkingHoursRepository;
import com.keepbooking.user.model.User;

@ExtendWith(MockitoExtension.class)
class WorkingHoursServiceTest {

    @Mock
    private WorkingHoursRepository workingHoursRepository;
    @Mock
    private WorkingHoursOverrideRepository overrideRepository;
    @Mock
    private RestaurantRepository restaurantRepository;

    private WorkingHoursService workingHoursService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESTAURANT_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        workingHoursService = new WorkingHoursService(workingHoursRepository, overrideRepository, restaurantRepository);
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
    void replaceWeekThrowsWhenOpenAndCloseTimeAreEqualAndDayIsNotOff() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        WorkingHoursItemRequest bad = item(1, false);
        bad.setCloseTime(bad.getOpenTime());

        assertThatThrownBy(() -> workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(bad)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(workingHoursRepository, never()).saveAll(anyList());
    }

    @Test
    void replaceWeekThrowsWhenOpenOrCloseMissingAndDayIsNotOff() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        WorkingHoursItemRequest bad = item(1, false);
        bad.setCloseTime(null);

        assertThatThrownBy(() -> workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(bad)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void replaceWeekAllowsOvernightScheduleWhereCloseIsBeforeOpen() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));
        when(workingHoursRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        WorkingHoursItemRequest overnight = item(5, false);
        overnight.setOpenTime(LocalTime.of(18, 0));
        overnight.setCloseTime(LocalTime.of(2, 0));

        List<WorkingHoursDto> result = workingHoursService.replaceWeek(OWNER_ID, RESTAURANT_ID, List.of(overnight));

        assertThat(result.get(0).getOpenTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(result.get(0).getCloseTime()).isEqualTo(LocalTime.of(2, 0));
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

    private UpsertWorkingHoursOverrideRequest overrideRequest(LocalDate date, LocalTime open, LocalTime close, Boolean closed) {
        UpsertWorkingHoursOverrideRequest request = new UpsertWorkingHoursOverrideRequest();
        request.setDate(date);
        request.setOpenTime(open);
        request.setCloseTime(close);
        request.setIsClosed(closed);
        return request;
    }

    @Test
    void upsertOverrideThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workingHoursService.upsertOverride(OWNER_ID, RESTAURANT_ID,
                overrideRequest(DATE, LocalTime.of(10, 0), LocalTime.of(20, 0), false)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void upsertOverrideThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> workingHoursService.upsertOverride(OTHER_USER_ID, RESTAURANT_ID,
                overrideRequest(DATE, LocalTime.of(10, 0), LocalTime.of(20, 0), false)))
                .isInstanceOf(AccessDeniedException.class);

        verify(overrideRepository, never()).save(any());
    }

    @Test
    void upsertOverrideCreatesNewEntryForAHoliday() {
        Restaurant restaurant = restaurantOwnedBy(OWNER_ID);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(overrideRepository.findByRestaurantIdAndDate(RESTAURANT_ID, DATE)).thenReturn(Optional.empty());
        when(overrideRepository.save(any(WorkingHoursOverride.class))).thenAnswer(inv -> {
            WorkingHoursOverride o = inv.getArgument(0);
            o.setId(500L);
            return o;
        });

        WorkingHoursOverrideDto dto = workingHoursService.upsertOverride(OWNER_ID, RESTAURANT_ID,
                overrideRequest(DATE, null, null, true));

        assertThat(dto.getId()).isEqualTo(500L);
        assertThat(dto.getIsClosed()).isTrue();
        assertThat(dto.getDate()).isEqualTo(DATE);
    }

    @Test
    void upsertOverrideReplacesExistingEntryForTheSameDate() {
        Restaurant restaurant = restaurantOwnedBy(OWNER_ID);
        WorkingHoursOverride existing = WorkingHoursOverride.builder().id(500L).restaurant(restaurant).date(DATE)
                .openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(16, 0)).isClosed(false).build();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(overrideRepository.findByRestaurantIdAndDate(RESTAURANT_ID, DATE)).thenReturn(Optional.of(existing));
        when(overrideRepository.save(any(WorkingHoursOverride.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkingHoursOverrideDto dto = workingHoursService.upsertOverride(OWNER_ID, RESTAURANT_ID,
                overrideRequest(DATE, LocalTime.of(12, 0), LocalTime.of(15, 0), false));

        assertThat(dto.getId()).isEqualTo(500L);
        assertThat(dto.getOpenTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(dto.getCloseTime()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void upsertOverrideThrowsWhenNotClosedAndTimesMissing() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> workingHoursService.upsertOverride(OWNER_ID, RESTAURANT_ID,
                overrideRequest(DATE, null, null, false)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(overrideRepository, never()).save(any());
    }

    @Test
    void deleteOverrideThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> workingHoursService.deleteOverride(OTHER_USER_ID, RESTAURANT_ID, DATE))
                .isInstanceOf(AccessDeniedException.class);

        verify(overrideRepository, never()).deleteByRestaurantIdAndDate(any(), any());
    }

    @Test
    void deleteOverrideSucceedsForOwner() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurantOwnedBy(OWNER_ID)));

        workingHoursService.deleteOverride(OWNER_ID, RESTAURANT_ID, DATE);

        verify(overrideRepository).deleteByRestaurantIdAndDate(RESTAURANT_ID, DATE);
    }

    @Test
    void listOverridesMapsAllEntries() {
        Restaurant restaurant = restaurantOwnedBy(OWNER_ID);
        WorkingHoursOverride holiday = WorkingHoursOverride.builder().id(1L).restaurant(restaurant).date(DATE)
                .isClosed(true).build();
        when(overrideRepository.findByRestaurantIdOrderByDate(RESTAURANT_ID)).thenReturn(List.of(holiday));

        List<WorkingHoursOverrideDto> result = workingHoursService.listOverrides(RESTAURANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(DATE);
        assertThat(result.get(0).getIsClosed()).isTrue();
    }
}
