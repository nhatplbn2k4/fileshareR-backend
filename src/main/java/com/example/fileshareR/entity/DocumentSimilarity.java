package com.example.fileshareR.entity;

import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.enums.PlagiarismTriggerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_similarities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tài liệu nghi đạo văn (suspected) — trigger scan. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id_1", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Document document1;

    /** Tài liệu gốc bị nghi đã copy (matched). Null khi match từ internet (xem externalUrl). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id_2", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Document document2;

    /** URL trang web bên ngoài — chỉ có giá trị khi document2 = null (internet plagiarism). */
    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "similarity_score", nullable = false)
    private Float similarityScore;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    // ── Plagiarism workflow fields ─────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 32)
    private PlagiarismTriggerType triggerType;

    /** folder.id hoặc group.id — context vì sao scan. */
    @Column(name = "trigger_context_id")
    private Long triggerContextId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private PlagiarismStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @PrePersist
    protected void onCreate() {
        calculatedAt = LocalDateTime.now();
    }
}
