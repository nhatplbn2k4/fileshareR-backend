package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.GroupVisibilityType;
import jakarta.validation.constraints.Min;
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
public class CreateGroupRequest {

    @NotBlank(message = "Tên nhóm không được để trống")
    @Size(max = 255, message = "Tên nhóm không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @Builder.Default
    private GroupVisibilityType visibility = GroupVisibilityType.PRIVATE;

    /** Optional: id of a system preset OR custom uploaded URL via uploadCustomCover before create. */
    private Long coverPresetId;

    /** Optional: custom cover URL (already uploaded). If both set, this wins. */
    private String coverImageUrl;

    /**
     * Dung lượng (bytes) chủ nhóm cấp cho nhóm, lấy từ quota cá nhân của mình.
     * Null/0 = nhóm chưa có dung lượng (thành viên chưa upload được cho tới khi owner cấp).
     */
    @Min(value = 0, message = "Dung lượng cấp cho nhóm không được âm")
    private Long allocatedQuotaBytes;
}
