package com.keepbooking.analytics.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.analytics.model.RestaurantDailyTableStats;

public interface RestaurantDailyTableStatsRepository extends JpaRepository<RestaurantDailyTableStats, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RestaurantDailyTableStats t WHERE t.restaurant.id = :restaurantId AND t.statDate = :statDate")
    void deleteByRestaurantIdAndStatDate(@Param("restaurantId") Long restaurantId, @Param("statDate") LocalDate statDate);

    @Query("""
            SELECT t.table.id, t.table.number, SUM(t.bookingCount) FROM RestaurantDailyTableStats t
            WHERE t.restaurant.id = :restaurantId AND t.statDate BETWEEN :from AND :to
            GROUP BY t.table.id, t.table.number
            ORDER BY SUM(t.bookingCount) DESC
            """)
    List<Object[]> findPopularTables(@Param("restaurantId") Long restaurantId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     Pageable pageable);
}
