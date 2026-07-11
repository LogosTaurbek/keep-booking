package com.keepbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.keepbooking.booking.dto.CreateBookingRequest;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.CompanyStatus;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.model.TableStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.restaurant.repository.HallRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;

/**
 * tz2.txt §11.2 / §21: parallel requests for the same table/slot must yield exactly
 * one successful booking. This exercises the real double-booking guarantee end to end —
 * the JPQL pre-check in BookingService AND the Postgres exclusion constraint (V004) —
 * against a real database, not a mock.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///keepbooking",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6380"
})
class BookingConcurrencyIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 10;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private HallRepository hallRepository;
    @Autowired
    private TableRepository tableRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void onlyOneOfManyConcurrentBookingsForTheSameTableAndSlotSucceeds() throws Exception {
        RestaurantTable table = createActiveTableWithCapacity(CONCURRENT_ATTEMPTS);
        List<User> guests = IntStream.range(0, CONCURRENT_ATTEMPTS)
                .mapToObj(i -> saveUser("concurrency-guest-" + i + "@test.com"))
                .toList();

        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime from = LocalTime.of(19, 0);
        LocalTime to = LocalTime.of(20, 0);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        CountDownLatch allThreadsReady = new CountDownLatch(CONCURRENT_ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        List<Future<Void>> futures = guests.stream()
                .map(guest -> pool.<Void>submit(() -> {
                    CreateBookingRequest request = new CreateBookingRequest();
                    request.setTableId(table.getId());
                    request.setBookingDate(date);
                    request.setTimeFrom(from);
                    request.setTimeTo(to);
                    request.setGuestCount(2);

                    allThreadsReady.countDown();
                    go.await();
                    try {
                        bookingService.create(guest.getId(), request, null);
                        successCount.incrementAndGet();
                    } catch (ApiException e) {
                        if (e.getErrorCode() == ErrorCode.TABLE_NOT_AVAILABLE) {
                            conflictCount.incrementAndGet();
                        } else {
                            throw e;
                        }
                    }
                    return null;
                }))
                .toList();

        allThreadsReady.await(10, TimeUnit.SECONDS);
        go.countDown();
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_ATTEMPTS - 1);

        long activeBookingsForSlot = bookingRepository
                .findByTableIdAndBookingDateAndStatusIn(table.getId(), date,
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))
                .size();
        assertThat(activeBookingsForSlot).isEqualTo(1);
    }

    private RestaurantTable createActiveTableWithCapacity(int capacity) {
        User owner = saveUser("concurrency-owner@test.com");
        Company company = companyRepository.save(Company.builder()
                .owner(owner).name("Concurrency Test Co").status(CompanyStatus.ACTIVE).build());
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .company(company).name("Concurrency Test Restaurant").status(RestaurantStatus.ACTIVE).build());
        Hall hall = hallRepository.save(Hall.builder().restaurant(restaurant).name("Main Hall").build());
        return tableRepository.save(RestaurantTable.builder()
                .hall(hall).number("CT1").capacity(capacity).status(TableStatus.ACTIVE).build());
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .firstname("Guest").lastname("Test").email(email).passwordHash("irrelevant-for-this-test").build());
    }
}
