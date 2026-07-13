package com.keepbooking.review.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.booking.model.Booking;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.restaurant.service.RestaurantService;
import com.keepbooking.review.dto.CreateReviewRequest;
import com.keepbooking.review.dto.ReplyToReviewRequest;
import com.keepbooking.review.dto.ReviewDto;
import com.keepbooking.review.model.Review;
import com.keepbooking.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public ReviewDto create(Long userId, CreateReviewRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ApiException(ErrorCode.REVIEW_BOOKING_NOT_COMPLETED);
        }
        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new ApiException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .restaurant(booking.getRestaurant())
                .user(booking.getUser())
                .booking(booking)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        review = reviewRepository.save(review);
        recalculateRestaurantRating(booking.getRestaurant().getId());

        return toDto(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getByRestaurant(Long restaurantId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getByRestaurantForOwner(Long userId, Long restaurantId, Pageable pageable) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));
        if (!restaurant.getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }
        Page<Review> page = reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional
    public ReviewDto reply(Long userId, Long reviewId, ReplyToReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.getRestaurant().getCompany().getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You don't own this restaurant");
        }

        review.setOwnerReply(request.getReply());
        review.setOwnerReplyAt(Instant.now());

        return toDto(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getMyReviews(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional
    public void delete(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCode.REVIEW_NOT_FOUND));
        Long restaurantId = review.getRestaurant().getId();
        reviewRepository.delete(review);
        recalculateRestaurantRating(restaurantId);
    }

    private void recalculateRestaurantRating(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESTAURANT_NOT_FOUND));

        BigDecimal average = reviewRepository.findAverageRating(restaurantId)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        long count = reviewRepository.countByRestaurantId(restaurantId);

        restaurant.setRating(average);
        restaurant.setReviewsCount((int) count);
        restaurantRepository.save(restaurant);
        restaurantService.evictCaches(restaurantId);
    }

    private ReviewDto toDto(Review r) {
        return ReviewDto.builder()
                .id(r.getId())
                .restaurantId(r.getRestaurant().getId())
                .userId(r.getUser().getId())
                .userName(r.getUser().getFirstname() + " " + r.getUser().getLastname())
                .bookingId(r.getBooking().getId())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .ownerReply(r.getOwnerReply())
                .ownerReplyAt(r.getOwnerReplyAt())
                .build();
    }
}
