package com.keepbooking.restaurant.model;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "restaurant_working_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "day_of_week"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private Integer dayOfWeek; // 1=Mon … 7=Sun

    private LocalTime openTime;
    private LocalTime closeTime;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDayOff = false;
}
