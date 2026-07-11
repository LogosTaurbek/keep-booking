package com.keepbooking.history.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchHistoryDto {
    private Long id;
    private String name;
    private String cuisineSlug;
    private Long cityId;
    private BigDecimal minRating;
    private Instant createdAt;
}
