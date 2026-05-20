package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.BanMemberRequest;
import com.example.fileshareR.dto.request.SetAdminRequest;
import com.example.fileshareR.dto.response.GroupBanResponse;
import com.example.fileshareR.dto.response.GroupResponse;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupBan;
import com.example.fileshareR.entity.GroupJoinRequest;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.BanType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.enums.JoinRequestStatus;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupBanRepository;
import com.example.fileshareR.repository.GroupCoverPresetRepository;
import com.example.fileshareR.repository.GroupFolderRepository;
import com.example.fileshareR.repository.GroupJoinRequestRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.service.FileStorageService;
import com.example.fileshareR.service.GroupFolderService;
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplAdminTest {

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupBanRepository groupBanRepository;
    @Mock private GroupJoinRequestRepository joinRequestRepository;
    @Mock private UserService userService;
    @Mock private GroupFolderService groupFolderService;
    @Mock private ObjectMapper objectMapper;
    @Mock private DocumentRepository documentRepository;
    @Mock private GroupFolderRepository groupFolderRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PlanRepository planRepository;
    @Mock private GroupCoverPresetRepository coverPresetRepository;
    @Mock private NotificationService notificationService;

    private GroupServiceImpl service;
    private User owner;
    private User member;
    private User admin;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new GroupServiceImpl(groupRepository, groupMemberRepository,
                groupBanRepository, joinRequestRepository, userService,
                groupFolderService, objectMapper, documentRepository, groupFolderRepository,
                fileStorageService, planRepository, coverPresetRepository, notificationService);

        owner = User.builder().id(1L).fullName("Owner").email("owner@x.com").build();
        member = User.builder().id(2L).fullName("Member").email("m@x.com").build();
        admin = User.builder().id(3L).fullName("Admin").email("a@x.com").build();
        group = Group.builder().id(100L).name("G").visibility(GroupVisibilityType.PUBLIC).owner(owner).build();

        lenient().when(groupMemberRepository.countByGroupId(anyLong())).thenReturn(3L);
    }

    // ── setAdmin ────────────────────────────────────────────────────────────

    @Test
    void setAdmin_notOwner_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        SetAdminRequest req = new SetAdminRequest();
        req.setUserId(2L);
        req.setIsAdmin(true);

        assertThatThrownBy(() -> service.setAdmin(100L, req, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void setAdmin_targetIsOwner_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        SetAdminRequest req = new SetAdminRequest();
        req.setUserId(1L); // owner's id
        req.setIsAdmin(true);

        assertThatThrownBy(() -> service.setAdmin(100L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void setAdmin_targetMissing_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        SetAdminRequest req = new SetAdminRequest();
        req.setUserId(2L);
        req.setIsAdmin(true);

        assertThatThrownBy(() -> service.setAdmin(100L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    void setAdmin_grant_setsAdminRole() {
        GroupMember m = GroupMember.builder().user(member).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(Optional.of(m));

        SetAdminRequest req = new SetAdminRequest();
        req.setUserId(2L);
        req.setIsAdmin(true);

        service.setAdmin(100L, req, 1L);

        assertThat(m.getRole()).isEqualTo(GroupMemberRole.ADMIN);
        verify(notificationService).notifyUser(eq(member), any(), any(), any(), eq(100L), any());
    }

    @Test
    void setAdmin_revoke_setsMemberRole() {
        GroupMember m = GroupMember.builder().user(member).role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(Optional.of(m));

        SetAdminRequest req = new SetAdminRequest();
        req.setUserId(2L);
        req.setIsAdmin(false);

        service.setAdmin(100L, req, 1L);

        assertThat(m.getRole()).isEqualTo(GroupMemberRole.MEMBER);
    }

    // ── banMember ───────────────────────────────────────────────────────────

    @Test
    void banMember_notAdmin_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 99L)).thenReturn(Optional.empty());

        BanMemberRequest req = new BanMemberRequest();
        req.setUserId(2L);
        req.setBanType(BanType.KICKED);

        assertThatThrownBy(() -> service.banMember(100L, req, 99L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void banMember_targetIsOwner_throws() {
        GroupMember reqMember = GroupMember.builder().user(admin).role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 3L)).thenReturn(Optional.of(reqMember));

        BanMemberRequest req = new BanMemberRequest();
        req.setUserId(1L); // owner
        req.setBanType(BanType.KICKED);

        assertThatThrownBy(() -> service.banMember(100L, req, 3L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void banMember_kickType_removesMembership_andNotifies() {
        GroupMember adminMember = GroupMember.builder().user(admin).role(GroupMemberRole.OWNER).build();
        GroupMember targetMember = GroupMember.builder().user(member).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetMember));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(userService.getUserById(2L)).thenReturn(Optional.of(member));
        when(groupBanRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(List.of());
        when(groupBanRepository.save(any(GroupBan.class))).thenAnswer(inv -> inv.getArgument(0));

        BanMemberRequest req = new BanMemberRequest();
        req.setUserId(2L);
        req.setBanType(BanType.KICKED);
        req.setReason("spamming");

        GroupBanResponse out = service.banMember(100L, req, 1L); // owner requester

        assertThat(out).isNotNull();
        verify(groupMemberRepository).delete(targetMember);
        verify(notificationService).notifyUser(eq(member), any(), any(), any(), eq(100L), any());
    }

    @Test
    void banMember_uploadBan_keepsMembership() {
        GroupMember adminMember = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        GroupMember targetMember = GroupMember.builder().user(member).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetMember));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(userService.getUserById(2L)).thenReturn(Optional.of(member));
        when(groupBanRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(List.of());
        when(groupBanRepository.save(any(GroupBan.class))).thenAnswer(inv -> inv.getArgument(0));

        BanMemberRequest req = new BanMemberRequest();
        req.setUserId(2L);
        req.setBanType(BanType.UPLOAD_BAN_7_DAYS);

        service.banMember(100L, req, 1L);

        verify(groupMemberRepository, never()).delete(any(GroupMember.class));
    }

    @Test
    void banMember_adminTriesBanAnotherAdmin_throws() {
        GroupMember adminRequester = GroupMember.builder().user(admin).role(GroupMemberRole.ADMIN).build();
        GroupMember targetAdmin = GroupMember.builder().user(member).role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 3L))
                .thenReturn(Optional.of(adminRequester));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetAdmin));

        BanMemberRequest req = new BanMemberRequest();
        req.setUserId(2L);
        req.setBanType(BanType.KICKED);

        assertThatThrownBy(() -> service.banMember(100L, req, 3L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void banMember_replaceExistingBan_deactivatesOldFirst() {
        GroupMember adminMember = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        GroupMember targetMember = GroupMember.builder().user(member).role(GroupMemberRole.MEMBER).build();
        GroupBan oldBan = GroupBan.builder().id(50L).active(true).banType(BanType.UPLOAD_BAN_7_DAYS).build();

        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetMember));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(userService.getUserById(2L)).thenReturn(Optional.of(member));
        when(groupBanRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(List.of(oldBan));
        when(groupBanRepository.save(any(GroupBan.class))).thenAnswer(inv -> inv.getArgument(0));

        BanMemberRequest req = new BanMemberRequest();
        req.setUserId(2L);
        req.setBanType(BanType.UPLOAD_BAN_1_MONTH);

        service.banMember(100L, req, 1L);

        assertThat(oldBan.getActive()).isFalse();
    }

    // ── unbanMember ─────────────────────────────────────────────────────────

    @Test
    void unbanMember_noActiveBans_throws() {
        GroupMember adminRequester = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminRequester));
        when(groupBanRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.unbanMember(100L, 2L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    void unbanMember_deactivatesAllActive() {
        GroupMember adminRequester = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        GroupBan active = GroupBan.builder().active(true).build();
        GroupBan inactive = GroupBan.builder().active(false).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminRequester));
        when(groupBanRepository.findByGroupIdAndUserId(100L, 2L))
                .thenReturn(List.of(active, inactive));

        service.unbanMember(100L, 2L, 1L);

        assertThat(active.getActive()).isFalse();
        verify(groupBanRepository).save(active);
        verify(groupBanRepository, never()).save(inactive);
    }

    // ── getBans ─────────────────────────────────────────────────────────────

    @Test
    void getBans_admin_listsActiveBans() {
        GroupMember adminRequester = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        GroupBan b = GroupBan.builder().id(50L).group(group).user(member)
                .bannedBy(owner).banType(BanType.UPLOAD_BAN_7_DAYS)
                .active(true).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminRequester));
        when(groupBanRepository.findByGroupIdAndActiveTrue(100L)).thenReturn(List.of(b));

        assertThat(service.getBans(100L, 1L)).hasSize(1);
    }

    // ── transferOwnership ───────────────────────────────────────────────────

    @Test
    void transferOwnership_notOwner_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.transferOwnership(100L, 5L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void transferOwnership_newOwnerNotMember_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferOwnership(100L, 5L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    void transferOwnership_happy_promotesNewOwner() {
        User newOwner = User.builder().id(5L).fullName("New").build();
        GroupMember newOwnerMember = GroupMember.builder().user(newOwner).role(GroupMemberRole.MEMBER).build();
        GroupMember oldOwnerMember = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 5L))
                .thenReturn(Optional.of(newOwnerMember));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(oldOwnerMember));
        when(userService.getUserById(5L)).thenReturn(Optional.of(newOwner));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupResponse out = service.transferOwnership(100L, 5L, 1L);

        assertThat(group.getOwner()).isEqualTo(newOwner);
        assertThat(newOwnerMember.getRole()).isEqualTo(GroupMemberRole.OWNER);
        assertThat(oldOwnerMember.getRole()).isEqualTo(GroupMemberRole.ADMIN); // demoted
        assertThat(out).isNotNull();
    }

    // ── approveRequest / rejectRequest ──────────────────────────────────────

    @Test
    void approveRequest_notAdmin_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveRequest(100L, 50L, 99L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void approveRequest_happy_addsMemberAndUpdatesRequest() {
        GroupMember adminRequester = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        User requester = User.builder().id(2L).build();
        GroupJoinRequest joinReq = GroupJoinRequest.builder()
                .id(50L).group(group).user(requester).status(JoinRequestStatus.PENDING).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminRequester));
        when(joinRequestRepository.findById(50L)).thenReturn(Optional.of(joinReq));
        when(joinRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(userService.getUserById(2L)).thenReturn(Optional.of(requester));
        when(groupBanRepository.findByGroupIdAndUserIdAndBanTypeAndActiveTrue(100L, 2L, BanType.KICKED))
                .thenReturn(Optional.empty());
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(false);

        service.approveRequest(100L, 50L, 1L);

        assertThat(joinReq.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    @Test
    void rejectRequest_happy_setsRejected() {
        GroupMember adminRequester = GroupMember.builder().user(owner).role(GroupMemberRole.OWNER).build();
        GroupJoinRequest joinReq = GroupJoinRequest.builder()
                .id(50L).group(group).user(member).status(JoinRequestStatus.PENDING).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(adminRequester));
        when(joinRequestRepository.findById(50L)).thenReturn(Optional.of(joinReq));
        when(joinRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        service.rejectRequest(100L, 50L, 1L);

        assertThat(joinReq.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
    }

    @Test
    void countPendingRequests_returnsListSize() {
        GroupJoinRequest r1 = GroupJoinRequest.builder().id(1L).build();
        GroupJoinRequest r2 = GroupJoinRequest.builder().id(2L).build();
        when(joinRequestRepository.findByGroupIdAndStatus(100L, JoinRequestStatus.PENDING))
                .thenReturn(List.of(r1, r2));

        assertThat(service.countPendingRequests(100L)).isEqualTo(2L);
    }
}
