package com.example.fileshareR.repository;

import com.example.fileshareR.entity.DocumentSimilarity;
import com.example.fileshareR.enums.PlagiarismStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentSimilarityRepository extends JpaRepository<DocumentSimilarity, Long> {

    /**
     * Legacy: find similar docs cho UI "Find similar".
     */
    @Query("SELECT ds FROM DocumentSimilarity ds WHERE " +
           "ds.document1.id = :documentId OR ds.document2.id = :documentId " +
           "ORDER BY ds.similarityScore DESC")
    List<DocumentSimilarity> findSimilarDocuments(@Param("documentId") Long documentId);

    /**
     * Upsert helper: kiểm tra đã có cặp (doc1, doc2) chưa.
     */
    Optional<DocumentSimilarity> findByDocument1IdAndDocument2Id(Long document1Id, Long document2Id);

    /**
     * Tất cả rows evidence của 1 report (= 1 suspected doc).
     */
    List<DocumentSimilarity> findByDocument1IdOrderBySimilarityScoreDesc(Long document1Id);

    /**
     * Đếm số row với status nhất định cho 1 suspected doc — dedup check.
     */
    long countByDocument1IdAndStatus(Long document1Id, PlagiarismStatus status);

    /**
     * Group view cho admin list: 1 dòng / suspected doc, kèm max score + match count.
     */
    @Query("""
        SELECT ds.document1.id AS suspectedDocumentId,
               MAX(ds.similarityScore) AS maxScore,
               COUNT(ds) AS matchCount,
               MIN(ds.calculatedAt) AS firstDetectedAt
        FROM DocumentSimilarity ds
        WHERE ds.status = :status
        GROUP BY ds.document1.id
        ORDER BY MAX(ds.similarityScore) DESC
        """)
    Page<PlagiarismReportProjection> findReportSummaries(@Param("status") PlagiarismStatus status, Pageable pageable);

    /**
     * Count distinct suspected docs cho badge "PENDING".
     */
    @Query("SELECT COUNT(DISTINCT ds.document1.id) FROM DocumentSimilarity ds WHERE ds.status = :status")
    long countDistinctReportsByStatus(@Param("status") PlagiarismStatus status);

    /**
     * Cleanup rows trỏ tới doc bị xóa (gọi trước khi delete doc để tránh FK violation).
     */
    @Modifying
    @Query("DELETE FROM DocumentSimilarity ds WHERE ds.document1.id = :docId OR ds.document2.id = :docId")
    void deleteByDocument1IdOrDocument2Id(@Param("docId") Long docId);

    interface PlagiarismReportProjection {
        Long getSuspectedDocumentId();
        Float getMaxScore();
        Long getMatchCount();
        java.time.LocalDateTime getFirstDetectedAt();
    }
}
