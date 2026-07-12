package com.keepbooking.restaurant.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

import com.keepbooking.restaurant.model.RestaurantStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RestaurantDto implements Serializable {
    private Long id;
    private Long companyId;
    private String name;
    private String description;
    private String address;
    private Long cityId;
    private String cityName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
    private BigDecimal rating;
    private Integer reviewsCount;
    private Integer avgCheck;
    private RestaurantStatus status;
    private Set<String> cuisineSlugs;
}
