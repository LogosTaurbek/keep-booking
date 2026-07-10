package com.keepbooking.restaurant.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchUpdateTablesRequest {

    @NotNull
    private Long hallId;

    @NotEmpty
    @Valid
    private List<TablePositionItem> tables;
}
