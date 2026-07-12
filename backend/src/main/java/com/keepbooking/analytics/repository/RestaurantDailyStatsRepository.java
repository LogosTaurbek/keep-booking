package com.keepbooking.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.analytics.model.RestaurantDailyStats;

public interface RestaurantDailyStatsRepository extends JpaRepository<RestaurantDailyStats, Long> {

    Optional<RestaurantDailyStats> findByRestaurantIdAndStatDate(Long restaurantId, LocalDate statDate);

    // List<Object[]>, not Object[] - a bare Object[] return type makes Spring Data JPA return the
    // whole single-row tuple as element 0 instead of unwrapping it (ClassCastException: Object[]
    // cannot be cast to Number), same as every other multi-column aggregate query in this codebase.
    @Query("""
            SELECT COALESCE(SUM(s.pendingCount), 0), COALESCE(SUM(s.confirmedCount), 0),
                   COALESCE(SUM(s.rejectedCount), 0), COALESCE(SUM(s.cancelledCount), 0),
                   COALESCE(SUM(s.completedCount), 0), COALESCE(SUM(s.noShowCount), 0)
            FROM RestaurantDailyStats s
            WHERE s.restaurant.id = :restaurantId AND s.statDate BETWEEN :from AND :to
            """)
    List<Object[]> sumStatusCounts(@Param("restaurantId") Long restaurantId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
