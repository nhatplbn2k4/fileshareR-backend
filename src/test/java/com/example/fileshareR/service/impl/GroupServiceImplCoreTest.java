package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.BanMemberRequest;
import com.example.fileshareR.dto.request.CreateGroupRequest;
import com.example.fileshareR.dto.request.SetAdminRequest;
import com.example.fileshareR.dto.request.UpdateGroupRequest;
import com.example.fileshareR.dto.response.GroupBanResponse;
import com.example.fileshareR.dto.response.GroupMemberResponse;
import com.example.fileshareR.dto.response.GroupResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupBan;
import com.example.fileshareR.entity.GroupCoverPreset;
import com.example.fileshareR.entity.GroupFolder;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.BanType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
class GroupServiceImplCoreTest {

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
    @Mock private com.example.fileshareR.service.StorageQuotaService storageQuotaService;

    private GroupServiceImpl service;
    private User owner;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new GroupServiceImpl(groupRepository, groupMemberRepository,
                groupBanRepository, joinRequestRepository, userService,
                groupFolderService, objectMapper, documentRepository, groupFolderRepository,
                fileStorageService, planRepository, coverPresetRepository, notificationService,
                storageQuotaService);

        owner = User.builder().id(1L).email("owner@x.com").fullName("Owner").build();
        group = Group.builder().id(100L).name("G").description("d")
                .visibility(GroupVisibilityType.PUBLIC).owner(owner).build();

