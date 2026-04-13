package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.CreateGroupFolderRequest;
import com.example.fileshareR.dto.response.GroupFolderResponse;

import java.util.List;

public interface GroupFolderService {

    GroupFolderResponse createFolder(Long groupId, CreateGroupFolderRequest request, Long userId);

    List<GroupFolderResponse> getGroupFolders(Long groupId, Long requesterId);

    List<GroupFolderResponse> getRootFolders(Long groupId, Long requesterId);

    List<GroupFolderResponse> getSubFolders(Long groupId, Long parentFolderId, Long requesterId);

    void deleteFolder(Long groupId, Long folderId, Long userId);

    /**
     * Lấy group folder theo share token.
     * Chỉ hoạt động khi group hiện vẫn đang PUBLIC — nếu đã đổi sang PRIVATE thì trả 404.
     */
    GroupFolderResponse getByShareToken(String shareToken);

    List<GroupFolderResponse> getSubFoldersByShareToken(String shareToken);

    /** Sinh share token mới cho group folder (revoke link cũ). Chỉ admin/owner mới gọi được. */
    GroupFolderResponse rotateShareToken(Long folderId, Long userId);

    /**
     * Cascade toàn bộ group folder trong group theo visibility mới của group.
     * - PUBLIC: sinh share_token cho folder nào đang NULL
     * - PRIVATE: clear toàn bộ share_token
     * Được gọi từ GroupServiceImpl.updateGroup khi visibility đổi.
     */
    void syncShareTokensWithGroupVisibility(Long groupId);
}
