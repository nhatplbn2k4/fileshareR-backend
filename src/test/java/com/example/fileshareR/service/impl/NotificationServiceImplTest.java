package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.NotificationResponse;
import com.example.fileshareR.entity.Notification;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.NotificationType;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.NotificationRepository;
import com.example.fileshareR.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userRepository, messagingTemplate);
    }

    // ── notifyUser(User, ...) ───────────────────────────────────────────────

    @Test
    void notifyUser_nullRecipient_returnsNull() {
        NotificationResponse out = service.notifyUser(
                (User) null, NotificationType.DOCUMENT_DOWNLOADED, "t", "m", 1L, "/x");

        assertThat(out).isNull();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyUser_validRecipient_persistsAndPushes() {
        User u = User.builder().id(1L).email("a@x.com").build();

        NotificationResponse out = service.notifyUser(
                u, NotificationType.DOCUMENT_DOWNLOADED, "Title", "Msg", 99L, "/docs");

        assertThat(out).isNotNull();
        assertThat(out.getTitle()).isEqualTo("Title");
        assertThat(out.getMessage()).isEqualTo("Msg");
        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq("a@x.com"), eq("/queue/notifications"), any(NotificationResponse.class));
    }

    @Test
    void notifyUser_stompFailure_dbStillPersisted() {
        User u = User.builder().id(1L).email("a@x.com").build();
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any(Object.class));

        // Should NOT throw — STOMP push is best-effort
        NotificationResponse out = service.notifyUser(
                u, NotificationType.DOCUMENT_DOWNLOADED, "T", "M", 1L, "/x");

        assertThat(out).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    // ── notifyUser(Long, ...) ───────────────────────────────────────────────

    @Test
    void notifyUserById_userMissing_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.notifyUser(
                99L, NotificationType.DOCUMENT_DOWNLOADED, "T", "M", 1L, "/x"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void notifyUserById_delegatesToUserOverload() {
        User u = User.builder().id(1L).email("a@x.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        NotificationResponse out = service.notifyUser(
                1L, NotificationType.DOCUMENT_DOWNLOADED, "T", "M", 99L, "/d");

        assertThat(out).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    // ── notifyAllAdmins ─────────────────────────────────────────────────────

    @Test
    void notifyAllAdmins_iteratesAllAdminAccounts() {
        User a1 = User.builder().id(1L).email("a1@x.com").build();
        User a2 = User.builder().id(2L).email("a2@x.com").build();
        when(userRepository.findAllByRole(UserRole.ADMIN)).thenReturn(List.of(a1, a2));

        service.notifyAllAdmins(NotificationType.PLATFORM_GROUP_CREATED,
                "New group", "msg", 7L, "/admin");

        verify(notificationRepository, org.mockito.Mockito.times(2)).save(any(Notification.class));
    }

    @Test
    void notifyAllAdmins_noAdmins_noop() {
        when(userRepository.findAllByRole(UserRole.ADMIN)).thenReturn(List.of());

        service.notifyAllAdmins(NotificationType.PLATFORM_GROUP_CREATED, "t", "m", 1L, "/x");

        verify(notificationRepository, never()).save(any());
    }

    // ── listForUser / listUnread / unreadCount ──────────────────────────────

    @Test
    void listForUser_pagedMappedToResponses() {
        User u = User.builder().id(1L).build();
        Notification n = Notification.builder().id(1L).user(u)
                .type(NotificationType.DOCUMENT_DOWNLOADED)
                .title("t").message("m").isRead(false).build();
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationResponse> out = service.listForUser(1L, Pageable.unpaged());

        assertThat(out.getContent()).hasSize(1);
        assertThat(out.getContent().get(0).getTitle()).isEqualTo("t");
    }

    @Test
    void listUnreadForUser_filtersByIsReadFalse() {
        User u = User.builder().id(1L).build();
        Notification n = Notification.builder().id(1L).user(u)
                .type(NotificationType.DOCUMENT_DOWNLOADED).isRead(false).build();
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(1L, false))
                .thenReturn(List.of(n));

        assertThat(service.listUnreadForUser(1L)).hasSize(1);
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(notificationRepository.countByUserIdAndIsRead(1L, false)).thenReturn(7L);

        assertThat(service.unreadCount(1L)).isEqualTo(7L);
    }

    // ── markAsRead ──────────────────────────────────────────────────────────

    @Test
    void markAsRead_notFound_throws() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void markAsRead_notOwner_throwsAccessDenied() {
        Notification n = Notification.builder().id(1L)
                .user(User.builder().id(99L).build()).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markAsRead(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void markAsRead_happy_setsIsReadAndSaves() {
        Notification n = Notification.builder().id(1L)
                .user(User.builder().id(1L).build()).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse out = service.markAsRead(1L, 1L);

        assertThat(n.getIsRead()).isTrue();
        assertThat(out.getIsRead()).isTrue();
    }

    // ── markAllAsRead ───────────────────────────────────────────────────────

    @Test
    void markAllAsRead_returnsRepoUpdateCount() {
        when(notificationRepository.markAllAsReadByUserId(1L)).thenReturn(5);

        assertThat(service.markAllAsRead(1L)).isEqualTo(5);
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_notFound_throws() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void delete_notOwner_throws() {
        Notification n = Notification.builder().id(1L)
                .user(User.builder().id(99L).build()).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.delete(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void delete_happy_callsRepoDelete() {
        Notification n = Notification.builder().id(1L)
                .user(User.builder().id(1L).build()).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        service.delete(1L, 1L);

        verify(notificationRepository).delete(n);
    }

    @Test
    void notifyUser_persistsCorrectEntityFields() {
        User u = User.builder().id(1L).email("a@x.com").build();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.notifyUser(u, NotificationType.DOCUMENT_DOWNLOADED, "Title", "Msg", 99L, "/link");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification entity = cap.getValue();
        assertThat(entity.getUser()).isEqualTo(u);
        assertThat(entity.getType()).isEqualTo(NotificationType.DOCUMENT_DOWNLOADED);
        assertThat(entity.getTitle()).isEqualTo("Title");
        assertThat(entity.getMessage()).isEqualTo("Msg");
        assertThat(entity.getReferenceId()).isEqualTo(99L);
        assertThat(entity.getLink()).isEqualTo("/link");
        assertThat(entity.getIsRead()).isFalse();
    }
}
