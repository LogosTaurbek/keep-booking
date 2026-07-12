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

    private BookingService bookingService;

    private User user;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, restaurantRepository, tableRepository,
                userRepository, idempotencyService, notificationService, auditLogService, new SimpleMeterRegistry(),
                workingHoursResolver);
        // Most tests aren't about opening hours - default to "always open" and let the
        // dedicated test below override this to exercise the BOOKING_RESTAURANT_CLOSED path.
        lenient().when(workingHoursResolver.isOpenAt(any(), any(), any(), any())).thenReturn(true);

        Restaurant restaurant = Restaurant.builder()
                .id(1L)
                .name("Test Restaurant")
                .status(RestaurantStatus.ACTIVE)
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

        CreateBookingRequest request = validRequest();
        request.setBookingDate(LocalDate.now());
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

        assertThatThrownBy(() -> bookingService.updateStatus(1L, user.getId(), request, true))
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

        assertThatThrownBy(() -> bookingService.updateStatus(1L, 999L, request, false))
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

        assertThatThrownBy(() -> bookingService.updateStatus(1L, user.getId(), request, false))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_STATUS_TRANSITION);
    }

    @Test
    void updateStatusToConfirmedSetsConfirmedByAndAllowsManager() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        User manager = User.builder().id(200L).firstname("Mgr").lastname("Admin").email("mgr@test.com").build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CONFIRMED);

        BookingDto dto = bookingService.updateStatus(1L, manager.getId(), request, true);

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedBy()).isEqualTo(manager);
    }

    @Test
    void updateStatusAllowsOwnerToCancelOwnBookingWithoutManagerRole() {
        Booking booking = Booking.builder().id(1L).user(user).status(BookingStatus.PENDING)
                .restaurant(table.getHall().getRestaurant()).table(table).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CANCELLED);
        request.setCancelReason("changed my mind");

        BookingDto dto = bookingService.updateStatus(1L, user.getId(), request, false);

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledBy()).isEqualTo(user);
        assertThat(booking.getCancelReason()).isEqualTo("changed my mind");
    }
}
