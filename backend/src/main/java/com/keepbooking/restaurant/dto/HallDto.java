package com.keepbooking.restaurant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HallDto {
    private Long id;
    private Long restaurantId;
    private String name;
    private Integer floor;
    private Integer canvasWidth;
    private Integer canvasHeight;
}
