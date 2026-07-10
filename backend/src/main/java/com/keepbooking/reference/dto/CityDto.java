package com.keepbooking.reference.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CityDto {
    private Long id;
    private Long countryId;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
}
