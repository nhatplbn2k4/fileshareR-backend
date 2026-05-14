package com.example.fileshareR.service;

import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.User;

public interface StorageQuotaService {

    /** Tổng quota của user (plan quota + bonus). */
    long getUserTotalQuota(User user);

    /** Tổng quota của group (plan quota + bonus). */
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
