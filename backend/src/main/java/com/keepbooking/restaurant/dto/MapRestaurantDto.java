package com.keepbooking.restaurant.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import com.keepbooking.restaurant.model.RestaurantStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapRestaurantDto implements Serializable {
    private Long id;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private RestaurantStatus status;
    private BigDecimal rating;
    private boolean hasFreeTablesNow;
}
