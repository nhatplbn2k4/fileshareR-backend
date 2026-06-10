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
    private Long quotaBytes;          // plan.quotaBytes
    private Long totalQuotaBytes;     // tổng quota khả dụng (plan + bonus [+ allocated nếu là nhóm])
    private Long allocatedQuotaBytes; // [nhóm] dung lượng owner cấp cho nhóm; [user] tổng đã cấp cho các nhóm
    private Long availableQuotaBytes; // [user] còn lại có thể dùng/cấp = total − used − đã cấp cho nhóm
}
