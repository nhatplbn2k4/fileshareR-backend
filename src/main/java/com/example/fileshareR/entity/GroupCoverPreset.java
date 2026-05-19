package com.example.fileshareR.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "group_cover_presets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupCoverPreset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;
}
