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
public class GroupFolderResponse {
    private Long id;
    private String name;
    private Long groupId;
    private String groupName;
    private Long parentId;
    private String parentName;
    private Long createdById;
    private String createdByName;
    private String shareToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
