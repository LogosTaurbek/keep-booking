package com.keepbooking.analytics.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.analytics.model.RestaurantDailyHourStats;

public interface RestaurantDailyHourStatsRepository extends JpaRepository<RestaurantDailyHourStats, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RestaurantDailyHourStats h WHERE h.restaurant.id = :restaurantId AND h.statDate = :statDate")
    void deleteByRestaurantIdAndStatDate(@Param("restaurantId") Long restaurantId, @Param("statDate") LocalDate statDate);

    @Query("""
            SELECT h.hourOfDay, SUM(h.bookingCount) FROM RestaurantDailyHourStats h
            WHERE h.restaurant.id = :restaurantId AND h.statDate BETWEEN :from AND :to
            GROUP BY h.hourOfDay
            ORDER BY SUM(h.bookingCount) DESC
            """)
    List<Object[]> findPopularHours(@Param("restaurantId") Long restaurantId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    Pageable pageable);
}
