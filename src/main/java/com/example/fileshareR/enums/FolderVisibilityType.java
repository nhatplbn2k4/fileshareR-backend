package com.example.fileshareR.enums;

/**
 * Chế độ hiển thị của thư mục cá nhân.
 * PUBLIC    → xuất hiện trong kết quả tìm kiếm (mọi người xem được)
 * PRIVATE   → chỉ chủ sở hữu mới xem được, KHÔNG xuất hiện khi tìm kiếm
 * LINK_ONLY → chỉ người có link trực tiếp mới xem được, KHÔNG xuất hiện khi tìm kiếm
 */
public enum FolderVisibilityType {
    PUBLIC,
    PRIVATE,
    LINK_ONLY
}