        // Stubs frequently used across tests — lenient so unused tests don't fail
        lenient().when(groupMemberRepository.countByGroupId(anyLong())).thenReturn(3L);
    }

    // ── createGroup ─────────────────────────────────────────────────────────

    @Test
    void createGroup_persistsGroupAndOwnerMembership() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(100L);
            return g;
        });

        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("New Group");
        req.setDescription("d");
        req.setVisibility(GroupVisibilityType.PUBLIC);

        GroupResponse out = service.createGroup(req, 1L);

        assertThat(out.getName()).isEqualTo("New Group");
        verify(groupMemberRepository).save(any(GroupMember.class));
        verify(notificationService).notifyAllAdmins(any(), any(), any(), eq(100L), any());
    }

    @Test
    void createGroup_allocatesQuotaFromOwner_reservesAndSaves() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(storageQuotaService.getUserAvailableQuota(owner)).thenReturn(100L * 1024 * 1024);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(100L);
            return g;
        });

        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("G");
        req.setAllocatedQuotaBytes(30L * 1024 * 1024);

        ArgumentCaptor<Group> cap = ArgumentCaptor.forClass(Group.class);
        service.createGroup(req, 1L);

        verify(groupRepository).save(cap.capture());
        assertThat(cap.getValue().getAllocatedQuotaBytes()).isEqualTo(30L * 1024 * 1024);
        assertThat(cap.getValue().getPlan()).isNull(); // không còn gói FREE riêng
    }

    @Test
    void createGroup_allocationExceedsAvailable_throws() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(storageQuotaService.getUserAvailableQuota(owner)).thenReturn(10L * 1024 * 1024);

        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("G");
        req.setAllocatedQuotaBytes(50L * 1024 * 1024); // vượt available

        assertThatThrownBy(() -> service.createGroup(req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_QUOTA_ALLOCATION_EXCEEDS_AVAILABLE);
    }

    @Test
    void createGroup_userNotFound_throws() {
        when(userService.getUserById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGroup(new CreateGroupRequest(), 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void createGroup_nullVisibility_defaultsToPrivate() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateGroupRequest req = new CreateGroupRequest();
        req.setName("X");
        // No visibility set

        GroupResponse out = service.createGroup(req, 1L);

        assertThat(out.getVisibility()).isEqualTo(GroupVisibilityType.PRIVATE);
    }

    // ── updateGroup ─────────────────────────────────────────────────────────

    @Test
    void updateGroup_notOwner_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setName("New");

        assertThatThrownBy(() -> service.updateGroup(100L, req, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void updateGroup_visibilityChange_triggersFolderSync() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setVisibility(GroupVisibilityType.PRIVATE);

        service.updateGroup(100L, req, 1L);

        assertThat(group.getVisibility()).isEqualTo(GroupVisibilityType.PRIVATE);
        verify(groupFolderService).syncShareTokensWithGroupVisibility(100L);
    }

    @Test
    void updateGroup_reduceQuotaBelowUsed_throws() {
        group.setStorageUsed(80L * 1024 * 1024);
        group.setAllocatedQuotaBytes(100L * 1024 * 1024);
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setAllocatedQuotaBytes(50L * 1024 * 1024); // thấp hơn 80MB đã dùng

        assertThatThrownBy(() -> service.updateGroup(100L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_QUOTA_BELOW_USED);
    }

    @Test
    void updateGroup_increaseQuotaWithinBudget_succeeds() {
        group.setStorageUsed(10L * 1024 * 1024);
        group.setAllocatedQuotaBytes(20L * 1024 * 1024);
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        // available của owner (đã trừ allocation hiện tại) = 100MB → budget = 100MB + 20MB cũ
        when(storageQuotaService.getUserAvailableQuota(owner)).thenReturn(100L * 1024 * 1024);

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setAllocatedQuotaBytes(50L * 1024 * 1024);

        service.updateGroup(100L, req, 1L);

        assertThat(group.getAllocatedQuotaBytes()).isEqualTo(50L * 1024 * 1024);
    }

    @Test
    void updateGroup_coverPresetId_resolvesUrlFromRepository() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(coverPresetRepository.findById(5L)).thenReturn(Optional.of(
                GroupCoverPreset.builder().id(5L).imageUrl("https://cdn/cover.png").build()));

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setCoverPresetId(5L);

        service.updateGroup(100L, req, 1L);

        assertThat(group.getCoverImageUrl()).isEqualTo("https://cdn/cover.png");
    }

    @Test
    void updateGroup_coverPresetNotFound_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(coverPresetRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setCoverPresetId(99L);

        assertThatThrownBy(() -> service.updateGroup(100L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void updateGroup_avatarOnly_updatesField() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateGroupRequest req = new UpdateGroupRequest();
        req.setAvatarUrl("https://av.png");

        service.updateGroup(100L, req, 1L);

        assertThat(group.getAvatarUrl()).isEqualTo("https://av.png");
    }

    // ── deleteGroup ─────────────────────────────────────────────────────────

    @Test
    void deleteGroup_notOwner_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.deleteGroup(100L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
        verify(groupRepository, never()).delete(any());
    }

    @Test
    void deleteGroup_owner_cascadesAndDeletes() {
        Document doc = Document.builder().id(50L).fileUrl("a/b").build();
        GroupMember m = GroupMember.builder().user(owner).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(documentRepository.findByGroupId(100L)).thenReturn(List.of(doc));
        when(groupFolderRepository.findByGroupId(100L)).thenReturn(List.of());
        when(joinRequestRepository.findByGroupIdAndStatus(eq(100L), any())).thenReturn(List.of());
        when(groupBanRepository.findByGroupIdAndActiveTrue(100L)).thenReturn(List.of());
        when(groupMemberRepository.findByGroupId(100L)).thenReturn(List.of(m));

        service.deleteGroup(100L, 1L);

        verify(documentRepository).delete(doc);
        verify(fileStorageService).deleteFile("a/b");
        verify(groupMemberRepository).delete(m);
        verify(groupRepository).delete(group);
    }

    // ── adminDeleteGroup ────────────────────────────────────────────────────

    @Test
    void adminDeleteGroup_notifiesAllFormerMembers() {
        User member = User.builder().id(2L).fullName("M").build();
        GroupMember m = GroupMember.builder().user(member).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupId(100L)).thenReturn(List.of(m));
        when(documentRepository.findByGroupId(100L)).thenReturn(List.of());
        when(groupFolderRepository.findByGroupId(100L)).thenReturn(List.of());
        when(joinRequestRepository.findByGroupIdAndStatus(eq(100L), any())).thenReturn(List.of());
        when(groupBanRepository.findByGroupIdAndActiveTrue(100L)).thenReturn(List.of());

        service.adminDeleteGroup(100L);

        verify(groupRepository).delete(group);
        verify(notificationService).notifyUser(eq(2L), any(), any(), any(), eq(100L), any());
    }

    // ── getMyGroups + getGroupById ──────────────────────────────────────────

    @Test
    void getMyGroups_mapsMembershipsToGroupResponses() {
        GroupMember m = GroupMember.builder().group(group).user(owner).role(GroupMemberRole.OWNER).build();
        when(groupMemberRepository.findByUserId(1L)).thenReturn(List.of(m));

        List<GroupResponse> out = service.getMyGroups(1L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getId()).isEqualTo(100L);
    }

    @Test
    void getGroupById_existingGroup_returnsResponse() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        assertThat(service.getGroupById(100L, 1L).getId()).isEqualTo(100L);
    }

    @Test
    void getGroupById_notFound_throws() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupById(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    // ── searchPublicGroups + searchGroups ───────────────────────────────────

    @Test
    void searchPublicGroups_noKeyword_listsByVisibility() {
        when(groupRepository.findByVisibility(GroupVisibilityType.PUBLIC)).thenReturn(List.of(group));

        assertThat(service.searchPublicGroups(null)).hasSize(1);
        assertThat(service.searchPublicGroups("  ")).hasSize(1);
    }

    @Test
    void searchPublicGroups_keyword_usesIgnoreCaseMatch() {
        when(groupRepository.findByNameContainingIgnoreCaseAndVisibility("D", GroupVisibilityType.PUBLIC))
                .thenReturn(List.of(group));

        assertThat(service.searchPublicGroups("D")).hasSize(1);
    }

    @Test
    void searchGroups_noKeyword_findsAll() {
        when(groupRepository.findAll()).thenReturn(List.of(group));

        assertThat(service.searchGroups("", 1L)).hasSize(1);
    }

    @Test
    void searchGroups_withKeyword_filters() {
        when(groupRepository.findByNameContainingIgnoreCase("foo")).thenReturn(List.of(group));

        assertThat(service.searchGroups("foo", 1L)).hasSize(1);
    }

    // ── joinGroup ───────────────────────────────────────────────────────────

    @Test
    void joinGroup_privateGroup_throws() {
        Group priv = Group.builder().id(100L).visibility(GroupVisibilityType.PRIVATE).owner(owner).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(priv));

        assertThatThrownBy(() -> service.joinGroup(100L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_JOIN_NOT_ALLOWED);
    }

    @Test
    void joinGroup_requiresApproval_throwsBadRequest() {
        Group approvalGroup = Group.builder().id(100L)
                .visibility(GroupVisibilityType.PUBLIC)
                .requireApproval(true).owner(owner).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(approvalGroup));

        assertThatThrownBy(() -> service.joinGroup(100L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void joinGroup_publicWithoutApproval_addsMember() {
        Group pub = Group.builder().id(100L)
                .visibility(GroupVisibilityType.PUBLIC).owner(owner).build();
        User member = User.builder().id(2L).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(pub));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(false);
        when(userService.getUserById(2L)).thenReturn(Optional.of(member));
        when(groupBanRepository.findByGroupIdAndUserIdAndBanTypeAndActiveTrue(100L, 2L, BanType.KICKED))
                .thenReturn(Optional.empty());

        service.joinGroup(100L, 2L);

        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    // ── leaveGroup ──────────────────────────────────────────────────────────

    @Test
    void leaveGroup_ownerCannotLeave_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.leaveGroup(100L, 1L)) // owner id = 1
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void leaveGroup_notMember_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveGroup(100L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    void leaveGroup_member_deletes() {
        User member = User.builder().id(2L).build();
        GroupMember m = GroupMember.builder().user(member).group(group).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(Optional.of(m));

        service.leaveGroup(100L, 2L);

        verify(groupMemberRepository).delete(m);
    }

    // ── getMembers ──────────────────────────────────────────────────────────

    @Test
    void getMembers_privateGroup_nonMember_throws() {
        Group priv = Group.builder().id(100L).visibility(GroupVisibilityType.PRIVATE).owner(owner).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(priv));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> service.getMembers(100L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
    }

    @Test
    void getMembers_privateGroup_nullRequester_throws() {
        Group priv = Group.builder().id(100L).visibility(GroupVisibilityType.PRIVATE).owner(owner).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(priv));

        assertThatThrownBy(() -> service.getMembers(100L, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
    }

    @Test
    void getMembers_publicGroup_listsMembers() {
        User member = User.builder().id(2L).fullName("M").email("m@x.com").build();
        GroupMember m = GroupMember.builder().user(member).role(GroupMemberRole.MEMBER)
                .joinedAt(LocalDateTime.now()).build();
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupId(100L)).thenReturn(List.of(m));
        when(groupBanRepository.findActiveUploadBan(eq(100L), eq(2L), any())).thenReturn(Optional.empty());

        List<GroupMemberResponse> out = service.getMembers(100L, 1L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getEmail()).isEqualTo("m@x.com");
    }

    // ── unbanMember ─────────────────────────────────────────────────────────

    @Test
    void unbanMember_notAdmin_throws() {
        when(groupRepository.findById(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unbanMember(100L, 5L, 2L))
                .isInstanceOf(CustomException.class);
    }
}
