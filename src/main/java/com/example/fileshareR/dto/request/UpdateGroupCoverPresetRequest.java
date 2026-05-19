package com.example.fileshareR.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupCoverPresetRequest {

    @Size(max = 255, message = "Tên không được vượt quá 255 ký tự")
    private String name;

    private Boolean isActive;

    private Integer displayOrder;
}
