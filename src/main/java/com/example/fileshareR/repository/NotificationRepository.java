package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Notification;
import com.example.fileshareR.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsRead(Long userId, Boolean isRead);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    /**
     * Used when admin reverses a "blocking" action (e.g. unban user) — stale
     * blocking-class notifications must be marked read so the frontend
     * loadInitial scan no longer re-triggers the blocking modal.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true "
            + "WHERE n.user.id = :userId AND n.type = :type AND n.isRead = false")
    int markAllAsReadByUserIdAndType(@Param("userId") Long userId,
                                     @Param("type") NotificationType type);
}
