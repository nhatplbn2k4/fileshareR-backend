package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.CreateFolderRequest;
import com.example.fileshareR.dto.request.UpdateFolderRequest;
import com.example.fileshareR.dto.response.FolderResponse;

import java.util.List;

public interface FolderService {
    /**
     * Tạo thư mục mới
     */
    FolderResponse createFolder(CreateFolderRequest request, Long userId);

    /**
     * Lấy danh sách tất cả thư mục của user
     */
    List<FolderResponse> getAllFolders(Long userId);

    /**
     * Lấy danh sách thư mục gốc (không có parent) của user
     */
    List<FolderResponse> getRootFolders(Long userId);

    /**
     * Lấy danh sách thư mục con của một thư mục
     */
    List<FolderResponse> getSubFolders(Long folderId, Long userId);

    /**
     * Lấy thông tin chi tiết một thư mục
     */
    FolderResponse getFolderById(Long folderId, Long userId);

    /**
     * Cập nhật thông tin thư mục
     */
    FolderResponse updateFolder(Long folderId, UpdateFolderRequest request, Long userId);

    /**
     * Xóa thư mục
     */
    void deleteFolder(Long folderId, Long userId);

    /**
     * Lấy thông tin folder PUBLIC hoặc LINK_ONLY theo folder id (legacy)
     * LINK_ONLY yêu cầu user đã đăng nhập (kiểm tra qua SecurityContext)
     */
    FolderResponse getSharedFolder(Long folderId);

    /**
     * Lấy sub-folders của folder PUBLIC/LINK_ONLY theo folder id (legacy)
     */
    List<FolderResponse> getSharedSubFolders(Long folderId);

    /**
     * Lấy folder share theo share token (UUID).
     * - PUBLIC: ai cũng truy cập được
     * - LINK_ONLY: yêu cầu user đã đăng nhập
     * - PRIVATE: token không tồn tại (đã clear khi chuyển sang PRIVATE)
     */
    FolderResponse getSharedFolderByToken(String shareToken);

    /**
     * Lấy sub-folders của folder share theo token. Áp dụng cùng rule auth như getSharedFolderByToken.
     */
    List<FolderResponse> getSharedSubFoldersByToken(String shareToken);

    /**
     * Sinh share token mới cho folder (revoke link cũ). Chỉ owner gọi được.
     * Không cho phép rotate khi folder đang PRIVATE.
     */
    FolderResponse rotateShareToken(Long folderId, Long userId);

    /** Lấy danh sách folder PUBLIC của user (cho profile page) */
    List<FolderResponse> getPublicFoldersByUser(Long userId);
}
