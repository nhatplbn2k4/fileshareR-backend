package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.GroupVisibilityType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupRequest {

    @Size(max = 255, message = "Tên nhóm không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    private GroupVisibilityType visibility;

    private String avatarUrl;

    private Boolean requireApproval;

    private List<String> joinQuestions;
}
