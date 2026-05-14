package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateGroupFolderRequest;
import com.example.fileshareR.dto.response.GroupFolderResponse;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupFolder;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupFolderRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.GroupFolderService;
import com.example.fileshareR.service.StorageQuotaService;
import com.example.fileshareR.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupFolderServiceImpl implements GroupFolderService {

    private final GroupFolderRepository groupFolderRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;
    private final DocumentRepository documentRepository;
    private final StorageQuotaService storageQuotaService;

    @Override
    public GroupFolderResponse createFolder(Long groupId, CreateGroupFolderRequest request, Long userId) {
        log.info("User {} creating folder '{}' in group {}", userId, request.getName(), groupId);

        Group group = getGroupById(groupId);
        requireAdminOrOwner(groupId, userId);

        GroupFolder parent = null;
        if (request.getParentId() != null) {
            parent = groupFolderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND));
            // Đảm bảo parent thuộc cùng nhóm
            if (!parent.getGroup().getId().equals(groupId)) {
                throw new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND);
            }
        }

        User creator = userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        GroupFolder folder = GroupFolder.builder()
                .group(group)
                .createdBy(creator)
                .parent(parent)
                .name(request.getName())
                // Nếu group PUBLIC, auto gen share token; PRIVATE thì để null
                .shareToken(group.getVisibility() == GroupVisibilityType.PUBLIC
                        ? UUID.randomUUID().toString()
                        : null)
                .build();

        folder = groupFolderRepository.save(folder);
        log.info("GroupFolder {} created in group {}", folder.getId(), groupId);
        return mapToResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupFolderResponse> getGroupFolders(Long groupId, Long requesterId) {
        checkGroupAccess(groupId, requesterId);
        return groupFolderRepository.findByGroupId(groupId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupFolderResponse> getRootFolders(Long groupId, Long requesterId) {
        checkGroupAccess(groupId, requesterId);
        return groupFolderRepository.findByGroupIdAndParentIsNull(groupId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupFolderResponse> getSubFolders(Long groupId, Long parentFolderId, Long requesterId) {
        checkGroupAccess(groupId, requesterId);
        return groupFolderRepository.findByGroupIdAndParentId(groupId, parentFolderId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteFolder(Long groupId, Long folderId, Long userId) {
        log.info("User {} deleting folder {} in group {}", userId, folderId, groupId);

        getGroupById(groupId);
        requireAdminOrOwner(groupId, userId);

        GroupFolder folder = groupFolderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND));

        if (!folder.getGroup().getId().equals(groupId)) {
            throw new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND);
        }

        Long freed = documentRepository.sumFileSizeInGroupFolderTree(folderId);
        long freedBytes = freed != null ? freed : 0L;

        groupFolderRepository.delete(folder);

        if (freedBytes > 0) {
            storageQuotaService.decrementGroupUsage(folder.getGroup(), freedBytes);
        }
        log.info("GroupFolder {} deleted from group {} (freed {} bytes)", folderId, groupId, freedBytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Group getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
    }

    private void requireAdminOrOwner(Long groupId, Long userId) {
        var member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getRole() == GroupMemberRole.MEMBER) {
            throw new CustomException(ErrorCode.GROUP_ADMIN_REQUIRED);
        }
    }

    /** Kiểm tra quyền xem nhóm: PUBLIC = ai cũng xem được, PRIVATE = chỉ thành viên */
    private void checkGroupAccess(Long groupId, Long requesterId) {
        Group group = getGroupById(groupId);
        if (group.getVisibility() == GroupVisibilityType.PRIVATE) {
            if (requesterId == null || !groupMemberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
                throw new CustomException(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Share-by-token
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public GroupFolderResponse getByShareToken(String shareToken) {
        log.info("Getting group folder by share token {}", shareToken);
        GroupFolder folder = groupFolderRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND));
        enforceGroupIsPublic(folder);
        return mapToResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupFolderResponse> getSubFoldersByShareToken(String shareToken) {
        log.info("Getting sub-folders by group folder share token {}", shareToken);
        GroupFolder parent = groupFolderRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND));
        enforceGroupIsPublic(parent);
        return groupFolderRepository.findByGroupIdAndParentId(parent.getGroup().getId(), parent.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public GroupFolderResponse rotateShareToken(Long folderId, Long userId) {
        log.info("User {} rotating group folder share token {}", userId, folderId);
        GroupFolder folder = groupFolderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND));

        requireAdminOrOwner(folder.getGroup().getId(), userId);

        if (folder.getGroup().getVisibility() != GroupVisibilityType.PUBLIC) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Không thể tạo link chia sẻ khi nhóm đang ở chế độ riêng tư.");
        }

        folder.setShareToken(UUID.randomUUID().toString());
        folder = groupFolderRepository.save(folder);
        return mapToResponse(folder);
    }

    @Override
    public void syncShareTokensWithGroupVisibility(Long groupId) {
        Group group = getGroupById(groupId);
        List<GroupFolder> folders = groupFolderRepository.findByGroupId(groupId);
        boolean shouldHaveToken = group.getVisibility() == GroupVisibilityType.PUBLIC;

        for (GroupFolder f : folders) {
            if (shouldHaveToken && f.getShareToken() == null) {
                f.setShareToken(UUID.randomUUID().toString());
                groupFolderRepository.save(f);
            } else if (!shouldHaveToken && f.getShareToken() != null) {
                f.setShareToken(null);
                groupFolderRepository.save(f);
            }
        }
        log.info("Synced share tokens for {} group folders (group={}, public={})",
                folders.size(), groupId, shouldHaveToken);
    }

    /**
     * Nếu group của folder không còn PUBLIC → coi như folder không tồn tại (404).
     * Dùng cho share-by-token để bảo vệ content cũ khi group bị khóa.
     */
    private void enforceGroupIsPublic(GroupFolder folder) {
        if (folder.getGroup().getVisibility() != GroupVisibilityType.PUBLIC) {
            throw new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND);
        }
    }

    private GroupFolderResponse mapToResponse(GroupFolder folder) {
        return GroupFolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .groupId(folder.getGroup().getId())
                .groupName(folder.getGroup().getName())
                .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .parentName(folder.getParent() != null ? folder.getParent().getName() : null)
                .createdById(folder.getCreatedBy().getId())
                .createdByName(folder.getCreatedBy().getFullName())
                .shareToken(folder.getShareToken())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
