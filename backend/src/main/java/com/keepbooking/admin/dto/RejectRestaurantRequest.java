package com.keepbooking.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectRestaurantRequest {

    @NotBlank
    @Size(max = 1000)
    private String reason;
}
