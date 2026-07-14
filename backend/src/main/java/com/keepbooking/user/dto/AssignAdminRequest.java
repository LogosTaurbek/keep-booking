package com.keepbooking.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignAdminRequest {

    @NotBlank
    @Email
    private String email;
}
