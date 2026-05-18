package com.example.fileshareR.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolvePlagiarismRequest {

    public enum Action {
        KEEP,        // Giữ tài liệu, đánh dấu report là RESOLVED_KEPT (false positive)
        REMOVE,      // Xóa tài liệu + cảnh báo user + có thể auto-ban
        PRIVATIZE,   // Ép tài liệu/folder về PRIVATE (hoặc gỡ khỏi group)
        IGNORE       // Bỏ qua, đánh dấu IGNORED, không cảnh báo user
    }

    @NotNull
    private Action action;

    private String note;
}
