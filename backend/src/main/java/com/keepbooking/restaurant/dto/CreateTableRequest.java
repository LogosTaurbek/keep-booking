package com.keepbooking.restaurant.dto;

import com.keepbooking.restaurant.model.TableType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTableRequest {

    @NotNull
    private Long hallId;

    @NotBlank
    @Size(max = 20)
    private String number;

    @NotNull
    @Min(1)
    private Integer capacity;

    @Min(1)
    private Integer minCapacity;

    @Size(max = 20)
    private String shape;

    private TableType type;

    private Integer posX;
    private Integer posY;
    private Integer width;
    private Integer height;
    private Integer rotation;

    private Boolean isVip;
    private Boolean isSofa;
    private Boolean nearWindow;
    private Boolean hasSocket;
    private Boolean isSmoking;
}
