package com.keepbooking.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.analytics.model.AnalyticsRefreshState;

public interface AnalyticsRefreshStateRepository extends JpaRepository<AnalyticsRefreshState, Long> {
}
