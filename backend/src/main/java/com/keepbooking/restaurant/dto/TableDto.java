package com.keepbooking.restaurant.dto;

import com.keepbooking.restaurant.model.TableStatus;
import com.keepbooking.restaurant.model.TableType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableDto {
    private Long id;
    private Long hallId;
    private String number;
    private Integer capacity;
    private Integer minCapacity;
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
