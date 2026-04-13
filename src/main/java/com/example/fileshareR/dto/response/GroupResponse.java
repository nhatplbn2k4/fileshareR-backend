package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private GroupVisibilityType visibility;
    private Long ownerId;
    private String ownerName;
    private String avatarUrl;
    private Long memberCount;
    private Boolean isMember;       // user hiện tại có là thành viên không
    private GroupMemberRole myRole; // vai trò của user hiện tại (null nếu không là thành viên)
    private String shareToken;      // invite token — member nào cũng có thể copy để share
    private Boolean requireApproval;
    private List<String> joinQuestions;
    private Boolean hasPendingRequest; // user hiện tại có request PENDING không
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
