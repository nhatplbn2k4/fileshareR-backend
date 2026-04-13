package com.example.fileshareR.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_similarities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"document_id_1", "document_id_2"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id_1", nullable = false)
    private Document document1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id_2", nullable = false)
    private Document document2;

    @Column(name = "similarity_score", nullable = false)
    private Float similarityScore;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        calculatedAt = LocalDateTime.now();
    }
}
