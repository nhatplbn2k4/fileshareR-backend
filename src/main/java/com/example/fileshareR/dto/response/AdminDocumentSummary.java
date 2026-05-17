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
public class AdminDocumentSummary {
    private Long id;
    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String visibility;
    private String moderationStatus;
    private Integer downloadCount;
    private Long ownerId;
    private String ownerEmail;
    private String ownerFullName;
    private Long groupId;
    private String groupName;
    private LocalDateTime createdAt;
}
