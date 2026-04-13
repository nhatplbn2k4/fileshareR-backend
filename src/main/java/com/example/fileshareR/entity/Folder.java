package com.example.fileshareR.entity;

import com.example.fileshareR.enums.FolderVisibilityType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parent;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    @Builder.Default
    private FolderVisibilityType visibility = FolderVisibilityType.PUBLIC;

    @Column(name = "share_token", length = 36, unique = true)
    private String shareToken;
}
