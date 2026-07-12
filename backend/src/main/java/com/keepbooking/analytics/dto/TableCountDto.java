package com.keepbooking.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableCountDto {
    private Long tableId;
    private String tableNumber;
    private long count;
}
