package com.keepbooking.restaurant.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.restaurant.model.WorkingHoursOverride;

public interface WorkingHoursOverrideRepository extends JpaRepository<WorkingHoursOverride, Long> {

    List<WorkingHoursOverride> findByRestaurantIdOrderByDate(Long restaurantId);

    Optional<WorkingHoursOverride> findByRestaurantIdAndDate(Long restaurantId, LocalDate date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkingHoursOverride o where o.restaurant.id = :restaurantId and o.date = :date")
    void deleteByRestaurantIdAndDate(@Param("restaurantId") Long restaurantId, @Param("date") LocalDate date);
}
