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
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.BatchUpdateTablesRequest;
import com.keepbooking.restaurant.dto.CreateTableRequest;
import com.keepbooking.restaurant.dto.TableDto;
import com.keepbooking.restaurant.dto.TablePositionItem;
import com.keepbooking.restaurant.dto.UpdateTableRequest;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.repository.HallRepository;
import com.keepbooking.restaurant.repository.TableRepository;
import com.keepbooking.user.model.User;

/**
 * batchUpdatePositions has logic distinct from Hall/Table CRUD: it loads all tables of a
 * hall into a Map by id and rejects any requested item whose id isn't in that map, even
 * if the table exists elsewhere — this catches "table belongs to a different hall" too.
 */
@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    private TableRepository tableRepository;
    @Mock
    private HallRepository hallRepository;

    private TableService tableService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long HALL_ID = 10L;
    private static final Long TABLE_ID = 100L;

    @BeforeEach
    void setUp() {
        tableService = new TableService(tableRepository, hallRepository);
    }

    private Hall hallOwnedBy(Long ownerId) {
        Company company = Company.builder().id(1L).owner(User.builder().id(ownerId).build()).name("Co").build();
        Restaurant restaurant = Restaurant.builder().id(1L).company(company).name("Restaurant").build();
        return Hall.builder().id(HALL_ID).restaurant(restaurant).name("Main Hall").build();
    }

    private RestaurantTable tableIn(Hall hall) {
        return RestaurantTable.builder().id(TABLE_ID).hall(hall).number("A1").capacity(4).build();
    }

    private CreateTableRequest createRequest() {
        CreateTableRequest request = new CreateTableRequest();
        request.setHallId(HALL_ID);
        request.setNumber("A1");
        request.setCapacity(4);
        return request;
    }

    private TablePositionItem positionItem(Long id) {
        TablePositionItem item = new TablePositionItem();
        item.setId(id);
        item.setPosX(10);
        item.setPosY(20);
        item.setWidth(50);
        item.setHeight(60);
        item.setRotation(90);
        return item;
    }

    @Test
    void createThrowsWhenHallNotFound() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.create(OWNER_ID, createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HALL_NOT_FOUND);
    }

    @Test
    void createThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hallOwnedBy(OWNER_ID)));

        assertThatThrownBy(() -> tableService.create(OTHER_USER_ID, createRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(tableRepository, never()).save(any());
    }

    @Test
    void createSavesTableWithGivenFields() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hallOwnedBy(OWNER_ID)));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> {
            RestaurantTable t = inv.getArgument(0);
            t.setId(TABLE_ID);
            return t;
        });

        TableDto dto = tableService.create(OWNER_ID, createRequest());

        assertThat(dto.getNumber()).isEqualTo("A1");
        assertThat(dto.getCapacity()).isEqualTo(4);
    }

    @Test
    void updateThrowsWhenTableNotFound() {
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.update(OWNER_ID, TABLE_ID, new UpdateTableRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    void updateThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        Hall hall = hallOwnedBy(OWNER_ID);
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(tableIn(hall)));

        assertThatThrownBy(() -> tableService.update(OTHER_USER_ID, TABLE_ID, new UpdateTableRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(tableRepository, never()).save(any());
    }

    @Test
    void updateOnlyChangesProvidedFieldsLeavingOthersUntouched() {
        Hall hall = hallOwnedBy(OWNER_ID);
        RestaurantTable table = tableIn(hall);
        table.setMinCapacity(2);
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTableRequest request = new UpdateTableRequest();
        request.setNumber("B2");
        // capacity/minCapacity intentionally left null -> should stay unchanged

        TableDto dto = tableService.update(OWNER_ID, TABLE_ID, request);

        assertThat(dto.getNumber()).isEqualTo("B2");
        assertThat(dto.getCapacity()).isEqualTo(4);
        assertThat(dto.getMinCapacity()).isEqualTo(2);
    }

    @Test
    void batchUpdatePositionsThrowsWhenHallNotFound() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.empty());
        BatchUpdateTablesRequest request = new BatchUpdateTablesRequest();
        request.setHallId(HALL_ID);
        request.setTables(List.of(positionItem(TABLE_ID)));

        assertThatThrownBy(() -> tableService.batchUpdatePositions(OWNER_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.HALL_NOT_FOUND);
    }

    @Test
    void batchUpdatePositionsThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hallOwnedBy(OWNER_ID)));
        BatchUpdateTablesRequest request = new BatchUpdateTablesRequest();
        request.setHallId(HALL_ID);
        request.setTables(List.of(positionItem(TABLE_ID)));

        assertThatThrownBy(() -> tableService.batchUpdatePositions(OTHER_USER_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(tableRepository, never()).saveAll(any());
    }

    @Test
    void batchUpdatePositionsThrowsTableNotFoundWhenIdIsNotInThisHall() {
        Hall hall = hallOwnedBy(OWNER_ID);
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hall));
        when(tableRepository.findByHallId(HALL_ID)).thenReturn(List.of(tableIn(hall)));

        BatchUpdateTablesRequest request = new BatchUpdateTablesRequest();
        request.setHallId(HALL_ID);
        request.setTables(List.of(positionItem(999L)));

        assertThatThrownBy(() -> tableService.batchUpdatePositions(OWNER_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_FOUND);

        verify(tableRepository, never()).saveAll(any());
    }

    @Test
    void batchUpdatePositionsUpdatesOnlyPositionFieldsLeavingCapacityAndNumberUntouched() {
        Hall hall = hallOwnedBy(OWNER_ID);
        RestaurantTable table = tableIn(hall);
        when(hallRepository.findById(HALL_ID)).thenReturn(Optional.of(hall));
        when(tableRepository.findByHallId(HALL_ID)).thenReturn(List.of(table));
        when(tableRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchUpdateTablesRequest request = new BatchUpdateTablesRequest();
        request.setHallId(HALL_ID);
        request.setTables(List.of(positionItem(TABLE_ID)));

        List<TableDto> result = tableService.batchUpdatePositions(OWNER_ID, request);

        assertThat(result).hasSize(1);
        TableDto dto = result.get(0);
        assertThat(dto.getPosX()).isEqualTo(10);
        assertThat(dto.getPosY()).isEqualTo(20);
        assertThat(dto.getWidth()).isEqualTo(50);
        assertThat(dto.getHeight()).isEqualTo(60);
        assertThat(dto.getRotation()).isEqualTo(90);
        assertThat(dto.getNumber()).isEqualTo("A1");
        assertThat(dto.getCapacity()).isEqualTo(4);
    }

    @Test
    void deleteThrowsWhenTableNotFound() {
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.delete(OWNER_ID, TABLE_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    void deleteThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        Hall hall = hallOwnedBy(OWNER_ID);
        RestaurantTable table = tableIn(hall);
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.delete(OTHER_USER_ID, TABLE_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(tableRepository, never()).delete(any());
    }

    @Test
    void deleteSucceedsForOwner() {
        Hall hall = hallOwnedBy(OWNER_ID);
        RestaurantTable table = tableIn(hall);
        when(tableRepository.findById(TABLE_ID)).thenReturn(Optional.of(table));

        tableService.delete(OWNER_ID, TABLE_ID);

        verify(tableRepository).delete(table);
    }
}
