package com.example.fileshareR.entity;

import com.example.fileshareR.enums.BanType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_bans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupBan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banned_by_id", nullable = false)
    private User bannedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "ban_type", nullable = false, length = 30)
    private BanType banType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /**
     * Thời điểm hết hạn ban.
     * - null: KICKED (bị đuổi vĩnh viễn, không tự hết hạn)
     * - non-null: ban upload có thời hạn
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
