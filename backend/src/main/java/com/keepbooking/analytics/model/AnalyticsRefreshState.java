package com.keepbooking.analytics.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-row watermark for {@code AnalyticsRefreshWorker}: only bookings written since this
 * timestamp are re-aggregated on the next cycle, instead of rescanning the whole table.
 */
@Entity
@Table(name = "analytics_refresh_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsRefreshState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;
}
