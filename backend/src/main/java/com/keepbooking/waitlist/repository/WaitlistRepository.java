package com.keepbooking.waitlist.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.waitlist.model.WaitlistEntry;
import com.keepbooking.waitlist.model.WaitlistStatus;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByIdAndUserId(Long id, Long userId);

    Page<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<WaitlistEntry> findByUserIdAndRestaurantIdAndBookingDateAndTimeFromAndTimeToAndStatus(
            Long userId, Long restaurantId, LocalDate bookingDate, LocalTime timeFrom, LocalTime timeTo,
            WaitlistStatus status);

    // A booking freed up [freedFrom, freedTo) on bookingDate - any ACTIVE entry whose desired
    // window overlaps it (same overlap rule as double-booking, tz2.txt §10.3) and whose party
    // fits the freed table's capacity is a candidate, oldest-waiting first.
    @Query("""
            SELECT w FROM WaitlistEntry w
            WHERE w.restaurant.id = :restaurantId
              AND w.status = 'ACTIVE'
              AND w.bookingDate = :bookingDate
              AND w.timeFrom < :freedTo AND w.timeTo > :freedFrom
              AND w.guestCount <= :capacity
            ORDER BY w.createdAt ASC
            """)
    List<WaitlistEntry> findMatchingActive(@Param("restaurantId") Long restaurantId,
                                            @Param("bookingDate") LocalDate bookingDate,
                                            @Param("freedFrom") LocalTime freedFrom,
                                            @Param("freedTo") LocalTime freedTo,
                                            @Param("capacity") Integer capacity,
                                            Pageable pageable);
}
