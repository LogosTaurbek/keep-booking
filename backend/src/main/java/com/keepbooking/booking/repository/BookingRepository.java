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

    Page<Booking> findByRestaurantIdOrderByBookingDateDesc(Long restaurantId, Pageable pageable);

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
}
