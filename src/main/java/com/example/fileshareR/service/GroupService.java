package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.BanMemberRequest;
import com.example.fileshareR.dto.request.CreateGroupRequest;
import com.example.fileshareR.dto.request.SetAdminRequest;
import com.example.fileshareR.dto.request.UpdateGroupRequest;
import com.example.fileshareR.dto.response.GroupBanResponse;
import com.example.fileshareR.dto.response.GroupJoinRequestResponse;
import com.example.fileshareR.dto.response.GroupLandingResponse;
import com.example.fileshareR.dto.response.GroupMemberResponse;
import com.example.fileshareR.dto.response.GroupResponse;

import java.util.List;

public interface GroupService {

    GroupResponse createGroup(CreateGroupRequest request, Long ownerId);

    GroupResponse updateGroup(Long groupId, UpdateGroupRequest request, Long userId);

    void deleteGroup(Long groupId, Long userId);

    /** Xoá nhóm bởi admin — bỏ qua check ownership */
    void adminDeleteGroup(Long groupId);

    GroupResponse getGroupById(Long groupId, Long requesterId);

    List<GroupResponse> getMyGroups(Long userId);

    List<GroupResponse> searchPublicGroups(String keyword);

    /** Tìm kiếm nhóm: PUBLIC + PRIVATE mà user là member */
    List<GroupResponse> searchGroups(String keyword, Long userId);

    void joinGroup(Long groupId, Long userId);

    void leaveGroup(Long groupId, Long userId);

    List<GroupMemberResponse> getMembers(Long groupId, Long requesterId);

    void setAdmin(Long groupId, SetAdminRequest request, Long requesterId);

    GroupBanResponse banMember(Long groupId, BanMemberRequest request, Long requesterId);

    void unbanMember(Long groupId, Long targetUserId, Long requesterId);

    List<GroupBanResponse> getBans(Long groupId, Long requesterId);

    /**
     * Lấy landing info của nhóm theo invite token. Public (không cần login).
     * @param requesterId id user hiện tại (null nếu chưa login) — dùng để set cờ isMember
     */
    GroupLandingResponse getLandingByShareToken(String shareToken, Long requesterId);

    /**
     * Join nhóm qua invite token. Bỏ qua check PRIVATE (token đã là sự đồng ý trước).
     * Nếu đã là member → idempotent, không throw.
     */
    GroupResponse joinViaShareToken(String shareToken, Long userId);

    /** Gửi yêu cầu tham gia nhóm (kèm answers cho các câu hỏi) */
    GroupJoinRequestResponse submitJoinRequest(Long groupId, List<String> answers, Long userId);

    /** Gửi yêu cầu tham gia qua invite token */
    GroupJoinRequestResponse submitJoinRequestByToken(String shareToken, List<String> answers, Long userId);

    /** Danh sách request PENDING (admin/owner) */
    List<GroupJoinRequestResponse> getPendingRequests(Long groupId, Long requesterId);

    /** Duyệt yêu cầu */
    GroupJoinRequestResponse approveRequest(Long groupId, Long requestId, Long reviewerId);

    /** Từ chối yêu cầu */
    GroupJoinRequestResponse rejectRequest(Long groupId, Long requestId, Long reviewerId);

    /** Chuyển quyền sở hữu */
    GroupResponse transferOwnership(Long groupId, Long newOwnerId, Long currentOwnerId);

    /** Đếm số request pending (cho badge) */
    long countPendingRequests(Long groupId);

    /** Lấy group entity để update (require owner) */
    com.example.fileshareR.entity.Group getGroupEntityForUpdate(Long groupId, Long ownerId);

    /** Cập nhật avatar nhóm */
    void updateGroupAvatar(Long groupId, String avatarUrl);
}
