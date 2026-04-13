package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.GroupMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private GroupMemberRole role;
    private LocalDateTime joinedAt;
    private Boolean isUploadBanned; // đang bị cấm upload không
    private LocalDateTime banExpiresAt; // null nếu không bị ban hoặc bị kick
}
