package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.UpdateGroupCoverPresetRequest;
import com.example.fileshareR.dto.response.GroupCoverPresetResponse;
import com.example.fileshareR.entity.GroupCoverPreset;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.GroupCoverPresetRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.AvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupCoverPresetServiceImplTest {

    @Mock private GroupCoverPresetRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private AvatarService avatarService;

    private GroupCoverPresetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GroupCoverPresetServiceImpl(repository, userRepository, avatarService);
    }

    // ── listActive / listAll ────────────────────────────────────────────────

    @Test
    void listActive_returnsOnlyActivePresets() {
        GroupCoverPreset p = preset(1L, "Beach", "url1", true, 0);
        when(repository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(p));

        List<GroupCoverPresetResponse> out = service.listActive();

        assertThat(out).hasSize(1).extracting(GroupCoverPresetResponse::getName).containsExactly("Beach");
    }

    @Test
    void listAll_returnsAllPresets() {
        when(repository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(
                preset(1L, "A", "u1", true, 0),
                preset(2L, "B", "u2", false, 1)));

        assertThat(service.listAll()).hasSize(2);
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_emptyName_throws() {
        MultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service.create("   ", file, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void create_nullName_throws() {
        MultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service.create(null, file, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void create_nullFile_throws() {
        assertThatThrownBy(() -> service.create("Beach", null, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void create_emptyFile_throws() {
        MultipartFile empty = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.create("Beach", empty, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void create_happy_uploadsAndPersists() {
        MultipartFile file = new MockMultipartFile("file", "Cover.PNG", "image/png", new byte[]{1, 2});
        User admin = User.builder().id(7L).build();
        when(avatarService.uploadAvatar(eq(file), any(String.class))).thenReturn("https://cdn/cover.png");
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(repository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(
                preset(1L, "X", "u", true, 5)));
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> {
            GroupCoverPreset p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        GroupCoverPresetResponse out = service.create("  Beach View  ", file, 7L);

        assertThat(out.getId()).isEqualTo(100L);
        ArgumentCaptor<GroupCoverPreset> cap = ArgumentCaptor.forClass(GroupCoverPreset.class);
        verify(repository).save(cap.capture());
        GroupCoverPreset saved = cap.getValue();
        assertThat(saved.getName()).isEqualTo("Beach View"); // trimmed
        assertThat(saved.getImageUrl()).isEqualTo("https://cdn/cover.png");
        assertThat(saved.getIsActive()).isTrue();
        // Next display order = 5 + 1
        assertThat(saved.getDisplayOrder()).isEqualTo(6);
        assertThat(saved.getCreatedBy()).isEqualTo(admin);
    }

    @Test
    void create_firstPreset_displayOrderZero() {
        MultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(avatarService.uploadAvatar(any(), any())).thenReturn("u");
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(repository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of()); // empty
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("X", file, 7L);

        ArgumentCaptor<GroupCoverPreset> cap = ArgumentCaptor.forClass(GroupCoverPreset.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getDisplayOrder()).isZero();
    }

    @Test
    void create_adminNotFound_savesPresetWithNullCreator() {
        MultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(avatarService.uploadAvatar(any(), any())).thenReturn("u");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of());
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("X", file, 99L);

        ArgumentCaptor<GroupCoverPreset> cap = ArgumentCaptor.forClass(GroupCoverPreset.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getCreatedBy()).isNull();
    }

    @Test
    void create_filenameWithoutDot_defaultsToJpgExt() {
        MultipartFile file = new MockMultipartFile("file", "noext", "image/jpeg", new byte[]{1});
        when(avatarService.uploadAvatar(any(), any())).thenReturn("u");
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(repository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of());
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("X", file, 1L);

        // Verify avatarService called with .jpg fallback path
        ArgumentCaptor<String> pathCap = ArgumentCaptor.forClass(String.class);
        verify(avatarService).uploadAvatar(eq(file), pathCap.capture());
        assertThat(pathCap.getValue()).endsWith(".jpg");
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    void update_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        UpdateGroupCoverPresetRequest req = new UpdateGroupCoverPresetRequest();
        req.setName("New");

        assertThatThrownBy(() -> service.update(99L, req))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void update_allFields_persistsChanges() {
        GroupCoverPreset p = preset(1L, "Old", "u", true, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateGroupCoverPresetRequest req = new UpdateGroupCoverPresetRequest();
        req.setName("New Name");
        req.setIsActive(false);
        req.setDisplayOrder(5);

        service.update(1L, req);

        assertThat(p.getName()).isEqualTo("New Name");
        assertThat(p.getIsActive()).isFalse();
        assertThat(p.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void update_nullFields_leftAlone() {
        GroupCoverPreset p = preset(1L, "Old", "u", true, 3);
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateGroupCoverPresetRequest req = new UpdateGroupCoverPresetRequest();
        // All null

        service.update(1L, req);

        assertThat(p.getName()).isEqualTo("Old");
        assertThat(p.getIsActive()).isTrue();
        assertThat(p.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void update_blankName_skipped() {
        GroupCoverPreset p = preset(1L, "Old", "u", true, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        when(repository.save(any(GroupCoverPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateGroupCoverPresetRequest req = new UpdateGroupCoverPresetRequest();
        req.setName("   ");

        service.update(1L, req);

        assertThat(p.getName()).isEqualTo("Old");
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_notFound_throws() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_happy_callsRepoDelete() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static GroupCoverPreset preset(Long id, String name, String url, boolean active, int order) {
        return GroupCoverPreset.builder().id(id).name(name).imageUrl(url)
                .isActive(active).displayOrder(order).build();
    }
}
