package com.keepbooking.restaurant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RestaurantPhotoDto {
    private Long id;
    private Long restaurantId;
    private String url;
    private Integer position;
}
