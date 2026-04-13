package com.example.fileshareR.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "group_folders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupFolder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GroupFolder parent;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "share_token", length = 36, unique = true)
    private String shareToken;
}
