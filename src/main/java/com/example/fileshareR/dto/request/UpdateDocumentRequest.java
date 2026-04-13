package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.VisibilityType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private Long folderId; // Có thể null để xóa khỏi folder

    private VisibilityType visibility;
}
