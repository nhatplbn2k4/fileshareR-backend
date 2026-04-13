package com.example.fileshareR.entity;

import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.VisibilityType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private FileType fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VisibilityType visibility = VisibilityType.PRIVATE;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // Lưu keywords dưới dạng JSON string (e.g., ["keyword1", "keyword2"])
    @Column(columnDefinition = "TEXT")
    private String keywords;

    // Lưu TF-IDF vector dưới dạng JSON string
    @Column(name = "tfidf_vector", columnDefinition = "TEXT")
    private String tfidfVector;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    // Liên kết với nhóm (null = tài liệu cá nhân)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_folder_id")
    private GroupFolder groupFolder;
}
