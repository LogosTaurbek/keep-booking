package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.restaurant.model.WorkingHours;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByRestaurantIdOrderByDayOfWeek(Long restaurantId);

    Optional<WorkingHours> findByRestaurantIdAndDayOfWeek(Long restaurantId, Integer dayOfWeek);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkingHours wh where wh.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") Long restaurantId);
}
