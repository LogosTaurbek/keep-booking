package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.WorkingHours;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByRestaurantIdOrderByDayOfWeek(Long restaurantId);

    Optional<WorkingHours> findByRestaurantIdAndDayOfWeek(Long restaurantId, Integer dayOfWeek);
}
