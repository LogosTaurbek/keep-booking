package com.keepbooking.analytics.model;

import java.time.LocalDate;

import com.keepbooking.common.model.BaseEntity;
import com.keepbooking.restaurant.model.Restaurant;

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

/** Per-restaurant-per-day booking count by hour-of-day (tz2.txt §15 "популярные часы"). */
@Entity
@Table(name = "restaurant_daily_hour_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "stat_date", "hour_of_day"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDailyHourStats extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "hour_of_day", nullable = false)
    private Integer hourOfDay;

    @Column(name = "booking_count", nullable = false)
    @Builder.Default
    private Integer bookingCount = 0;
}
