package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.GroupVisibilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO cho landing page khi user click vào invite link /shared/group/{token}.
 * Chứa thông tin public của nhóm + trạng thái member của user hiện tại (nếu đã login).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupLandingResponse {
    private Long id;
    private String name;
    private String description;
    private GroupVisibilityType visibility;
    private String avatarUrl;
    private String ownerName;
    private Long memberCount;
    private Boolean isMember;
    private Boolean requireApproval;
    private List<String> joinQuestions;
    private Boolean hasPendingRequest;
}
