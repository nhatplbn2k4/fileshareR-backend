package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateGroupFolderRequest;
import com.example.fileshareR.dto.response.GroupFolderResponse;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupFolder;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.repository.GroupFolderRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupFolderServiceImplShareTest {

    @Mock private GroupFolderRepository groupFolderRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserService userService;

    @InjectMocks private GroupFolderServiceImpl service;

    private User owner;
    private Group publicGroup;
    private Group privateGroup;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        owner.setFullName("Owner");

        publicGroup = Group.builder()
                .id(10L)
                .name("Public Group")
                .visibility(GroupVisibilityType.PUBLIC)
                .owner(owner)
                .shareToken("group-tok")
                .build();

        privateGroup = Group.builder()
                .id(20L)
                .name("Private Group")
                .visibility(GroupVisibilityType.PRIVATE)
                .owner(owner)
                .shareToken("private-group-tok")
                .build();

        lenient().when(groupFolderRepository.save(any(GroupFolder.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private GroupFolder folder(long id, Group group, String token) {
        return GroupFolder.builder()
                .id(id)
                .group(group)
                .createdBy(owner)
                .name("folder-" + id)
                .shareToken(token)
                .build();
    }

    private GroupMember adminMember(Long userId) {
        GroupMember m = new GroupMember();
        m.setRole(GroupMemberRole.ADMIN);
        return m;
    }

    private GroupMember memberMember(Long userId) {
        GroupMember m = new GroupMember();
        m.setRole(GroupMemberRole.MEMBER);
        return m;
    }

    // ── createFolder: auto-gen token ─────────────────────────────────────────

    @Test
    void createFolder_inPublicGroup_generatesShareToken() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(adminMember(1L)));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateGroupFolderRequest req = CreateGroupFolderRequest.builder().name("docs").build();
        GroupFolderResponse res = service.createFolder(10L, req, 1L);

        assertThat(res.getShareToken()).isNotBlank();
    }

    @Test
    void createFolder_inPrivateGroup_shareTokenIsNull() {
        when(groupRepository.findById(20L)).thenReturn(Optional.of(privateGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(adminMember(1L)));
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateGroupFolderRequest req = CreateGroupFolderRequest.builder().name("secret").build();
        GroupFolderResponse res = service.createFolder(20L, req, 1L);

        assertThat(res.getShareToken()).isNull();
    }

    // ── getByShareToken ──────────────────────────────────────────────────────

    @Test
    void getByShareToken_tokenNotFound_throws() {
        when(groupFolderRepository.findByShareToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByShareToken("ghost"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_FOLDER_NOT_FOUND);
    }

    @Test
    void getByShareToken_groupWentPrivate_throws404() {
        GroupFolder f = folder(200L, privateGroup, "stale-tok");
        when(groupFolderRepository.findByShareToken("stale-tok")).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.getByShareToken("stale-tok"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_FOLDER_NOT_FOUND);
    }

    @Test
    void getByShareToken_publicGroup_ok() {
        GroupFolder f = folder(201L, publicGroup, "live-tok");
        when(groupFolderRepository.findByShareToken("live-tok")).thenReturn(Optional.of(f));

        GroupFolderResponse res = service.getByShareToken("live-tok");

        assertThat(res.getId()).isEqualTo(201L);
        assertThat(res.getShareToken()).isEqualTo("live-tok");
    }

    // ── rotateShareToken ─────────────────────────────────────────────────────

    @Test
    void rotateShareToken_notAdmin_throws() {
        GroupFolder f = folder(300L, publicGroup, "old-tok");
        when(groupFolderRepository.findById(300L)).thenReturn(Optional.of(f));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 5L))
                .thenReturn(Optional.of(memberMember(5L)));

        assertThatThrownBy(() -> service.rotateShareToken(300L, 5L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ADMIN_REQUIRED);
    }

    @Test
    void rotateShareToken_privateGroup_throwsBadRequest() {
        GroupFolder f = folder(301L, privateGroup, null);
        when(groupFolderRepository.findById(301L)).thenReturn(Optional.of(f));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(adminMember(1L)));

        assertThatThrownBy(() -> service.rotateShareToken(301L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rotateShareToken_happyPath_newToken() {
        GroupFolder f = folder(302L, publicGroup, "old-tok");
        when(groupFolderRepository.findById(302L)).thenReturn(Optional.of(f));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(adminMember(1L)));

        GroupFolderResponse res = service.rotateShareToken(302L, 1L);

        assertThat(res.getShareToken()).isNotBlank().isNotEqualTo("old-tok");
    }

    // ── syncShareTokensWithGroupVisibility ───────────────────────────────────

    @Test
    void sync_publicGroup_generatesTokensForNullOnes() {
        GroupFolder f1 = folder(400L, publicGroup, null);
        GroupFolder f2 = folder(401L, publicGroup, "already-tok");
        when(groupRepository.findById(10L)).thenReturn(Optional.of(publicGroup));
        when(groupFolderRepository.findByGroupId(10L)).thenReturn(List.of(f1, f2));

        service.syncShareTokensWithGroupVisibility(10L);

        assertThat(f1.getShareToken()).isNotBlank();
        assertThat(f2.getShareToken()).isEqualTo("already-tok"); // không đổi
    }

    @Test
    void sync_privateGroup_clearsAllTokens() {
        GroupFolder f1 = folder(500L, privateGroup, "tok1");
        GroupFolder f2 = folder(501L, privateGroup, "tok2");
        when(groupRepository.findById(20L)).thenReturn(Optional.of(privateGroup));
        when(groupFolderRepository.findByGroupId(20L)).thenReturn(List.of(f1, f2));

        service.syncShareTokensWithGroupVisibility(20L);

        assertThat(f1.getShareToken()).isNull();
        assertThat(f2.getShareToken()).isNull();
    }
}
