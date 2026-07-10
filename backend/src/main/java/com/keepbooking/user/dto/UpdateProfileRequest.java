package com.keepbooking.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 100)
    private String firstname;

    @Size(min = 2, max = 100)
    private String lastname;

    @Size(max = 20)
    private String phone;

    @Size(max = 10)
    private String language;

    @Size(max = 50)
    private String timezone;

    private Long cityId;
}
