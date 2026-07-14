package com.keepbooking.user.dto;

import java.time.Instant;

import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.model.UserStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDto {

    private Long id;
    private String firstname;
    private String lastname;
    private String phone;
    private String email;
    private String avatarUrl;
    private String language;
    private String timezone;
    private UserStatus status;
    private Boolean emailVerified;
    private UserRole role;
    private Long companyId;
    private Long restaurantId;
    private Long cityId;
    private String cityName;
    private Long countryId;
    private String countryName;
    private Instant createdAt;
}
