package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.FolderVisibilityType;
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
public class CreateFolderRequest {
    @NotBlank(message = "Tên thư mục không được để trống")
    @Size(max = 255, message = "Tên thư mục không được vượt quá 255 ký tự")
    private String name;

    private Long parentId; // null nếu là thư mục gốc

    private FolderVisibilityType visibility; // null → mặc định PUBLIC
}
