package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StorageQuotaServiceImpl implements StorageQuotaService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Override
    public long getUserTotalQuota(User user) {
        long base = (user.getPlan() != null) ? user.getPlan().getQuotaBytes() : 0L;
        long bonus = (user.getBonusStorageBytes() != null) ? user.getBonusStorageBytes() : 0L;
        return base + bonus;
    }

    @Override
    public long getGroupTotalQuota(Group group) {
        long base = (group.getPlan() != null) ? group.getPlan().getQuotaBytes() : 0L;
        long bonus = (group.getBonusStorageBytes() != null) ? group.getBonusStorageBytes() : 0L;
        return base + bonus;
    }

    @Override
    public void ensureUserCanUpload(User user, long fileSize) {
        long used = nz(user.getStorageUsed());
        long total = getUserTotalQuota(user);
        if (used + fileSize > total) {
            log.warn("User {} quota exceeded: used={}, file={}, total={}",
                    user.getId(), used, fileSize, total);
            throw new CustomException(ErrorCode.USER_STORAGE_QUOTA_EXCEEDED);
        }
    }

    @Override
    public void ensureGroupCanUpload(Group group, long fileSize) {
        long used = nz(group.getStorageUsed());
        long total = getGroupTotalQuota(group);
        if (used + fileSize > total) {
            log.warn("Group {} quota exceeded: used={}, file={}, total={}",
                    group.getId(), used, fileSize, total);
            throw new CustomException(ErrorCode.GROUP_STORAGE_QUOTA_EXCEEDED);
        }
    }

    @Override
    public void incrementUserUsage(User user, long size) {
        user.setStorageUsed(nz(user.getStorageUsed()) + size);
        userRepository.save(user);
    }

    @Override
    public void decrementUserUsage(User user, long size) {
        long next = nz(user.getStorageUsed()) - size;
        user.setStorageUsed(Math.max(0L, next));
        userRepository.save(user);
    }

    @Override
    public void incrementGroupUsage(Group group, long size) {
        group.setStorageUsed(nz(group.getStorageUsed()) + size);
        groupRepository.save(group);
    }

    @Override
    public void decrementGroupUsage(Group group, long size) {
        long next = nz(group.getStorageUsed()) - size;
        group.setStorageUsed(Math.max(0L, next));
        groupRepository.save(group);
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }
}
