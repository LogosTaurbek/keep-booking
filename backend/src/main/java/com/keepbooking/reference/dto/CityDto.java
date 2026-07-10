package com.keepbooking.reference.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CityDto implements Serializable {
    private Long id;
    private Long countryId;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;
}
