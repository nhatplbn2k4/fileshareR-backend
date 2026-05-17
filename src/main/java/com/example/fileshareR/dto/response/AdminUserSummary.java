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
public class AdminUserSummary {
    private Long id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String authProvider;
    private Boolean isActive;
    private Boolean emailVerified;
    private String planCode;
    private String planName;
    private Long storageUsed;
    private Long totalQuotaBytes;
    private LocalDateTime createdAt;
}
