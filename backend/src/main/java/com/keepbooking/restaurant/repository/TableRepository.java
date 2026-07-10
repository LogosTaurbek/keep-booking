package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.model.TableStatus;

public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByHallIdAndStatus(Long hallId, TableStatus status);

    List<RestaurantTable> findByHallId(Long hallId);

    Optional<RestaurantTable> findByIdAndHallId(Long id, Long hallId);

    @Query("""
            SELECT t FROM RestaurantTable t
            WHERE t.hall.restaurant.id = :restaurantId
              AND t.status = 'ACTIVE'
              AND t.capacity >= :guests
              AND (t.minCapacity IS NULL OR t.minCapacity <= :guests)
            """)
    List<RestaurantTable> findCandidatesForAvailability(@Param("restaurantId") Long restaurantId,
                                                          @Param("guests") Integer guests);
}
