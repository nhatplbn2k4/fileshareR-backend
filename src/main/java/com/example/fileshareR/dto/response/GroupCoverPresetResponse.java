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
public class GroupCoverPresetResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private Boolean isActive;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
