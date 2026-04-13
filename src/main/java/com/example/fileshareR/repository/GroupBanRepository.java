package com.example.fileshareR.repository;

import com.example.fileshareR.entity.GroupBan;
import com.example.fileshareR.enums.BanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupBanRepository extends JpaRepository<GroupBan, Long> {

    /**
     * Tìm ban upload còn hiệu lực:
     * - active = true
     * - banType != KICKED
     * - expiresAt > now (chưa hết hạn)
     */
    @Query("SELECT b FROM GroupBan b WHERE b.group.id = :groupId AND b.user.id = :userId " +
            "AND b.active = true AND b.banType != 'KICKED' " +
            "AND b.expiresAt > :now")
    Optional<GroupBan> findActiveUploadBan(@Param("groupId") Long groupId,
                                           @Param("userId") Long userId,
                                           @Param("now") LocalDateTime now);

    /**
     * Tìm lệnh KICKED còn hiệu lực (để chặn join lại)
     */
    Optional<GroupBan> findByGroupIdAndUserIdAndBanTypeAndActiveTrue(Long groupId, Long userId, BanType banType);

    /**
     * Danh sách tất cả ban còn active trong nhóm
     */
    List<GroupBan> findByGroupIdAndActiveTrue(Long groupId);

    /**
     * Tất cả ban (active hoặc không) của một user trong nhóm
     */
    List<GroupBan> findByGroupIdAndUserId(Long groupId, Long userId);
}
