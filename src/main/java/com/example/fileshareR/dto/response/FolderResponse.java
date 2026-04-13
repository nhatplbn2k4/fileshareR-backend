package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.FolderVisibilityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponse {
    private Long id;
    private String name;
    private Long userId;
    private Long parentId;
    private String parentName;
    private FolderVisibilityType visibility;
    private String shareToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
