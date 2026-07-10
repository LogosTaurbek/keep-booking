package com.keepbooking.restaurant.dto;

import com.keepbooking.restaurant.model.CompanyStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyDto {
    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private String website;
    private String phone;
    private String email;
    private CompanyStatus status;
}
