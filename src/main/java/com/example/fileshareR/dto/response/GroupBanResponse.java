package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.BanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupBanResponse {
    private Long id;
    private Long groupId;
    private Long userId;
    private String userName;
    private String userEmail;
    private BanType banType;
    private String reason;
    private LocalDateTime expiresAt;
    private Long bannedById;
    private String bannedByName;
    private Boolean active;
    private LocalDateTime createdAt;
}
