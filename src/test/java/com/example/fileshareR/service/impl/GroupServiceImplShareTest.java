package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.GroupLandingResponse;
import com.example.fileshareR.dto.response.GroupResponse;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupBan;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.BanType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.repository.GroupBanRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.GroupFolderService;
import com.example.fileshareR.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class GroupServiceImplShareTest {

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupBanRepository groupBanRepository;
    @Mock private UserService userService;
    @Mock private GroupFolderService groupFolderService;

    @InjectMocks private GroupServiceImpl groupService;

    private User owner;
    private User joiner;
    private Group group;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        owner.setFullName("Owner");

        joiner = new User();
        joiner.setId(2L);
        joiner.setEmail("joiner@example.com");
        joiner.setFullName("Joiner");

        group = Group.builder()
                .id(100L)
                .name("Nhóm DATN")
                .description("mô tả")
                .visibility(GroupVisibilityType.PRIVATE)
                .owner(owner)
                .shareToken("invite-tok")
                .build();

        lenient().when(groupMemberRepository.countByGroupId(100L)).thenReturn(3L);
        lenient().when(groupBanRepository.findByGroupIdAndUserIdAndBanTypeAndActiveTrue(
                eq(100L), anyLong(), eq(BanType.KICKED))).thenReturn(Optional.empty());
    }

    // ── getLandingByShareToken ───────────────────────────────────────────────

    @Test
    void getLandingByShareToken_notFound_throws() {
        when(groupRepository.findByShareToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getLandingByShareToken("ghost", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void getLandingByShareToken_anonymousUser_returnsIsMemberFalse() {
        when(groupRepository.findByShareToken("invite-tok")).thenReturn(Optional.of(group));

        GroupLandingResponse res = groupService.getLandingByShareToken("invite-tok", null);

        assertThat(res.getName()).isEqualTo("Nhóm DATN");
        assertThat(res.getIsMember()).isFalse();
        assertThat(res.getMemberCount()).isEqualTo(3L);
    }

    @Test
    void getLandingByShareToken_existingMember_returnsIsMemberTrue() {
        when(groupRepository.findByShareToken("invite-tok")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(true);

        GroupLandingResponse res = groupService.getLandingByShareToken("invite-tok", 2L);

        assertThat(res.getIsMember()).isTrue();
    }

    // ── joinViaShareToken ────────────────────────────────────────────────────

    @Test
    void joinViaShareToken_tokenNotFound_throws() {
        when(groupRepository.findByShareToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.joinViaShareToken("ghost", 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void joinViaShareToken_privateGroup_newUser_joinsSuccessfully() {
        when(groupRepository.findByShareToken("invite-tok")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(false);
        when(userService.getUserById(2L)).thenReturn(Optional.of(joiner));

        GroupResponse res = groupService.joinViaShareToken("invite-tok", 2L);

        assertThat(res.getId()).isEqualTo(100L);
        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    @Test
    void joinViaShareToken_alreadyMember_idempotent_noSave() {
        when(groupRepository.findByShareToken("invite-tok")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(true);

        groupService.joinViaShareToken("invite-tok", 2L);

        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    void joinViaShareToken_kickedUser_throwsJoinNotAllowed() {
        when(groupRepository.findByShareToken("invite-tok")).thenReturn(Optional.of(group));
        GroupBan ban = GroupBan.builder().id(999L).banType(BanType.KICKED).active(true).build();
        when(groupBanRepository.findByGroupIdAndUserIdAndBanTypeAndActiveTrue(
                100L, 2L, BanType.KICKED)).thenReturn(Optional.of(ban));

        assertThatThrownBy(() -> groupService.joinViaShareToken("invite-tok", 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_JOIN_NOT_ALLOWED);
    }

    // ── GroupResponse.shareToken visibility gating ───────────────────────────

    @Test
    void joinViaShareToken_newMember_responseIncludesShareToken() {
        when(groupRepository.findByShareToken("invite-tok")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 2L)).thenReturn(false, true);
        when(userService.getUserById(2L)).thenReturn(Optional.of(joiner));

        GroupResponse res = groupService.joinViaShareToken("invite-tok", 2L);

        // Sau khi join, isMember=true nên shareToken được trả về
        assertThat(res.getIsMember()).isTrue();
        assertThat(res.getShareToken()).isEqualTo("invite-tok");
    }
}
