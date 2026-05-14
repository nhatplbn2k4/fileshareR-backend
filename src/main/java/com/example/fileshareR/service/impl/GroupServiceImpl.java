package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.BanMemberRequest;
import com.example.fileshareR.dto.request.CreateGroupRequest;
import com.example.fileshareR.dto.request.SetAdminRequest;
import com.example.fileshareR.dto.request.UpdateGroupRequest;
import com.example.fileshareR.dto.response.GroupBanResponse;
import com.example.fileshareR.dto.response.GroupJoinRequestResponse;
import com.example.fileshareR.dto.response.GroupLandingResponse;
import com.example.fileshareR.dto.response.GroupMemberResponse;
import com.example.fileshareR.dto.response.GroupResponse;
import com.example.fileshareR.entity.GroupJoinRequest;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupBan;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.fileshareR.enums.BanType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.enums.JoinRequestStatus;
import com.example.fileshareR.repository.GroupBanRepository;
import com.example.fileshareR.repository.GroupJoinRequestRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.GroupFolderService;
import com.example.fileshareR.service.GroupService;
import com.example.fileshareR.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupBanRepository groupBanRepository;
    private final GroupJoinRequestRepository joinRequestRepository;
    private final UserService userService;
    private final GroupFolderService groupFolderService;
    private final ObjectMapper objectMapper;
    private final com.example.fileshareR.repository.DocumentRepository documentRepository;
    private final com.example.fileshareR.repository.GroupFolderRepository groupFolderRepository;
    private final com.example.fileshareR.service.FileStorageService fileStorageService;
    private final com.example.fileshareR.repository.PlanRepository planRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD Nhóm
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public GroupResponse createGroup(CreateGroupRequest request, Long ownerId) {
        log.info("User {} creating group: {}", ownerId, request.getName());

        User owner = getUserById(ownerId);

        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .visibility(request.getVisibility() != null ? request.getVisibility() : GroupVisibilityType.PRIVATE)
                .owner(owner)
                .shareToken(UUID.randomUUID().toString())
                .plan(planRepository.findByCode("FREE").orElse(null))
                .build();
        group = groupRepository.save(group);

        // Tự động thêm owner vào group_members với role OWNER
        GroupMember ownerMember = GroupMember.builder()
                .group(group)
                .user(owner)
                .role(GroupMemberRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();
        groupMemberRepository.save(ownerMember);

        log.info("Group {} created by user {}", group.getId(), ownerId);
        return mapToGroupResponse(group, ownerId);
    }

    @Override
    public GroupResponse updateGroup(Long groupId, UpdateGroupRequest request, Long userId) {
        log.info("User {} updating group {}", userId, groupId);

        Group group = getGroupEntityById(groupId);
        requireOwner(group, userId);

        if (request.getName() != null && !request.getName().isBlank()) {
            group.setName(request.getName());
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }
        boolean visibilityChanged = false;
        if (request.getVisibility() != null && request.getVisibility() != group.getVisibility()) {
            group.setVisibility(request.getVisibility());
            visibilityChanged = true;
        }
        if (request.getAvatarUrl() != null) {
            group.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getRequireApproval() != null) {
            group.setRequireApproval(request.getRequireApproval());
        }
        if (request.getJoinQuestions() != null) {
            List<String> questions = request.getJoinQuestions().stream()
                    .filter(q -> q != null && !q.isBlank()).collect(Collectors.toList());
            group.setJoinQuestion(questions.isEmpty() ? null : toJson(questions));
        }

        group = groupRepository.save(group);

        // Cascade: khi visibility đổi, đồng bộ share_token của toàn bộ group folders
        if (visibilityChanged) {
            groupFolderService.syncShareTokensWithGroupVisibility(groupId);
        }
        log.info("Group {} updated by user {}", groupId, userId);
        return mapToGroupResponse(group, userId);
    }

    @Override
    public void deleteGroup(Long groupId, Long userId) {
        log.info("User {} deleting group {}", userId, groupId);

        Group group = getGroupEntityById(groupId);
        requireOwner(group, userId);

        // Xóa tất cả dữ liệu liên quan theo thứ tự FK dependency
        // 1. Documents thuộc group
        documentRepository.findByGroupId(groupId)
                .forEach(doc -> {
                    try { fileStorageService.deleteFile(doc.getFileUrl()); } catch (Exception e) { /* ignore */ }
                    documentRepository.delete(doc);
                });
        // 2. Group folders
        groupFolderRepository.findByGroupId(groupId)
                .forEach(groupFolderRepository::delete);
        // 3. Join requests
        joinRequestRepository.findByGroupIdAndStatus(groupId, JoinRequestStatus.PENDING)
                .forEach(joinRequestRepository::delete);
        // 4. Bans
        groupBanRepository.findByGroupIdAndActiveTrue(groupId)
                .forEach(ban -> {
                    ban.setActive(false);
                    groupBanRepository.save(ban);
                });
        // 5. Members
        groupMemberRepository.findByGroupId(groupId)
                .forEach(groupMemberRepository::delete);
        // 6. Group
        groupRepository.delete(group);

        log.info("Group {} deleted by user {}", groupId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Truy vấn nhóm
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroupById(Long groupId, Long requesterId) {
        Group group = getGroupEntityById(groupId);
        // Không throw khi PRIVATE + non-member — frontend sẽ hiện thông báo riêng dựa trên isMember
        return mapToGroupResponse(group, requesterId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(Long userId) {
        log.info("Getting groups for user {}", userId);
        return groupMemberRepository.findByUserId(userId).stream()
                .map(m -> mapToGroupResponse(m.getGroup(), userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> searchPublicGroups(String keyword) {
        List<Group> groups;
        if (keyword == null || keyword.isBlank()) {
            groups = groupRepository.findByVisibility(GroupVisibilityType.PUBLIC);
        } else {
            groups = groupRepository.findByNameContainingIgnoreCaseAndVisibility(
                    keyword, GroupVisibilityType.PUBLIC);
        }
        return groups.stream()
                .map(g -> mapToGroupResponse(g, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> searchGroups(String keyword, Long userId) {
        // Tìm TẤT CẢ nhóm (PUBLIC + PRIVATE) matching keyword
        List<Group> groups;
        if (keyword == null || keyword.isBlank()) {
            groups = groupRepository.findAll();
        } else {
            groups = groupRepository.findByNameContainingIgnoreCase(keyword);
        }

        List<GroupResponse> results = groups.stream()
                .map(g -> mapToGroupResponse(g, userId))
                .collect(Collectors.toList());
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tham gia / Rời nhóm
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void joinGroup(Long groupId, Long userId) {
        log.info("User {} joining group {}", userId, groupId);

        Group group = getGroupEntityById(groupId);

        // Chỉ có thể tự join nhóm PUBLIC
        if (group.getVisibility() == GroupVisibilityType.PRIVATE) {
            throw new CustomException(ErrorCode.GROUP_JOIN_NOT_ALLOWED);
        }

        // Nếu nhóm yêu cầu phê duyệt → không cho join trực tiếp, phải qua submitJoinRequest
        if (Boolean.TRUE.equals(group.getRequireApproval())) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Nhóm này yêu cầu phê duyệt. Vui lòng gửi yêu cầu tham gia.");
        }

        addMemberDirectly(group, userId);
    }

    @Override
    public void leaveGroup(Long groupId, Long userId) {
        log.info("User {} leaving group {}", userId, groupId);

        Group group = getGroupEntityById(groupId);

        // OWNER không thể rời nhóm
        if (group.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.GROUP_OWNER_REQUIRED,
                    "Chủ nhóm không thể rời nhóm. Hãy xóa nhóm hoặc chuyển quyền chủ nhóm.");
        }

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        groupMemberRepository.delete(member);
        log.info("User {} left group {}", userId, groupId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quản lý thành viên
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(Long groupId, Long requesterId) {
        Group group = getGroupEntityById(groupId);

        // Nhóm PRIVATE: chỉ thành viên xem được
        if (group.getVisibility() == GroupVisibilityType.PRIVATE) {
            if (requesterId == null || !groupMemberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
                throw new CustomException(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
            }
        }

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        LocalDateTime now = LocalDateTime.now();

        return members.stream().map(m -> {
            Optional<GroupBan> activeBan = groupBanRepository.findActiveUploadBan(
                    groupId, m.getUser().getId(), now);
            return GroupMemberResponse.builder()
                    .userId(m.getUser().getId())
                    .fullName(m.getUser().getFullName())
                    .email(m.getUser().getEmail())
                    .avatarUrl(m.getUser().getAvatarUrl())
                    .role(m.getRole())
                    .joinedAt(m.getJoinedAt())
                    .isUploadBanned(activeBan.isPresent())
                    .banExpiresAt(activeBan.map(GroupBan::getExpiresAt).orElse(null))
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public void setAdmin(Long groupId, SetAdminRequest request, Long requesterId) {
        log.info("User {} setting admin={} for user {} in group {}",
                requesterId, request.getIsAdmin(), request.getUserId(), groupId);

        Group group = getGroupEntityById(groupId);
        requireOwner(group, requesterId);

        // Không thể thao tác trên chính owner
        if (request.getUserId().equals(group.getOwner().getId())) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Không thể thay đổi quyền của chủ nhóm.");
        }

        GroupMember targetMember = groupMemberRepository.findByGroupIdAndUserId(groupId, request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (Boolean.TRUE.equals(request.getIsAdmin())) {
            targetMember.setRole(GroupMemberRole.ADMIN);
            log.info("User {} granted ADMIN in group {}", request.getUserId(), groupId);
        } else {
            targetMember.setRole(GroupMemberRole.MEMBER);
            log.info("User {} revoked ADMIN in group {}", request.getUserId(), groupId);
        }
        groupMemberRepository.save(targetMember);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ban / Unban thành viên
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public GroupBanResponse banMember(Long groupId, BanMemberRequest request, Long requesterId) {
        log.info("User {} banning user {} (type: {}) in group {}",
                requesterId, request.getUserId(), request.getBanType(), groupId);

        Group group = getGroupEntityById(groupId);
        GroupMember requesterMember = requireAdminOrOwner(groupId, requesterId);

        // Không thể ban OWNER
        if (request.getUserId().equals(group.getOwner().getId())) {
            throw new CustomException(ErrorCode.GROUP_ADMIN_REQUIRED,
                    "Không thể ban chủ nhóm.");
        }

        // ADMIN không thể ban ADMIN khác (chỉ OWNER có thể)
        GroupMember targetMember = groupMemberRepository.findByGroupIdAndUserId(groupId, request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (targetMember.getRole() == GroupMemberRole.ADMIN
                && requesterMember.getRole() == GroupMemberRole.ADMIN) {
            throw new CustomException(ErrorCode.GROUP_OWNER_REQUIRED,
                    "Chỉ chủ nhóm mới có thể ban quản trị viên khác.");
        }

        User requester = getUserById(requesterId);
        User targetUser = getUserById(request.getUserId());

        // Tính thời hạn hết ban
        LocalDateTime expiresAt = computeExpiresAt(request.getBanType());

        // Hủy bỏ các ban upload cũ còn active
        groupBanRepository.findByGroupIdAndUserId(groupId, request.getUserId())
                .stream()
                .filter(GroupBan::getActive)
                .forEach(b -> {
                    b.setActive(false);
                    groupBanRepository.save(b);
                });

        GroupBan ban = GroupBan.builder()
                .group(group)
                .user(targetUser)
                .bannedBy(requester)
                .banType(request.getBanType())
                .reason(request.getReason())
                .expiresAt(expiresAt)
                .active(true)
                .build();
        ban = groupBanRepository.save(ban);

        // Nếu là KICKED → xóa khỏi group_members
        if (request.getBanType() == BanType.KICKED) {
            groupMemberRepository.delete(targetMember);
            log.info("User {} kicked from group {}", request.getUserId(), groupId);
        } else {
            log.info("User {} upload-banned until {} in group {}",
                    request.getUserId(), expiresAt, groupId);
        }

        return mapToBanResponse(ban);
    }

    @Override
    public void unbanMember(Long groupId, Long targetUserId, Long requesterId) {
        log.info("User {} unbanning user {} in group {}", requesterId, targetUserId, groupId);

        getGroupEntityById(groupId);
        requireAdminOrOwner(groupId, requesterId);

        List<GroupBan> activeBans = groupBanRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .stream()
                .filter(GroupBan::getActive)
                .collect(Collectors.toList());

        if (activeBans.isEmpty()) {
            throw new CustomException(ErrorCode.GROUP_MEMBER_NOT_FOUND,
                    "Không tìm thấy lệnh ban cho người dùng này.");
        }

        activeBans.forEach(b -> {
            b.setActive(false);
            groupBanRepository.save(b);
        });
        log.info("User {} unbanned from group {}", targetUserId, groupId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupBanResponse> getBans(Long groupId, Long requesterId) {
        getGroupEntityById(groupId);
        requireAdminOrOwner(groupId, requesterId);

        return groupBanRepository.findByGroupIdAndActiveTrue(groupId)
                .stream()
                .map(this::mapToBanResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    private Group getGroupEntityById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User getUserById(Long userId) {
        return userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void requireOwner(Group group, Long userId) {
        if (!group.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private GroupMember requireAdminOrOwner(Long groupId, Long userId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_ACCESS_DENIED));
        if (member.getRole() == GroupMemberRole.MEMBER) {
            throw new CustomException(ErrorCode.GROUP_ADMIN_REQUIRED);
        }
        return member;
    }

    /**
     * Tính thời hạn hết ban dựa theo BanType.
     * KICKED trả null (vĩnh viễn).
     */
    private LocalDateTime computeExpiresAt(BanType banType) {
        return switch (banType) {
            case KICKED -> null;
            case UPLOAD_BAN_7_DAYS -> LocalDateTime.now().plusDays(7);
            case UPLOAD_BAN_1_MONTH -> LocalDateTime.now().plusMonths(1);
            case UPLOAD_BAN_1_YEAR -> LocalDateTime.now().plusYears(1);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Share by invite token
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public GroupLandingResponse getLandingByShareToken(String shareToken, Long requesterId) {
        log.info("Getting group landing by share token {}", shareToken);
        Group group = groupRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        long memberCount = groupMemberRepository.countByGroupId(group.getId());
        boolean isMember = requesterId != null
                && groupMemberRepository.existsByGroupIdAndUserId(group.getId(), requesterId);

        boolean hasPending = requesterId != null && !isMember
                && joinRequestRepository.existsByGroupIdAndUserIdAndStatus(
                        group.getId(), requesterId, JoinRequestStatus.PENDING);

        return GroupLandingResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .visibility(group.getVisibility())
                .avatarUrl(group.getAvatarUrl())
                .ownerName(group.getOwner().getFullName())
                .memberCount(memberCount)
                .isMember(isMember)
                .requireApproval(group.getRequireApproval())
                .joinQuestions(parseJsonList(group.getJoinQuestion()))
                .hasPendingRequest(hasPending)
                .build();
    }

    @Override
    public GroupResponse joinViaShareToken(String shareToken, Long userId) {
        log.info("User {} joining group via share token {}", userId, shareToken);
        Group group = groupRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        // Nếu nhóm yêu cầu phê duyệt → không cho join trực tiếp qua invite link
        if (Boolean.TRUE.equals(group.getRequireApproval())) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Nhóm này yêu cầu phê duyệt. Vui lòng gửi yêu cầu tham gia.");
        }

        // Check kicked
        Optional<GroupBan> kick = groupBanRepository
                .findByGroupIdAndUserIdAndBanTypeAndActiveTrue(group.getId(), userId, BanType.KICKED);
        if (kick.isPresent()) {
            throw new CustomException(ErrorCode.GROUP_JOIN_NOT_ALLOWED);
        }

        // Idempotent
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            return mapToGroupResponse(group, userId);
        }

        addMemberDirectly(group, userId);
        return mapToGroupResponse(group, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Join requests + Transfer ownership
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public GroupJoinRequestResponse submitJoinRequest(Long groupId, List<String> answers, Long userId) {
        log.info("User {} submitting join request for group {}", userId, groupId);
        Group group = getGroupEntityById(groupId);
        return createJoinRequest(group, answers, userId);
    }

    @Override
    public GroupJoinRequestResponse submitJoinRequestByToken(String shareToken, List<String> answers, Long userId) {
        log.info("User {} submitting join request via token {}", userId, shareToken);
        Group group = groupRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        return createJoinRequest(group, answers, userId);
    }

    private GroupJoinRequestResponse createJoinRequest(Group group, List<String> answers, Long userId) {
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new CustomException(ErrorCode.GROUP_ALREADY_MEMBER);
        }
        Optional<GroupBan> kick = groupBanRepository
                .findByGroupIdAndUserIdAndBanTypeAndActiveTrue(group.getId(), userId, BanType.KICKED);
        if (kick.isPresent()) {
            throw new CustomException(ErrorCode.GROUP_JOIN_NOT_ALLOWED);
        }
        if (joinRequestRepository.existsByGroupIdAndUserIdAndStatus(
                group.getId(), userId, JoinRequestStatus.PENDING)) {
            throw new CustomException(ErrorCode.GROUP_JOIN_REQUEST_PENDING);
        }

        User user = getUserById(userId);
        GroupJoinRequest request = GroupJoinRequest.builder()
                .group(group)
                .user(user)
                .answer(answers != null && !answers.isEmpty() ? toJson(answers) : null)
                .status(JoinRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        request = joinRequestRepository.save(request);
        log.info("Join request {} created for group {}", request.getId(), group.getId());
        return mapToJoinRequestResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupJoinRequestResponse> getPendingRequests(Long groupId, Long requesterId) {
        getGroupEntityById(groupId);
        requireAdminOrOwner(groupId, requesterId);
        return joinRequestRepository.findByGroupIdAndStatus(groupId, JoinRequestStatus.PENDING)
                .stream()
                .map(this::mapToJoinRequestResponse)
                .collect(Collectors.toList());
    }

    @Override
    public GroupJoinRequestResponse approveRequest(Long groupId, Long requestId, Long reviewerId) {
        log.info("Reviewer {} approving request {} in group {}", reviewerId, requestId, groupId);
        getGroupEntityById(groupId);
        requireAdminOrOwner(groupId, reviewerId);

        GroupJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND));
        if (!request.getGroup().getId().equals(groupId) || request.getStatus() != JoinRequestStatus.PENDING) {
            throw new CustomException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND);
        }

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setReviewedBy(getUserById(reviewerId));
        request.setReviewedAt(LocalDateTime.now());
        joinRequestRepository.save(request);

        addMemberDirectly(request.getGroup(), request.getUser().getId());
        log.info("Request {} approved, user {} added to group {}", requestId, request.getUser().getId(), groupId);
        return mapToJoinRequestResponse(request);
    }

    @Override
    public GroupJoinRequestResponse rejectRequest(Long groupId, Long requestId, Long reviewerId) {
        log.info("Reviewer {} rejecting request {} in group {}", reviewerId, requestId, groupId);
        getGroupEntityById(groupId);
        requireAdminOrOwner(groupId, reviewerId);

        GroupJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND));
        if (!request.getGroup().getId().equals(groupId) || request.getStatus() != JoinRequestStatus.PENDING) {
            throw new CustomException(ErrorCode.GROUP_JOIN_REQUEST_NOT_FOUND);
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setReviewedBy(getUserById(reviewerId));
        request.setReviewedAt(LocalDateTime.now());
        joinRequestRepository.save(request);
        log.info("Request {} rejected", requestId);
        return mapToJoinRequestResponse(request);
    }

    @Override
    public GroupResponse transferOwnership(Long groupId, Long newOwnerId, Long currentOwnerId) {
        log.info("Transferring ownership of group {} from {} to {}", groupId, currentOwnerId, newOwnerId);
        Group group = getGroupEntityById(groupId);
        requireOwner(group, currentOwnerId);

        if (newOwnerId.equals(currentOwnerId)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Không thể chuyển quyền cho chính mình.");
        }

        GroupMember newOwnerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, newOwnerId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        GroupMember oldOwnerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, currentOwnerId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        // Swap roles
        newOwnerMember.setRole(GroupMemberRole.OWNER);
        oldOwnerMember.setRole(GroupMemberRole.ADMIN);
        groupMemberRepository.save(newOwnerMember);
        groupMemberRepository.save(oldOwnerMember);

        // Update group owner
        group.setOwner(getUserById(newOwnerId));
        group = groupRepository.save(group);

        log.info("Ownership transferred: group {} new owner {}", groupId, newOwnerId);
        return mapToGroupResponse(group, currentOwnerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingRequests(Long groupId) {
        return joinRequestRepository.findByGroupIdAndStatus(groupId, JoinRequestStatus.PENDING).size();
    }

    private void addMemberDirectly(Group group, Long userId) {
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) return;
        Optional<GroupBan> kick = groupBanRepository
                .findByGroupIdAndUserIdAndBanTypeAndActiveTrue(group.getId(), userId, BanType.KICKED);
        if (kick.isPresent()) {
            throw new CustomException(ErrorCode.GROUP_JOIN_NOT_ALLOWED);
        }
        User user = getUserById(userId);
        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
        groupMemberRepository.save(member);
        log.info("User {} added to group {} as MEMBER", userId, group.getId());
    }

    private GroupJoinRequestResponse mapToJoinRequestResponse(GroupJoinRequest r) {
        return GroupJoinRequestResponse.builder()
                .id(r.getId())
                .groupId(r.getGroup().getId())
                .userId(r.getUser().getId())
                .userName(r.getUser().getFullName())
                .userAvatarUrl(r.getUser().getAvatarUrl())
                .answers(parseJsonList(r.getAnswer()))
                .questions(parseJsonList(r.getGroup().getJoinQuestion()))
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .build();
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            // Fallback: chuỗi cũ (single question) → wrap thành list
            return List.of(json);
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public Group getGroupEntityForUpdate(Long groupId, Long ownerId) {
        Group group = getGroupEntityById(groupId);
        requireOwner(group, ownerId);
        return group;
    }

    @Override
    public void updateGroupAvatar(Long groupId, String avatarUrl) {
        Group group = getGroupEntityById(groupId);
        group.setAvatarUrl(avatarUrl);
        groupRepository.save(group);
        log.info("Group {} avatar updated", groupId);
    }

    private GroupResponse mapToGroupResponse(Group group, Long requesterId) {
        long memberCount = groupMemberRepository.countByGroupId(group.getId());
        boolean isMember = requesterId != null
                && groupMemberRepository.existsByGroupIdAndUserId(group.getId(), requesterId);
        GroupMemberRole myRole = null;
        if (isMember) {
            myRole = groupMemberRepository.findByGroupIdAndUserId(group.getId(), requesterId)
                    .map(GroupMember::getRole).orElse(null);
        }

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .visibility(group.getVisibility())
                .ownerId(group.getOwner().getId())
                .ownerName(group.getOwner().getFullName())
                .avatarUrl(group.getAvatarUrl())
                .memberCount(memberCount)
                .isMember(isMember)
                .myRole(myRole)
                .shareToken(isMember ? group.getShareToken() : null)
                .requireApproval(group.getRequireApproval())
                .joinQuestions(parseJsonList(group.getJoinQuestion()))
                .hasPendingRequest(requesterId != null && !isMember
                        && joinRequestRepository.existsByGroupIdAndUserIdAndStatus(
                                group.getId(), requesterId, JoinRequestStatus.PENDING))
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private GroupBanResponse mapToBanResponse(GroupBan ban) {
        return GroupBanResponse.builder()
                .id(ban.getId())
                .groupId(ban.getGroup().getId())
                .userId(ban.getUser().getId())
                .userName(ban.getUser().getFullName())
                .userEmail(ban.getUser().getEmail())
                .banType(ban.getBanType())
                .reason(ban.getReason())
                .expiresAt(ban.getExpiresAt())
                .bannedById(ban.getBannedBy().getId())
                .bannedByName(ban.getBannedBy().getFullName())
                .active(ban.getActive())
                .createdAt(ban.getCreatedAt())
                .build();
    }
}
