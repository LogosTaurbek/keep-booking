package com.keepbooking.restaurant.model;

import com.keepbooking.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "restaurant_tables",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hall_id", "number"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private Integer capacity;

    private Integer minCapacity;

    @Column(nullable = false)
    @Builder.Default
    private String shape = "RECT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TableType type = TableType.REGULAR;

    // Позиция на схеме зала
    @Column(name = "pos_x", nullable = false) @Builder.Default private Integer posX = 0;
    @Column(name = "pos_y", nullable = false) @Builder.Default private Integer posY = 0;
    @Column(nullable = false) @Builder.Default private Integer width = 80;
    @Column(nullable = false) @Builder.Default private Integer height = 80;
    @Column(nullable = false) @Builder.Default private Integer rotation = 0;

    @Column(nullable = false) @Builder.Default private Boolean isVip = false;
    @Column(nullable = false) @Builder.Default private Boolean isSofa = false;
    @Column(nullable = false) @Builder.Default private Boolean nearWindow = false;
    @Column(nullable = false) @Builder.Default private Boolean hasSocket = false;
    @Column(nullable = false) @Builder.Default private Boolean isSmoking = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TableStatus status = TableStatus.ACTIVE;
}
