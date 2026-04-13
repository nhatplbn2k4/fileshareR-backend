package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.*;
import com.example.fileshareR.dto.response.*;
import com.example.fileshareR.dto.response.GroupJoinRequestResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.GroupFolderService;
import com.example.fileshareR.service.GroupService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupFolderService groupFolderService;
    private final DocumentService documentService;
    private final UserService userService;
    private final com.example.fileshareR.service.AvatarService avatarService;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC endpoints (không cần auth)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tìm kiếm nhóm PUBLIC
     * GET /api/groups/public?keyword=xxx
     */
    @GetMapping("/public")
    public ResponseEntity<List<GroupResponse>> searchPublicGroups(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(groupService.searchPublicGroups(keyword));
    }

    /**
     * [PUBLIC] Tìm kiếm nhóm: PUBLIC + PRIVATE (mà user là thành viên)
     * GET /api/groups/search?keyword=xxx
     */
    @GetMapping("/search")
    public ResponseEntity<List<GroupResponse>> searchGroups(
            @RequestParam(required = false) String keyword) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupService.searchGroups(keyword, requesterId));
    }

    /**
     * Chi tiết nhóm (PUBLIC = mọi người thấy, PRIVATE = chỉ thành viên)
     * GET /api/groups/{id}
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroupById(@PathVariable Long groupId) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupService.getGroupById(groupId, requesterId));
    }

    /**
     * Danh sách thành viên nhóm
     * GET /api/groups/{id}/members
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> getMembers(@PathVariable Long groupId) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupService.getMembers(groupId, requesterId));
    }

    /**
     * Tài liệu nhóm (PUBLIC = mọi người tải được, PRIVATE = chỉ thành viên)
     * GET /api/groups/{id}/documents?folderId=xxx
     * folderId=null → tất cả, folderId=-1 → không có folder, folderId={id} → trong folder đó
     */
    @GetMapping("/{groupId}/documents")
    public ResponseEntity<List<DocumentResponse>> getGroupDocuments(
            @PathVariable Long groupId,
            @RequestParam(required = false) Long folderId) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(documentService.getGroupDocuments(groupId, folderId, requesterId));
    }

    /**
     * Tải tài liệu nhóm
     * GET /api/groups/{id}/documents/{did}/download
     */
    @GetMapping("/{groupId}/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadGroupDocument(
            @PathVariable Long groupId,
            @PathVariable Long documentId) {
        Long requesterId = getCurrentUserIdOptional();
        Resource resource = documentService.downloadGroupDocument(documentId, groupId, requesterId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Danh sách thư mục nhóm
     * GET /api/groups/{id}/folders
     */
    @GetMapping("/{groupId}/folders")
    public ResponseEntity<List<GroupFolderResponse>> getGroupFolders(@PathVariable Long groupId) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupFolderService.getGroupFolders(groupId, requesterId));
    }

    /**
     * Thư mục gốc của nhóm
     * GET /api/groups/{id}/folders/root
     */
    @GetMapping("/{groupId}/folders/root")
    public ResponseEntity<List<GroupFolderResponse>> getRootFolders(@PathVariable Long groupId) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupFolderService.getRootFolders(groupId, requesterId));
    }

    /**
     * Thư mục con
     * GET /api/groups/{id}/folders/{fid}/subfolders
     */
    @GetMapping("/{groupId}/folders/{folderId}/subfolders")
    public ResponseEntity<List<GroupFolderResponse>> getSubFolders(
            @PathVariable Long groupId,
            @PathVariable Long folderId) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupFolderService.getSubFolders(groupId, folderId, requesterId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * [AUTH, OWNER] Upload avatar nhóm.
     * POST /api/groups/{id}/avatar
     */
    @PostMapping("/{groupId}/avatar")
    public ResponseEntity<java.util.Map<String, String>> uploadGroupAvatar(
            @PathVariable Long groupId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        Long userId = getCurrentUserId();
        // Chỉ owner mới đổi avatar
        com.example.fileshareR.entity.Group group = groupService.getGroupEntityForUpdate(groupId, userId);
        String ext = file.getOriginalFilename() != null
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1)
                : "jpg";
        String path = "avatars/groups/" + groupId + "." + ext;
        String url = avatarService.uploadAvatar(file, path);
        groupService.updateGroupAvatar(groupId, url);
        return ResponseEntity.ok(java.util.Map.of("avatarUrl", url));
    }

    // Share group folder by token (chỉ áp dụng cho folder trong group PUBLIC)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [PUBLIC] Xem group folder qua share token.
     * GET /api/groups/folders/shared/{token}
     */
    @GetMapping("/folders/shared/{token}")
    public ResponseEntity<GroupFolderResponse> getGroupFolderByShareToken(@PathVariable String token) {
        return ResponseEntity.ok(groupFolderService.getByShareToken(token));
    }

    /**
     * [PUBLIC] Sub-folders của group folder share.
     * GET /api/groups/folders/shared/{token}/subfolders
     */
    @GetMapping("/folders/shared/{token}/subfolders")
    public ResponseEntity<List<GroupFolderResponse>> getGroupFolderSubfoldersByShareToken(
            @PathVariable String token) {
        return ResponseEntity.ok(groupFolderService.getSubFoldersByShareToken(token));
    }

    /**
     * [PUBLIC] Tài liệu trong group folder share.
     * GET /api/groups/folders/shared/{token}/documents
     */
    @GetMapping("/folders/shared/{token}/documents")
    public ResponseEntity<List<DocumentResponse>> getGroupFolderDocumentsByShareToken(
            @PathVariable String token) {
        // Resolve token → folder (service đã enforce group PUBLIC)
        GroupFolderResponse folder = groupFolderService.getByShareToken(token);
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(
                documentService.getGroupDocuments(folder.getGroupId(), folder.getId(), requesterId));
    }

    /**
     * [AUTH, ADMIN/OWNER] Rotate share token của group folder.
     * POST /api/groups/folders/{folderId}/share/rotate
     */
    @PostMapping("/folders/{folderId}/share/rotate")
    public ResponseEntity<GroupFolderResponse> rotateGroupFolderShareToken(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupFolderService.rotateShareToken(folderId, userId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Join requests + Transfer ownership
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [AUTH] Gửi yêu cầu tham gia nhóm (khi requireApproval=true).
     * POST /api/groups/{id}/join-request
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/{groupId}/join-request")
    public ResponseEntity<GroupJoinRequestResponse> submitJoinRequest(
            @PathVariable Long groupId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        Long userId = getCurrentUserId();
        java.util.List<String> answers = body != null ? (java.util.List<String>) body.get("answers") : null;
        return ResponseEntity.ok(groupService.submitJoinRequest(groupId, answers, userId));
    }

    /**
     * [AUTH, ADMIN/OWNER] Danh sách yêu cầu tham gia đang chờ.
     * GET /api/groups/{id}/join-requests
     */
    @GetMapping("/{groupId}/join-requests")
    public ResponseEntity<java.util.List<GroupJoinRequestResponse>> getPendingRequests(
            @PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.getPendingRequests(groupId, userId));
    }

    /**
     * [AUTH, ADMIN/OWNER] Duyệt yêu cầu.
     * POST /api/groups/{id}/join-requests/{reqId}/approve
     */
    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public ResponseEntity<GroupJoinRequestResponse> approveJoinRequest(
            @PathVariable Long groupId, @PathVariable Long requestId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.approveRequest(groupId, requestId, userId));
    }

    /**
     * [AUTH, ADMIN/OWNER] Từ chối yêu cầu.
     * POST /api/groups/{id}/join-requests/{reqId}/reject
     */
    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public ResponseEntity<GroupJoinRequestResponse> rejectJoinRequest(
            @PathVariable Long groupId, @PathVariable Long requestId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.rejectRequest(groupId, requestId, userId));
    }

    /**
     * [AUTH, OWNER] Chuyển quyền sở hữu nhóm.
     * POST /api/groups/{id}/transfer-ownership
     */
    @PostMapping("/{groupId}/transfer-ownership")
    public ResponseEntity<GroupResponse> transferOwnership(
            @PathVariable Long groupId,
            @RequestBody java.util.Map<String, Long> body) {
        Long userId = getCurrentUserId();
        Long newOwnerId = body.get("newOwnerId");
        return ResponseEntity.ok(groupService.transferOwnership(groupId, newOwnerId, userId));
    }

    /**
     * [AUTH] Gửi yêu cầu tham gia qua invite token.
     * POST /api/groups/shared/{token}/join-request
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/shared/{token}/join-request")
    public ResponseEntity<GroupJoinRequestResponse> submitJoinRequestByToken(
            @PathVariable String token,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        Long userId = getCurrentUserId();
        java.util.List<String> answers = body != null ? (java.util.List<String>) body.get("answers") : null;
        return ResponseEntity.ok(groupService.submitJoinRequestByToken(token, answers, userId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Share by invite token
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [PUBLIC] Landing info của nhóm qua invite token.
     * GET /api/groups/shared/{token}
     */
    @GetMapping("/shared/{token}")
    public ResponseEntity<GroupLandingResponse> getGroupLandingByShareToken(@PathVariable String token) {
        Long requesterId = getCurrentUserIdOptional();
        return ResponseEntity.ok(groupService.getLandingByShareToken(token, requesterId));
    }

    /**
     * [AUTH] Join nhóm qua invite token.
     * POST /api/groups/shared/{token}/join
     */
    @PostMapping("/shared/{token}/join")
    public ResponseEntity<GroupResponse> joinGroupByShareToken(@PathVariable String token) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.joinViaShareToken(token, userId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTHENTICATED endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Nhóm của tôi (các nhóm tôi đang là thành viên)
     * GET /api/groups/my
     */
    @GetMapping("/my")
    public ResponseEntity<List<GroupResponse>> getMyGroups() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.getMyGroups(userId));
    }

    /**
     * Tạo nhóm mới
     * POST /api/groups
     */
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        Long userId = getCurrentUserId();
        GroupResponse response = groupService.createGroup(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Tham gia nhóm PUBLIC
     * POST /api/groups/{id}/join
     */
    @PostMapping("/{groupId}/join")
    public ResponseEntity<String> joinGroup(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        groupService.joinGroup(groupId, userId);
        return ResponseEntity.ok("Tham gia nhóm thành công");
    }

    /**
     * Rời nhóm
     * DELETE /api/groups/{id}/leave
     */
    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<String> leaveGroup(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        groupService.leaveGroup(groupId, userId);
        return ResponseEntity.ok("Rời nhóm thành công");
    }

    /**
     * Upload tài liệu vào nhóm (thành viên + không bị ban upload)
     * POST /api/groups/{id}/documents
     */
    @PostMapping("/{groupId}/documents")
    public ResponseEntity<DocumentResponse> uploadGroupDocument(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "groupFolderId", required = false) Long groupFolderId,
            @RequestParam(value = "visibility", required = false,
                    defaultValue = "PUBLIC") String visibility) {
        Long userId = getCurrentUserId();

        UploadGroupDocumentRequest request = UploadGroupDocumentRequest.builder()
                .title(title)
                .groupFolderId(groupFolderId)
                .build();

        DocumentResponse response = documentService.uploadGroupDocument(file, request, groupId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OWNER only
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật nhóm (chỉ OWNER)
     * PUT /api/groups/{id}
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.updateGroup(groupId, request, userId));
    }

    /**
     * Xóa nhóm (chỉ OWNER)
     * DELETE /api/groups/{id}
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<String> deleteGroup(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        groupService.deleteGroup(groupId, userId);
        return ResponseEntity.ok("Xóa nhóm thành công");
    }

    /**
     * Cấp / thu hồi quyền ADMIN (chỉ OWNER)
     * POST /api/groups/{id}/admin
     */
    @PostMapping("/{groupId}/admin")
    public ResponseEntity<String> setAdmin(
            @PathVariable Long groupId,
            @Valid @RequestBody SetAdminRequest request) {
        Long userId = getCurrentUserId();
        groupService.setAdmin(groupId, request, userId);
        String msg = Boolean.TRUE.equals(request.getIsAdmin())
                ? "Cấp quyền quản trị viên thành công"
                : "Thu hồi quyền quản trị viên thành công";
        return ResponseEntity.ok(msg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN / OWNER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ban thành viên (ADMIN/OWNER)
     * POST /api/groups/{id}/ban
     */
    @PostMapping("/{groupId}/ban")
    public ResponseEntity<GroupBanResponse> banMember(
            @PathVariable Long groupId,
            @Valid @RequestBody BanMemberRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.banMember(groupId, request, userId));
    }

    /**
     * Gỡ ban thành viên (ADMIN/OWNER)
     * DELETE /api/groups/{id}/ban/{uid}
     */
    @DeleteMapping("/{groupId}/ban/{targetUserId}")
    public ResponseEntity<String> unbanMember(
            @PathVariable Long groupId,
            @PathVariable Long targetUserId) {
        Long userId = getCurrentUserId();
        groupService.unbanMember(groupId, targetUserId, userId);
        return ResponseEntity.ok("Gỡ ban thành công");
    }

    /**
     * Danh sách ban còn hiệu lực (ADMIN/OWNER)
     * GET /api/groups/{id}/bans
     */
    @GetMapping("/{groupId}/bans")
    public ResponseEntity<List<GroupBanResponse>> getBans(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(groupService.getBans(groupId, userId));
    }

    /**
     * Tạo thư mục trong nhóm (ADMIN/OWNER)
     * POST /api/groups/{id}/folders
     */
    @PostMapping("/{groupId}/folders")
    public ResponseEntity<GroupFolderResponse> createGroupFolder(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateGroupFolderRequest request) {
        Long userId = getCurrentUserId();
        GroupFolderResponse response = groupFolderService.createFolder(groupId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Xóa thư mục trong nhóm (ADMIN/OWNER)
     * DELETE /api/groups/{id}/folders/{fid}
     */
    @DeleteMapping("/{groupId}/folders/{folderId}")
    public ResponseEntity<String> deleteGroupFolder(
            @PathVariable Long groupId,
            @PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        groupFolderService.deleteFolder(groupId, folderId, userId);
        return ResponseEntity.ok("Xóa thư mục thành công");
    }

    /**
     * Xóa tài liệu nhóm (owner tài liệu hoặc ADMIN/OWNER nhóm)
     * DELETE /api/groups/{id}/documents/{did}
     */
    @DeleteMapping("/{groupId}/documents/{documentId}")
    public ResponseEntity<String> deleteGroupDocument(
            @PathVariable Long groupId,
            @PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        documentService.deleteGroupDocument(documentId, groupId, userId);
        return ResponseEntity.ok("Xóa tài liệu thành công");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }

    /**
     * Trả về userId nếu đã đăng nhập, null nếu chưa (dùng cho public endpoints)
     */
    private Long getCurrentUserIdOptional() {
        Optional<String> emailOpt = SecurityUtil.getCurrentUserLogin();
        if (emailOpt.isEmpty()) return null;
        return userService.getUserByEmail(emailOpt.get())
                .map(User::getId)
                .orElse(null);
    }
}
