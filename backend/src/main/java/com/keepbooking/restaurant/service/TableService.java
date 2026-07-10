package com.keepbooking.restaurant.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.dto.BatchUpdateTablesRequest;
import com.keepbooking.restaurant.dto.CreateTableRequest;
import com.keepbooking.restaurant.dto.TableDto;
import com.keepbooking.restaurant.dto.TablePositionItem;
import com.keepbooking.restaurant.dto.UpdateTableRequest;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.repository.HallRepository;
import com.keepbooking.restaurant.repository.TableRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final HallRepository hallRepository;

    @Transactional
    public TableDto create(Long userId, CreateTableRequest request) {
        Hall hall = findHall(request.getHallId());
        verifyOwner(hall, userId);

        RestaurantTable table = RestaurantTable.builder()
                .hall(hall)
                .number(request.getNumber())
                .capacity(request.getCapacity())
                .minCapacity(request.getMinCapacity())
                .build();

        if (request.getShape() != null) table.setShape(request.getShape());
        if (request.getType() != null) table.setType(request.getType());
        if (request.getPosX() != null) table.setPosX(request.getPosX());
        if (request.getPosY() != null) table.setPosY(request.getPosY());
        if (request.getWidth() != null) table.setWidth(request.getWidth());
        if (request.getHeight() != null) table.setHeight(request.getHeight());
        if (request.getRotation() != null) table.setRotation(request.getRotation());
        if (request.getIsVip() != null) table.setIsVip(request.getIsVip());
        if (request.getIsSofa() != null) table.setIsSofa(request.getIsSofa());
        if (request.getNearWindow() != null) table.setNearWindow(request.getNearWindow());
        if (request.getHasSocket() != null) table.setHasSocket(request.getHasSocket());
        if (request.getIsSmoking() != null) table.setIsSmoking(request.getIsSmoking());

        return toDto(tableRepository.save(table));
    }

    @Transactional(readOnly = true)
    public TableDto getById(Long tableId) {
        return toDto(findTable(tableId));
    }

    @Transactional(readOnly = true)
    public List<TableDto> listByHall(Long hallId) {
        return tableRepository.findByHallId(hallId).stream().map(this::toDto).toList();
    }

    @Transactional
    public TableDto update(Long userId, Long tableId, UpdateTableRequest request) {
        RestaurantTable table = findTable(tableId);
        verifyOwner(table.getHall(), userId);

        if (request.getNumber() != null) table.setNumber(request.getNumber());
        if (request.getCapacity() != null) table.setCapacity(request.getCapacity());
        if (request.getMinCapacity() != null) table.setMinCapacity(request.getMinCapacity());
        if (request.getShape() != null) table.setShape(request.getShape());
        if (request.getType() != null) table.setType(request.getType());
        if (request.getPosX() != null) table.setPosX(request.getPosX());
        if (request.getPosY() != null) table.setPosY(request.getPosY());
        if (request.getWidth() != null) table.setWidth(request.getWidth());
        if (request.getHeight() != null) table.setHeight(request.getHeight());
        if (request.getRotation() != null) table.setRotation(request.getRotation());
        if (request.getIsVip() != null) table.setIsVip(request.getIsVip());
        if (request.getIsSofa() != null) table.setIsSofa(request.getIsSofa());
        if (request.getNearWindow() != null) table.setNearWindow(request.getNearWindow());
        if (request.getHasSocket() != null) table.setHasSocket(request.getHasSocket());
        if (request.getIsSmoking() != null) table.setIsSmoking(request.getIsSmoking());
        if (request.getStatus() != null) table.setStatus(request.getStatus());

        return toDto(tableRepository.save(table));
    }

    @Transactional
    public List<TableDto> batchUpdatePositions(Long userId, BatchUpdateTablesRequest request) {
        Hall hall = findHall(request.getHallId());
        verifyOwner(hall, userId);

        Map<Long, RestaurantTable> tablesById = tableRepository.findByHallId(hall.getId()).stream()
                .collect(Collectors.toMap(RestaurantTable::getId, Function.identity()));

        List<RestaurantTable> updated = request.getTables().stream().map(item -> {
            RestaurantTable table = tablesById.get(item.getId());
            if (table == null) {
                throw new ApiException(ErrorCode.TABLE_NOT_FOUND);
            }
            applyPosition(table, item);
            return table;
        }).toList();

        return tableRepository.saveAll(updated).stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(Long userId, Long tableId) {
        RestaurantTable table = findTable(tableId);
        verifyOwner(table.getHall(), userId);
        tableRepository.delete(table);
    }

    private void applyPosition(RestaurantTable table, TablePositionItem item) {
        table.setPosX(item.getPosX());
        table.setPosY(item.getPosY());
        table.setWidth(item.getWidth());
        table.setHeight(item.getHeight());
        table.setRotation(item.getRotation());
    }

    private Hall findHall(Long hallId) {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> new ApiException(ErrorCode.HALL_NOT_FOUND));
    }

    private RestaurantTable findTable(Long tableId) {
        return tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(ErrorCode.TABLE_NOT_FOUND));
    }

    private void verifyOwner(Hall hall, Long userId) {
        Restaurant restaurant = hall.getRestaurant();
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }
    }

    private TableDto toDto(RestaurantTable t) {
        return TableDto.builder()
                .id(t.getId())
                .hallId(t.getHall().getId())
                .number(t.getNumber())
                .capacity(t.getCapacity())
                .minCapacity(t.getMinCapacity())
                .shape(t.getShape())
                .type(t.getType())
                .posX(t.getPosX())
                .posY(t.getPosY())
                .width(t.getWidth())
                .height(t.getHeight())
                .rotation(t.getRotation())
                .isVip(t.getIsVip())
                .isSofa(t.getIsSofa())
                .nearWindow(t.getNearWindow())
                .hasSocket(t.getHasSocket())
                .isSmoking(t.getIsSmoking())
                .status(t.getStatus())
                .build();
    }
}
