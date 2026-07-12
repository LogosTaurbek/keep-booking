package com.keepbooking.restaurant.dto;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRestaurantRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 500)
    private String address;

    private Long cityId;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Size(max = 50)
    private String timezone;

    private Integer avgCheck;
    private Set<Long> cuisineIds;
}
