package com.example.fileshareR.service;

import com.example.fileshareR.dto.response.NotificationResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service contract for persisting notifications and pushing them
 * over STOMP. All emit methods are best-effort — STOMP failure must NOT
 * fail the caller's business transaction.
 */
public interface NotificationService {

    /** Persist + push to a single user. Returns the persisted DTO. */
    NotificationResponse notifyUser(User recipient, NotificationType type,
                                    String title, String message,
                                    Long referenceId, String link);

    /** Convenience overload — emit by user ID (fetches user). */
    NotificationResponse notifyUser(Long recipientUserId, NotificationType type,
                                    String title, String message,
                                    Long referenceId, String link);

    /** Push to every ADMIN user (admin dashboard inbox). */
    void notifyAllAdmins(NotificationType type, String title, String message,
                         Long referenceId, String link);

    /** Read helpers used by REST + initial frontend load. */
    Page<NotificationResponse> listForUser(Long userId, Pageable pageable);
    List<NotificationResponse> listUnreadForUser(Long userId);
    long unreadCount(Long userId);

    NotificationResponse markAsRead(Long userId, Long notificationId);
    int markAllAsRead(Long userId);
    void delete(Long userId, Long notificationId);
}
