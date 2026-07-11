package com.keepbooking.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemStatsDto {
    private long totalUsers;
    private long activeUsers;
    private long blockedUsers;

    private long totalCompanies;
    private long activeCompanies;
    private long pendingModerationCompanies;

    private long totalRestaurants;
    private long activeRestaurants;
    private long pendingModerationRestaurants;

    private long totalBookings;
    private long completedBookings;
    private long cancelledBookings;
}
