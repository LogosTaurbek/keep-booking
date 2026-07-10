package com.keepbooking.restaurant.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMenuItemRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    @Size(max = 100)
    private String category;

    @Size(max = 500)
    private String photoUrl;

    private Boolean isAvailable;

    private Integer position;
}
