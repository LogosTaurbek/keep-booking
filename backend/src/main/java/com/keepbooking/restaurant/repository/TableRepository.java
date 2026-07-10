package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.model.TableStatus;

public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByHallIdAndStatus(Long hallId, TableStatus status);

    List<RestaurantTable> findByHallId(Long hallId);

    Optional<RestaurantTable> findByIdAndHallId(Long id, Long hallId);
}
