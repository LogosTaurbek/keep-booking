package com.keepbooking.restaurant.dto;

import com.keepbooking.restaurant.model.TableStatus;
import com.keepbooking.restaurant.model.TableType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTableRequest {

    @Size(max = 20)
    private String number;

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

    private TableStatus status;
}
