package com.keepbooking.booking.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserIdOrderByBookingDateDesc(Long userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatusOrderByBookingDateDesc(Long userId, BookingStatus status, Pageable pageable);

    long countByStatus(BookingStatus status);

    Page<Booking> findByRestaurantIdOrderByBookingDateDesc(Long restaurantId, Pageable pageable);

    Page<Booking> findByRestaurantIdAndBookingDateBetweenOrderByBookingDateDesc(
            Long restaurantId, LocalDate from, LocalDate to, Pageable pageable);

    List<Booking> findByTableIdAndBookingDateAndStatusIn(Long tableId, LocalDate date, List<BookingStatus> statuses);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = 'CONFIRMED'
              AND (b.bookingDate < :today OR (b.bookingDate = :today AND b.timeTo <= :nowTime))
            """)
    List<Booking> findConfirmedPastEnd(@Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.table.id = :tableId
              AND b.bookingDate = :date
              AND b.status IN ('PENDING', 'CONFIRMED')
              AND b.timeFrom < :timeTo
              AND b.timeTo > :timeFrom
            """)
    boolean existsConflictingBooking(@Param("tableId") Long tableId,
                                     @Param("date") LocalDate date,
                                     @Param("timeFrom") LocalTime timeFrom,
                                     @Param("timeTo") LocalTime timeTo);

    @Query("""
            SELECT b.table.id FROM Booking b
            WHERE b.restaurant.id = :restaurantId
              AND b.bookingDate = :date
              AND b.status IN ('PENDING', 'CONFIRMED')
              AND b.timeFrom < :timeTo
              AND b.timeTo > :timeFrom
            """)
    List<Long> findBookedTableIds(@Param("restaurantId") Long restaurantId,
                                  @Param("date") LocalDate date,
                                  @Param("timeFrom") LocalTime timeFrom,
                                  @Param("timeTo") LocalTime timeTo);

    @Query("""
            SELECT COUNT(DISTINCT b.user.id) FROM Booking b
            WHERE b.restaurant.id = :restaurantId AND b.bookingDate BETWEEN :from AND :to
            """)
    long countDistinctGuestsForRestaurant(@Param("restaurantId") Long restaurantId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    // --- Analytics read-model refresh (tz2.txt §15/§25): AnalyticsRefreshWorker re-aggregates
    // exactly the (restaurant, date) pairs touched since the last cycle into the
    // restaurant_daily_*_stats tables; AnalyticsService reads those, not this table directly. ---

    @Query("""
            SELECT DISTINCT b.restaurant.id, b.bookingDate FROM Booking b
            WHERE b.updatedAt > :since
            """)
    List<Object[]> findDirtyRestaurantDatesSince(@Param("since") Instant since);

    @Query("""
            SELECT b.status, COUNT(b) FROM Booking b
            WHERE b.restaurant.id = :restaurantId AND b.bookingDate = :date
            GROUP BY b.status
            """)
    List<Object[]> countByStatusForRestaurantAndDate(@Param("restaurantId") Long restaurantId,
                                                      @Param("date") LocalDate date);

    @Query("""
            SELECT extract(HOUR FROM b.timeFrom), COUNT(b) FROM Booking b
            WHERE b.restaurant.id = :restaurantId AND b.bookingDate = :date
            GROUP BY extract(HOUR FROM b.timeFrom)
            """)
    List<Object[]> countByHourForRestaurantAndDate(@Param("restaurantId") Long restaurantId,
                                                    @Param("date") LocalDate date);

    @Query("""
            SELECT b.table.id, COUNT(b) FROM Booking b
            WHERE b.restaurant.id = :restaurantId AND b.bookingDate = :date
            GROUP BY b.table.id
            """)
    List<Object[]> countByTableForRestaurantAndDate(@Param("restaurantId") Long restaurantId,
                                                     @Param("date") LocalDate date);
}
