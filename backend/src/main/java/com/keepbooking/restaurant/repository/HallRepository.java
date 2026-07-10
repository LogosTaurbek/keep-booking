package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.Hall;

public interface HallRepository extends JpaRepository<Hall, Long> {

    List<Hall> findByRestaurantId(Long restaurantId);

    Optional<Hall> findByIdAndRestaurantId(Long id, Long restaurantId);
}
