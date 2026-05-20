package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateGroupFolderRequest;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupFolder;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupFolderRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.StorageQuotaService;
import com.example.fileshareR.service.UserService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupFolderServiceImplCoreTest {

    @Mock private GroupFolderRepository groupFolderRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private UserService userService;
    @Mock private StorageQuotaService storageQuotaService;

    private GroupFolderServiceImpl service;
    private User owner;
    private Group publicGroup;
    private Group privateGroup;

    @BeforeEach
    void setUp() {
        service = new GroupFolderServiceImpl(groupFolderRepository, groupRepository,
                groupMemberRepository, userService, documentRepository, storageQuotaService);
        owner = User.builder().id(1L).build();
        publicGroup = Group.builder().id(10L).visibility(GroupVisibilityType.PUBLIC)
                .owner(owner).shareToken("group-tok").build();
        privateGroup = Group.builder().id(20L).visibility(GroupVisibilityType.PRIVATE)
                .owner(owner).build();
        lenient().when(groupFolderRepository.save(any(GroupFolder.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── createFolder ────────────────────────────────────────────────────────

    @Test
    void createFolder_notAdmin_throws() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 5L)).thenReturn(Optional.empty());

        CreateGroupFolderRequest req = new CreateGroupFolderRequest();
        req.setName("F");

        assertThatThrownBy(() -> service.createFolder(10L, req, 5L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACCESS_DENIED);
    }

    @Test
    void createFolder_memberRole_throws() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        GroupMember m = GroupMember.builder().role(GroupMemberRole.MEMBER).build();
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 5L)).thenReturn(Optional.of(m));

        CreateGroupFolderRequest req = new CreateGroupFolderRequest();
        req.setName("F");

        assertThatThrownBy(() -> service.createFolder(10L, req, 5L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ADMIN_REQUIRED);
    }

    @Test
    void createFolder_publicGroup_assignsShareToken() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateGroupFolderRequest req = new CreateGroupFolderRequest();
        req.setName("Public Folder");

        service.createFolder(10L, req, 1L);

        // Public group → folder gets share token
        verify(groupFolderRepository).save(org.mockito.ArgumentMatchers.argThat(
                (GroupFolder f) -> f.getShareToken() != null));
    }

    @Test
    void createFolder_privateGroup_noShareToken() {
        when(groupRepository.findById(20L)).thenReturn(Optional.of(privateGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateGroupFolderRequest req = new CreateGroupFolderRequest();
        req.setName("Private Folder");

        service.createFolder(20L, req, 1L);

        verify(groupFolderRepository).save(org.mockito.ArgumentMatchers.argThat(
                (GroupFolder f) -> f.getShareToken() == null));
    }

    @Test
    void createFolder_parentInDifferentGroup_throws() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));
        GroupFolder parent = GroupFolder.builder().id(50L)
                .group(Group.builder().id(99L).build()).build();
        when(groupFolderRepository.findById(50L)).thenReturn(Optional.of(parent));

        CreateGroupFolderRequest req = new CreateGroupFolderRequest();
        req.setName("F");
        req.setParentId(50L);

        assertThatThrownBy(() -> service.createFolder(10L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_FOLDER_NOT_FOUND);
    }

    // ── getGroupFolders ─────────────────────────────────────────────────────

    @Test
    void getGroupFolders_publicGroup_anyoneCanList() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        GroupFolder f = GroupFolder.builder().id(50L).name("F")
                .group(publicGroup).createdBy(owner).build();
        when(groupFolderRepository.findByGroupId(10L)).thenReturn(List.of(f));

        assertThat(service.getGroupFolders(10L, null)).hasSize(1);
    }

    @Test
    void getGroupFolders_privateGroup_nonMember_throws() {
        when(groupRepository.findById(20L)).thenReturn(Optional.of(privateGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(20L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getGroupFolders(20L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
    }

    @Test
    void getRootFolders_filtersByParentIsNull() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupFolderRepository.findByGroupIdAndParentIsNull(10L))
                .thenReturn(List.of(GroupFolder.builder().id(1L).group(publicGroup).createdBy(owner).build()));

        assertThat(service.getRootFolders(10L, 1L)).hasSize(1);
    }

    @Test
    void getSubFolders_byParent_returnsList() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupFolderRepository.findByGroupIdAndParentId(10L, 50L))
                .thenReturn(List.of(GroupFolder.builder().id(60L).group(publicGroup).createdBy(owner).build()));

        assertThat(service.getSubFolders(10L, 50L, 1L)).hasSize(1);
    }

    // ── deleteFolder ────────────────────────────────────────────────────────

    @Test
    void deleteFolder_notInGroup_throws() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));
        GroupFolder folder = GroupFolder.builder().id(50L)
                .group(Group.builder().id(99L).build()).build();
        when(groupFolderRepository.findById(50L)).thenReturn(Optional.of(folder));

        assertThatThrownBy(() -> service.deleteFolder(10L, 50L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_FOLDER_NOT_FOUND);
    }

    @Test
    void deleteFolder_owner_freesQuotaAndDeletes() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));
        GroupFolder folder = GroupFolder.builder().id(50L).group(publicGroup).build();
        when(groupFolderRepository.findById(50L)).thenReturn(Optional.of(folder));
        when(documentRepository.sumFileSizeInGroupFolderTree(50L)).thenReturn(500L);

        service.deleteFolder(10L, 50L, 1L);

        verify(groupFolderRepository).delete(folder);
        verify(storageQuotaService).decrementGroupUsage(publicGroup, 500L);
    }

    // ── rotateShareToken ────────────────────────────────────────────────────

    @Test
    void rotateShareToken_privateGroup_throws() {
        GroupFolder folder = GroupFolder.builder().id(50L).group(privateGroup)
                .shareToken("old-tok").build();
        when(groupFolderRepository.findById(50L)).thenReturn(Optional.of(folder));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));

        assertThatThrownBy(() -> service.rotateShareToken(50L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rotateShareToken_publicGroup_setsNewToken() {
        GroupFolder folder = GroupFolder.builder().id(50L).group(publicGroup)
                .createdBy(owner).shareToken("old-tok").build();
        when(groupFolderRepository.findById(50L)).thenReturn(Optional.of(folder));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));

        service.rotateShareToken(50L, 1L);

        assertThat(folder.getShareToken()).isNotEqualTo("old-tok");
        assertThat(folder.getShareToken()).isNotNull();
    }

    // ── syncShareTokensWithGroupVisibility ──────────────────────────────────

    @Test
    void syncShareTokens_groupBecomesPublic_assignsTokensToNullOnes() {
        GroupFolder withToken = GroupFolder.builder().id(1L).shareToken("existing").build();
        GroupFolder noToken = GroupFolder.builder().id(2L).shareToken(null).build();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupFolderRepository.findByGroupId(10L)).thenReturn(List.of(withToken, noToken));

        service.syncShareTokensWithGroupVisibility(10L);

        assertThat(withToken.getShareToken()).isEqualTo("existing"); // unchanged
        assertThat(noToken.getShareToken()).isNotNull(); // assigned
    }

    @Test
    void syncShareTokens_groupBecomesPrivate_nullsAllTokens() {
        GroupFolder f1 = GroupFolder.builder().id(1L).shareToken("tok1").build();
        GroupFolder f2 = GroupFolder.builder().id(2L).shareToken(null).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(privateGroup));
        when(groupFolderRepository.findByGroupId(20L)).thenReturn(List.of(f1, f2));

        service.syncShareTokensWithGroupVisibility(20L);

        assertThat(f1.getShareToken()).isNull();
        assertThat(f2.getShareToken()).isNull();
    }

}
