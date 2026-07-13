package com.keepbooking.review.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewDto {
    private Long id;
    private Long restaurantId;
    private Long userId;
    private String userName;
    private Long bookingId;
    private Integer rating;
    private String comment;
    private Instant createdAt;
    private String ownerReply;
    private Instant ownerReplyAt;
}
