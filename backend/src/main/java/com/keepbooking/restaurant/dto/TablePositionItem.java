package com.keepbooking.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TablePositionItem {

    @NotNull
    private Long id;

    @NotNull
    private Integer posX;

    @NotNull
    private Integer posY;

    @NotNull
    private Integer width;

    @NotNull
    private Integer height;

    @NotNull
    private Integer rotation;
}
