package com.keepbooking.reference.dto;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CountryDto implements Serializable {
    private Long id;
    private String name;
    private String code;
}
