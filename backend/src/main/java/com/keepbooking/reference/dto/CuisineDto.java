package com.keepbooking.reference.dto;

import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CuisineDto implements Serializable {
    private Long id;
    private String name;
    private String slug;
}
