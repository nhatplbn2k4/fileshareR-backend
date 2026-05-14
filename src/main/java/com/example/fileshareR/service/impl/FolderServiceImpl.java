package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateFolderRequest;
import com.example.fileshareR.dto.request.UpdateFolderRequest;
import com.example.fileshareR.dto.response.FolderResponse;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.service.FolderService;
import com.example.fileshareR.service.StorageQuotaService;
import com.example.fileshareR.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final UserService userService;
    private final DocumentRepository documentRepository;
    private final StorageQuotaService storageQuotaService;

    @Override
    public FolderResponse createFolder(CreateFolderRequest request, Long userId) {
        log.info("Creating folder {} for user {}", request.getName(), userId);

        User user = userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Folder parentFolder = null;
        if (request.getParentId() != null) {
            parentFolder = folderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PARENT_FOLDER_NOT_FOUND));

            // Kiểm tra quyền sở hữu parent folder
            if (!parentFolder.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
            }
        }

        FolderVisibilityType visibility = request.getVisibility() != null
                ? request.getVisibility()
                : FolderVisibilityType.PUBLIC;

        Folder folder = Folder.builder()
                .name(request.getName())
                .user(user)
                .parent(parentFolder)
                .visibility(visibility)
                .shareToken(visibility == FolderVisibilityType.PRIVATE ? null : UUID.randomUUID().toString())
                .build();

        folder = folderRepository.save(folder);
        log.info("Folder created successfully with id: {}", folder.getId());

        return mapToResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getAllFolders(Long userId) {
        log.info("Getting all folders for user {}", userId);

        List<Folder> folders = folderRepository.findByUserId(userId);
        return folders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getRootFolders(Long userId) {
        log.info("Getting root folders for user {}", userId);

        List<Folder> folders = folderRepository.findByUserIdAndParentIsNull(userId);
        return folders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getSubFolders(Long folderId, Long userId) {
        log.info("Getting sub folders of folder {} for user {}", folderId, userId);

        // Kiểm tra quyền truy cập folder cha
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        List<Folder> subFolders = folderRepository.findByUserIdAndParentId(userId, folderId);
        return subFolders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponse getFolderById(Long folderId, Long userId) {
        log.info("Getting folder {} for user {}", folderId, userId);

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        return mapToResponse(folder);
    }

    @Override
    public FolderResponse updateFolder(Long folderId, UpdateFolderRequest request, Long userId) {
        log.info("Updating folder {} for user {}", folderId, userId);

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        // Kiểm tra quyền sở hữu
        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        // Cập nhật tên
        folder.setName(request.getName());

        // Cập nhật visibility (cascade xuống toàn bộ cây con)
        if (request.getVisibility() != null && request.getVisibility() != folder.getVisibility()) {
            applyVisibilityCascade(folder, request.getVisibility());
        }

        // Cập nhật parent folder nếu có
        if (request.getParentId() != null) {
            // Không cho phép đặt chính nó làm parent
            if (request.getParentId().equals(folderId)) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            Folder parentFolder = folderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PARENT_FOLDER_NOT_FOUND));

            // Kiểm tra quyền sở hữu parent folder
            if (!parentFolder.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
            }

            folder.setParent(parentFolder);
        } else {
            folder.setParent(null);
        }

        folder = folderRepository.save(folder);
        log.info("Folder updated successfully: {}", folderId);

        return mapToResponse(folder);
    }

    @Override
    public void deleteFolder(Long folderId, Long userId) {
        log.info("Deleting folder {} for user {}", folderId, userId);

        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        // Kiểm tra quyền sở hữu
        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        // Tính tổng dung lượng cần trả lại quota trước khi cascade delete
        Long freed = documentRepository.sumFileSizeInFolderTree(folderId);
        long freedBytes = freed != null ? freed : 0L;

        folderRepository.delete(folder);

        if (freedBytes > 0) {
            storageQuotaService.decrementUserUsage(folder.getUser(), freedBytes);
        }
        log.info("Folder deleted successfully: {} (freed {} bytes)", folderId, freedBytes);
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponse getSharedFolder(Long folderId) {
        log.info("Getting shared folder {}", folderId);
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));
        enforceShareAccess(folder);
        return mapToResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getSharedSubFolders(Long folderId) {
        log.info("Getting shared sub-folders of folder {}", folderId);
        Folder parent = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));
        enforceShareAccess(parent);
        return folderRepository.findByUserIdAndParentId(parent.getUser().getId(), folderId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponse getSharedFolderByToken(String shareToken) {
        log.info("Getting shared folder by token {}", shareToken);
        Folder folder = folderRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));
        enforceShareAccess(folder);
        return mapToResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getSharedSubFoldersByToken(String shareToken) {
        log.info("Getting shared sub-folders by token {}", shareToken);
        Folder parent = folderRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));
        enforceShareAccess(parent);
        return folderRepository.findByUserIdAndParentId(parent.getUser().getId(), parent.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FolderResponse rotateShareToken(Long folderId, Long userId) {
        log.info("Rotating share token of folder {} by user {}", folderId, userId);
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        if (!folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }
        if (folder.getVisibility() == FolderVisibilityType.PRIVATE) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        folder.setShareToken(UUID.randomUUID().toString());
        folder = folderRepository.save(folder);
        return mapToResponse(folder);
    }

    /**
     * Enforce rule truy cập folder share:
     * - PRIVATE → 404 (giấu sự tồn tại)
     * - LINK_ONLY → 401 nếu chưa đăng nhập
     * - PUBLIC → cho qua
     */
    private void enforceShareAccess(Folder folder) {
        FolderVisibilityType v = folder.getVisibility();
        if (v == null || v == FolderVisibilityType.PRIVATE) {
            throw new CustomException(ErrorCode.FOLDER_NOT_FOUND);
        }
        if (v == FolderVisibilityType.LINK_ONLY && !isRealUserAuthenticated()) {
            throw new CustomException(ErrorCode.SHARE_LOGIN_REQUIRED);
        }
    }

    /**
     * Trả về true nếu request hiện tại có user thật sự đăng nhập.
     * AnonymousAuthenticationToken (mặc định Spring Security set khi không có JWT
     * trên endpoint permitAll) KHÔNG được tính là đã đăng nhập.
     */
    private boolean isRealUserAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * Cascade visibility từ folder gốc xuống toàn bộ cây con (BFS).
     * - PRIVATE: clear shareToken cho tất cả
     * - PUBLIC / LINK_ONLY: sinh shareToken mới cho folder nào chưa có
     */
    private void applyVisibilityCascade(Folder root, FolderVisibilityType newVisibility) {
        Deque<Folder> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Folder current = queue.poll();
            current.setVisibility(newVisibility);
            if (newVisibility == FolderVisibilityType.PRIVATE) {
                current.setShareToken(null);
            } else if (current.getShareToken() == null) {
                current.setShareToken(UUID.randomUUID().toString());
            }
            folderRepository.save(current);
            queue.addAll(folderRepository.findByParentId(current.getId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getPublicFoldersByUser(Long userId) {
        log.info("Getting public folders for user {}", userId);
        return folderRepository.findByUserIdAndVisibility(userId, FolderVisibilityType.PUBLIC)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private FolderResponse mapToResponse(Folder folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .userId(folder.getUser().getId())
                .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .parentName(folder.getParent() != null ? folder.getParent().getName() : null)
                .visibility(folder.getVisibility())
                .shareToken(folder.getShareToken())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }
}
