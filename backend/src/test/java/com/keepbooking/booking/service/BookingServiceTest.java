package com.keepbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.booking.dto.BookingDto;
import com.keepbooking.booking.dto.CreateBookingRequest;
import com.keepbooking.booking.dto.UpdateBookingStatusRequest;
import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.audit.AuditLogService;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.notification.service.NotificationService;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Hall;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.model.RestaurantTable;
import com.keepbooking.restaurant.model.TableStatus;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.repository.TableRepository;
import com.keepbooking.restaurant.service.WorkingHoursResolver;
import com.keepbooking.user.model.User;
import com.keepbooking.user.repository.UserRepository;
import com.keepbooking.waitlist.service.WaitlistService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for the booking creation and status-transition business rules
 * (tz2.txt §11.1 / §21) — no Spring context, no database, all collaborators mocked
 * or (for MeterRegistry) a real lightweight in-memory instance.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private WorkingHoursResolver workingHoursResolver;
    @Mock
    private WaitlistService waitlistService;

    private BookingService bookingService;

    private static final Long RESTAURANT_OWNER_ID = 300L;

    private User user;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, restaurantRepository, tableRepository,
                userRepository, idempotencyService, notificationService, auditLogService, new SimpleMeterRegistry(),
                workingHoursResolver, waitlistService);
        // Most tests aren't about opening hours - default to "always open" and let the
        // dedicated test below override this to exercise the BOOKING_RESTAURANT_CLOSED path.
        lenient().when(workingHoursResolver.isOpenAt(any(), any(), any(), any())).thenReturn(true);

        Company company = Company.builder().id(1L).name("Test Co")
                .owner(User.builder().id(RESTAURANT_OWNER_ID).build()).build();
        Restaurant restaurant = Restaurant.builder()
                .id(1L)
                .name("Test Restaurant")
                .status(RestaurantStatus.ACTIVE)
                .company(company)
                .build();
        Hall hall = Hall.builder().id(1L).restaurant(restaurant).name("Main Hall").build();
        table = RestaurantTable.builder()
                .id(10L)
                .hall(hall)
                .number("T1")
                .capacity(4)
                .minCapacity(2)
                .status(TableStatus.ACTIVE)
                .build();
        user = User.builder().id(100L).firstname("Test").lastname("User").email("test@test.com").build();
    }

    private CreateBookingRequest validRequest() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setTableId(table.getId());
        request.setBookingDate(LocalDate.now().plusDays(1));
        request.setTimeFrom(LocalTime.of(12, 0));
        request.setTimeTo(LocalTime.of(13, 0));
        request.setGuestCount(3);
        return request;
    }

    @Test
    void createSucceedsAndPersistsBookingWhenAllChecksPass() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        when(bookingRepository.existsConflictingBooking(any(), any(), any(), any())).thenReturn(false);
        doAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(999L);
            return b;
        }).when(bookingRepository).save(any(Booking.class));

        BookingDto dto = bookingService.create(user.getId(), validRequest(), null);

        assertThat(dto.getId()).isEqualTo(999L);
        assertThat(dto.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(dto.getTableId()).isEqualTo(table.getId());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createTranslatesExclusionConstraintViolationToTableNotAvailable() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        when(bookingRepository.existsConflictingBooking(any(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("exclusion constraint violated"));

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_AVAILABLE);
    }

    @Test
    void createTranslatesLockAcquisitionFailureToTableNotAvailable() {
        // Under heavy concurrent contention on the same table+slot, Postgres's GiST exclusion
        // constraint check (V004) can surface as a lock failure instead of a constraint
        // violation - both must resolve to the same client-facing outcome (regression test for
        // a CI-only failure: CannotAcquireLockException wasn't caught, crashing with a 500).
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        when(bookingRepository.existsConflictingBooking(any(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("could not obtain lock"));

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_AVAILABLE);
    }

    @Test
    void createThrowsWhenTableNotFound() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_FOUND);
    }

    @Test
    void createThrowsWhenTableNotActive() {
        table.setStatus(TableStatus.MAINTENANCE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_AVAILABLE);
    }

    @Test
    void createThrowsWhenRestaurantNotActive() {
        table.getHall().getRestaurant().setStatus(RestaurantStatus.DRAFT);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_ACTIVE);
    }

    @Test
    void createThrowsWhenTimeFromIsNotBeforeTimeTo() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

        CreateBookingRequest request = validRequest();
        request.setTimeFrom(LocalTime.of(14, 0));
        request.setTimeTo(LocalTime.of(13, 0));

        assertThatThrownBy(() -> bookingService.create(user.getId(), request, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_INVALID_TIME);
    }

    @Test
    void createThrowsWhenBookingDateTimeIsInThePast() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

        // Yesterday, not "today at 00:00" - the latter is only in the past once the wall clock
        // passes 00:30 UTC, making this test flaky for the first half hour of every day (it did
        // flake: NPE from an unstubbed save() once validation unexpectedly let the booking through).
        CreateBookingRequest request = validRequest();
        request.setBookingDate(LocalDate.now().minusDays(1));
        request.setTimeFrom(LocalTime.MIN);
        request.setTimeTo(LocalTime.MIN.plusMinutes(30));

        assertThatThrownBy(() -> bookingService.create(user.getId(), request, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_INVALID_TIME);
    }

    @Test
    void createThrowsWhenGuestCountExceedsTableCapacity() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

        CreateBookingRequest request = validRequest();
        request.setGuestCount(table.getCapacity() + 1);

        assertThatThrownBy(() -> bookingService.create(user.getId(), request, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_GUEST_COUNT);
    }

    @Test
    void createThrowsWhenGuestCountBelowTableMinCapacity() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));

        CreateBookingRequest request = validRequest();
        request.setGuestCount(table.getMinCapacity() - 1);

        assertThatThrownBy(() -> bookingService.create(user.getId(), request, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_GUEST_COUNT);
    }

    @Test
    void createThrowsWhenRestaurantIsClosedAtRequestedTime() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        when(workingHoursResolver.isOpenAt(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_RESTAURANT_CLOSED);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createThrowsWhenTableAlreadyBookedForOverlappingSlot() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        when(bookingRepository.existsConflictingBooking(any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(user.getId(), validRequest(), null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TABLE_NOT_AVAILABLE);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createWithIdempotencyKeyReturnsCachedResultWithoutTouchingRepositories() {
        BookingDto cached = BookingDto.builder().id(555L).status(BookingStatus.PENDING).build();
        when(idempotencyService.get(user.getId(), "key-1")).thenReturn(Optional.of(cached));

        BookingDto dto = bookingService.create(user.getId(), validRequest(), "key-1");

        assertThat(dto).isSameAs(cached);
        verify(tableRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void updateStatusThrowsWhenBookingNotFound() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());
        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> bookingService.updateStatus(1L, user.getId(), request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_NOT_FOUND);
    }

    @Test
    void updateStatusThrowsAccessDeniedWhenActorIsNeitherOwnerNorManager() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() -> bookingService.updateStatus(1L, 999L, request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void updateStatusThrowsOnDisallowedTransition() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.COMPLETED);

        assertThatThrownBy(() -> bookingService.updateStatus(1L, user.getId(), request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_STATUS_TRANSITION);
    }

    @Test
    void updateStatusToConfirmedSetsConfirmedByWhenActorOwnsTheRestaurant() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        // No role check involved - owning this booking's restaurant is what makes someone its
        // manager. See updateStatusThrowsAccessDeniedWhenActorIsNeitherOwnerNorManager for the
        // rejection case (some other, unrelated user).
        User manager = User.builder().id(RESTAURANT_OWNER_ID).firstname("Mgr").lastname("Admin").email("mgr@test.com").build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CONFIRMED);

        BookingDto dto = bookingService.updateStatus(1L, manager.getId(), request);

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedBy()).isEqualTo(manager);
        verify(waitlistService, never()).notifyTableFreed(any());
    }

    @Test
    void updateStatusAllowsOwnerToCancelOwnBooking() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CANCELLED);
        request.setCancelReason("changed my mind");

        BookingDto dto = bookingService.updateStatus(1L, user.getId(), request);

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledBy()).isEqualTo(user);
        assertThat(booking.getCancelReason()).isEqualTo("changed my mind");
        verify(waitlistService).notifyTableFreed(booking);
    }

    @Test
    void getRestaurantBookingsThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getRestaurantBookings(RESTAURANT_OWNER_ID, 1L, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void getRestaurantBookingsThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(table.getHall().getRestaurant()));

        assertThatThrownBy(() -> bookingService.getRestaurantBookings(999L, 1L, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getRestaurantBookingsReturnsBookingsForTheOwner() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(table.getHall().getRestaurant()));
        when(bookingRepository.findByRestaurantIdOrderByBookingDateDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(booking)));

        var result = bookingService.getRestaurantBookings(RESTAURANT_OWNER_ID, 1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(BookingDto::getId).containsExactly(1L);
    }
}
