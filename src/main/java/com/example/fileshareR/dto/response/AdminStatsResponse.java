package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long premiumUsers;
    private long activeUsers;
    private long signupsToday;

    private long totalDocuments;
    private long totalGroups;

    private long totalStorageUsedBytes;
    private long totalStorageQuotaBytes;

    private long totalRevenueVnd;
    private long revenueLast30dVnd;
    private long successPaymentsCount;
    private long pendingPaymentsCount;
}
