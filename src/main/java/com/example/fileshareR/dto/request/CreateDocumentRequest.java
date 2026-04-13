package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.VisibilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDocumentRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private Long folderId; // Có thể null nếu không thuộc folder nào

    @NotNull(message = "Visibility không được để trống")
    private VisibilityType visibility = VisibilityType.PRIVATE;
}
