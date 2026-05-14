package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.enums.VisibilityType;
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
public class DocumentResponse {
    private Long id;
    private String title;
    private String fileName;
    private FileType fileType;
    private Long fileSize;
    private String fileUrl;
    private VisibilityType visibility;
    private String summary;
    private List<String> keywords;
    private Integer downloadCount;

    // User info
    private Long userId;
    private String userName;

    // Folder info
    private Long folderId;
    private String folderName;

    // Group info (null nếu không thuộc nhóm)
    private Long groupId;
    private String groupName;

    // Moderation (chỉ relevant cho tài liệu nhóm; tài liệu cá nhân luôn APPROVED)
    private ModerationStatus moderationStatus;
    private String moderationReason;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
