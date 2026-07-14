package com.keepbooking.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.common.security.AccessControlService;
import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.service.RestaurantService;
import com.keepbooking.review.dto.CreateReviewRequest;
import com.keepbooking.review.dto.ReplyToReviewRequest;
import com.keepbooking.review.dto.ReviewDto;
import com.keepbooking.review.model.Review;
import com.keepbooking.review.repository.ReviewRepository;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.repository.UserRepository;

/**
 * "Отзыв можно оставить только после брони со статусом COMPLETED, и не более одного
 * отзыва на бронирование" (tz2.txt §13) + rating/reviewsCount denormalization on write.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantService restaurantService;
    @Mock
    private UserRepository userRepository;

    private ReviewService reviewService;

    private static final Long USER_ID = 1L;
    private static final Long BOOKING_ID = 100L;
    private static final Long RESTAURANT_OWNER_ID = 300L;
    private static final Long COMPANY_ID = 1L;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository, bookingRepository, restaurantRepository, restaurantService, new AccessControlService(userRepository));
    }

    private void stubOwner() {
        when(userRepository.findById(RESTAURANT_OWNER_ID)).thenReturn(Optional.of(
                User.builder().id(RESTAURANT_OWNER_ID).role(UserRole.ROLE_COMPANY_ADMIN).companyId(COMPANY_ID).build()));
    }

    private User user() {
        return User.builder().id(USER_ID).firstname("Test").lastname("User").build();
    }

    private Restaurant restaurant() {
        Company company = Company.builder().id(COMPANY_ID).name("Test Co").build();
        return Restaurant.builder().id(1L).name("Test Restaurant").company(company)
                .rating(BigDecimal.ZERO).reviewsCount(0).build();
    }

    private Booking completedBooking() {
        return Booking.builder().id(BOOKING_ID).user(user()).restaurant(restaurant()).status(BookingStatus.COMPLETED).build();
    }

    private CreateReviewRequest request(int rating) {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setBookingId(BOOKING_ID);
        request.setRating(rating);
        request.setComment("Great!");
        return request;
    }

    @Test
    void createThrowsWhenBookingNotFound() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(USER_ID, request(5)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOKING_NOT_FOUND);
    }

    @Test
    void createThrowsWhenActorIsNotTheBookingOwner() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(completedBooking()));

        assertThatThrownBy(() -> reviewService.create(999L, request(5)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void createThrowsWhenBookingIsNotCompleted() {
        Booking pending = Booking.builder().id(BOOKING_ID).user(user()).restaurant(restaurant())
                .status(BookingStatus.PENDING).build();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request(5)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_BOOKING_NOT_COMPLETED);
    }

    @Test
    void createThrowsWhenReviewAlreadyExistsForBooking() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(completedBooking()));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(USER_ID, request(5)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createSavesReviewAndRecalculatesRestaurantRating() {
        Booking booking = completedBooking();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(restaurantRepository.findById(booking.getRestaurant().getId())).thenReturn(Optional.of(booking.getRestaurant()));
        when(reviewRepository.findAverageRating(booking.getRestaurant().getId())).thenReturn(Optional.of(new BigDecimal("5.0")));
        when(reviewRepository.countByRestaurantId(booking.getRestaurant().getId())).thenReturn(1L);

        ReviewDto dto = reviewService.create(USER_ID, request(5));

        assertThat(dto.getRating()).isEqualTo(5);
        assertThat(booking.getRestaurant().getRating()).isEqualByComparingTo("5.00");
        assertThat(booking.getRestaurant().getReviewsCount()).isEqualTo(1);
        verify(restaurantRepository).save(booking.getRestaurant());
        verify(restaurantService).evictCaches(booking.getRestaurant().getId());
    }

    @Test
    void deleteThrowsWhenReviewNotFound() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.delete(1L))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void deleteRemovesReviewAndRecalculatesRestaurantRatingToZeroWhenLastOneRemoved() {
        Restaurant restaurant = restaurant();
        Review review = Review.builder().id(1L).restaurant(restaurant).user(user()).rating(5).build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));
        when(reviewRepository.findAverageRating(restaurant.getId())).thenReturn(Optional.empty());
        when(reviewRepository.countByRestaurantId(restaurant.getId())).thenReturn(0L);

        reviewService.delete(1L);

        verify(reviewRepository).delete(review);
        assertThat(restaurant.getRating()).isEqualByComparingTo("0.00");
        assertThat(restaurant.getReviewsCount()).isZero();
        verify(restaurantService).evictCaches(restaurant.getId());
    }

    @Test
    void replyThrowsWhenReviewNotFound() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());

        ReplyToReviewRequest request = new ReplyToReviewRequest();
        request.setReply("Thanks for visiting!");

        assertThatThrownBy(() -> reviewService.reply(RESTAURANT_OWNER_ID, 1L, request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void replyThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        Review review = Review.builder().id(1L).restaurant(restaurant()).user(user()).rating(5).build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        ReplyToReviewRequest request = new ReplyToReviewRequest();
        request.setReply("Thanks for visiting!");

        assertThatThrownBy(() -> reviewService.reply(999L, 1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void replySetsOwnerReplyAndOwnerReplyAtForTheOwner() {
        stubOwner();
        Review review = Review.builder().id(1L).restaurant(restaurant()).user(user()).booking(completedBooking()).rating(5).build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReplyToReviewRequest request = new ReplyToReviewRequest();
        request.setReply("Thanks for visiting!");

        ReviewDto dto = reviewService.reply(RESTAURANT_OWNER_ID, 1L, request);

        assertThat(dto.getOwnerReply()).isEqualTo("Thanks for visiting!");
        assertThat(dto.getOwnerReplyAt()).isNotNull();
    }

    @Test
    void getByRestaurantForOwnerThrowsWhenRestaurantNotFound() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getByRestaurantForOwner(RESTAURANT_OWNER_ID, 1L, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void getByRestaurantForOwnerThrowsAccessDeniedWhenActorDoesNotOwnTheRestaurant() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant()));

        assertThatThrownBy(() -> reviewService.getByRestaurantForOwner(999L, 1L, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByRestaurantForOwnerReturnsReviewsForTheOwner() {
        stubOwner();
        Restaurant restaurant = restaurant();
        Review review = Review.builder().id(1L).restaurant(restaurant).user(user()).booking(completedBooking()).rating(5).build();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(review)));

        var result = reviewService.getByRestaurantForOwner(RESTAURANT_OWNER_ID, 1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ReviewDto::getId).containsExactly(1L);
    }
}
