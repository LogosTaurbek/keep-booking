package com.keepbooking.reference.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CuisineDto {
    private Long id;
    private String name;
    private String slug;
}
