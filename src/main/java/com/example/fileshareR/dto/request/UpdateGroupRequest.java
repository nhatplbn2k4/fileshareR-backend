package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.GroupVisibilityType;
import jakarta.validation.constraints.Min;
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

    private String coverImageUrl;

    private Long coverPresetId;

    private Boolean requireApproval;

    private List<String> joinQuestions;

    /**
     * Dung lượng (bytes) cấp cho nhóm. Khi đặt lại: không được thấp hơn dung lượng nhóm đã
     * dùng và không vượt quá dung lượng khả dụng của owner. Null = giữ nguyên.
     */
    @Min(value = 0, message = "Dung lượng cấp cho nhóm không được âm")
    private Long allocatedQuotaBytes;
}
