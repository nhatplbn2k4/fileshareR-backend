package com.example.fileshareR.service;

import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.User;

public interface StorageQuotaService {

    /** Tổng quota của user (plan quota + bonus). */
    long getUserTotalQuota(User user);

    /**
     * Quota cá nhân còn khả dụng của user = tổng quota − đã dùng cá nhân
     * − tổng dung lượng đã cấp phát cho các nhóm mình sở hữu.
     * Có thể trả về số âm (khi dữ liệu cũ vượt mức); caller tự xử lý.
     */
    long getUserAvailableQuota(User user);

    /** Tổng quota của group (plan quota + bonus + dung lượng owner cấp phát). */
    long getGroupTotalQuota(Group group);

    /** Throw nếu user upload sẽ vượt quota. */
    void ensureUserCanUpload(User user, long fileSize);

    /** Throw nếu group upload sẽ vượt quota. */
    void ensureGroupCanUpload(Group group, long fileSize);

    void incrementUserUsage(User user, long size);

    void decrementUserUsage(User user, long size);

    void incrementGroupUsage(Group group, long size);

    void decrementGroupUsage(Group group, long size);
}
