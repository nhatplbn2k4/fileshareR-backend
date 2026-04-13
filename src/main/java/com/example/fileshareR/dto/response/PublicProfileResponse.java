package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProfileResponse {
    private Long id;
    private String fullName;
    private String avatarUrl;
    private UserRole role;
    private Long publicDocumentCount;
    private Long publicFolderCount;
    private LocalDateTime createdAt;
}
