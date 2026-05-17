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
import com.example.fileshareR.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * NotificationService implementation.
 *
 * Save-and-send pattern: DB persistence runs in the caller's transaction;
 * STOMP push is best-effort (try/catch swallows broker errors so business
 * flows like document download / payment confirm never fail because the
 * notification topic can't be reached).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private static final String USER_QUEUE_DESTINATION = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public NotificationResponse notifyUser(User recipient, NotificationType type,
                                           String title, String message,
                                           Long referenceId, String link) {
        if (recipient == null) {
            log.warn("Skip notify — recipient null (type={}, title={})", type, title);
            return null;
        }
        Notification entity = Notification.builder()
                .user(recipient)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .link(link)
                .isRead(false)
                .build();
        notificationRepository.save(entity);

        NotificationResponse payload = toResponse(entity);
        sendToUserBestEffort(recipient.getEmail(), payload);
        return payload;
    }

    @Override
    @Transactional
    public NotificationResponse notifyUser(Long recipientUserId, NotificationType type,
                                           String title, String message,
                                           Long referenceId, String link) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return notifyUser(recipient, type, title, message, referenceId, link);
    }

    @Override
    @Transactional
    public void notifyAllAdmins(NotificationType type, String title, String message,
                                Long referenceId, String link) {
        List<User> admins = userRepository.findAllByRole(UserRole.ADMIN);
        for (User admin : admins) {
            notifyUser(admin, type, title, message, referenceId, link);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listUnreadForUser(Long userId) {
        return notificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!n.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        n.setIsRead(true);
        return toResponse(notificationRepository.save(n));
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!n.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        notificationRepository.delete(n);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .referenceId(n.getReferenceId())
                .link(n.getLink())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    /**
     * convertAndSendToUser uses the STOMP user-principal set by
     * WebSocketConfig (email) to route /user/queue/notifications.
     * Wrapped in try/catch so broker downtime never propagates.
     */
    private void sendToUserBestEffort(String userEmail, NotificationResponse payload) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userEmail, USER_QUEUE_DESTINATION, payload);
        } catch (Exception ex) {
            log.warn("STOMP push failed for user={} type={} — DB row persisted, frontend will see it on next poll/login. cause={}",
                    userEmail, payload.getType(), ex.getMessage());
        }
    }
}
