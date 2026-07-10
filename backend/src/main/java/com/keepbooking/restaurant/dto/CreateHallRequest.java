package com.keepbooking.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateHallRequest {

    @NotNull
    private Long restaurantId;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Positive
    private Integer floor;

    @Positive
    private Integer canvasWidth;

    @Positive
    private Integer canvasHeight;
}
