package com.example.fileshareR.enums;

/**
 * Trạng thái kiểm duyệt tài liệu trong nhóm.
 * APPROVED  — đã duyệt (mặc định cho tài liệu cá nhân + tài liệu admin/owner upload)
 * PENDING   — chờ admin/owner nhóm duyệt sau khi engine flag
 * REJECTED  — bị admin từ chối
 */
public enum ModerationStatus {
    APPROVED,
    PENDING,
    REJECTED
}
