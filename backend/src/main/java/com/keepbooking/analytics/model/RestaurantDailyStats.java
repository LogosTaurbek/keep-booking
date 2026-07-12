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

/**
 * Per-restaurant-per-day booking-status rollup (tz2.txt §15/§25 read model), kept fresh by
 * {@code AnalyticsRefreshWorker} instead of being computed from the live {@code bookings} table
 * on every analytics read.
 */
@Entity
@Table(name = "restaurant_daily_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "stat_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDailyStats extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "pending_count", nullable = false)
    @Builder.Default
    private Integer pendingCount = 0;

    @Column(name = "confirmed_count", nullable = false)
    @Builder.Default
    private Integer confirmedCount = 0;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private Integer rejectedCount = 0;

    @Column(name = "cancelled_count", nullable = false)
    @Builder.Default
    private Integer cancelledCount = 0;

    @Column(name = "completed_count", nullable = false)
    @Builder.Default
    private Integer completedCount = 0;

    @Column(name = "no_show_count", nullable = false)
    @Builder.Default
    private Integer noShowCount = 0;
}
