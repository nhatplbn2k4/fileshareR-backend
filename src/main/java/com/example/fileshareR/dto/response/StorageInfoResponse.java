package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageInfoResponse {
    private PlanResponse plan;
    private Long storageUsed;
    private Long bonusStorageBytes;
    private Long quotaBytes;      // plan.quotaBytes
    private Long totalQuotaBytes; // plan.quotaBytes + bonusStorageBytes
}
