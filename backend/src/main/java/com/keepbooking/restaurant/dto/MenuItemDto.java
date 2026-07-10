package com.keepbooking.restaurant.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuItemDto {
    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private String photoUrl;
    private Boolean isAvailable;
    private Integer position;
}
