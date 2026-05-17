package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGroupSummary {
    private Long id;
    private String name;
    private String description;
    private String visibility;
    private String avatarUrl;
    private Boolean requireApproval;

    private Long ownerId;
    private String ownerEmail;
    private String ownerFullName;

    private String planCode;
    private Long storageUsed;
    private Long totalQuotaBytes;

    private long memberCount;
    private long documentCount;

    private LocalDateTime createdAt;
}
