package com.example.fileshareR.entity;

import com.example.fileshareR.enums.GroupVisibilityType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupVisibilityType visibility = GroupVisibilityType.PRIVATE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "share_token", length = 36, unique = true)
    private String shareToken;

    @Column(name = "require_approval", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean requireApproval = false;

    @Column(name = "join_question", columnDefinition = "TEXT")
    private String joinQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "bonus_storage_bytes", nullable = false, columnDefinition = "bigint not null default 0")
    @Builder.Default
    private Long bonusStorageBytes = 0L;

    /**
     * Dung lượng chủ nhóm cấp phát cho nhóm, lấy ("giữ chỗ") từ quota cá nhân của owner.
     * Đây là nguồn quota chính của nhóm — thay cho việc nhóm tự có quota FREE riêng,
     * nhằm chặn việc tạo nhiều nhóm để lách thêm dung lượng miễn phí.
     */
    @Column(name = "allocated_quota_bytes", nullable = false, columnDefinition = "bigint not null default 0")
    @Builder.Default
    private Long allocatedQuotaBytes = 0L;

    @Column(name = "storage_used", nullable = false, columnDefinition = "bigint not null default 0")
    @Builder.Default
    private Long storageUsed = 0L;
}
