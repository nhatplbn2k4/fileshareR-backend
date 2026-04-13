package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.VisibilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadGroupDocumentRequest {

    @NotBlank(message = "Tiêu đề tài liệu không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    private Long groupFolderId; // null = không thuộc thư mục nào trong nhóm

    @Builder.Default
    private VisibilityType visibility = VisibilityType.PUBLIC; // Mặc định public trong nhóm
}
