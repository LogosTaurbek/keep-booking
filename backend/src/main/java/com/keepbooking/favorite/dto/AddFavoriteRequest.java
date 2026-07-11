package com.keepbooking.favorite.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddFavoriteRequest {

    @NotNull
    private Long restaurantId;
}
