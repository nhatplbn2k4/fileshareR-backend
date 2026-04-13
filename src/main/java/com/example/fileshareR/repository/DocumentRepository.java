package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.enums.VisibilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserId(Long userId);
    List<Document> findByFolderId(Long folderId);
    List<Document> findByVisibility(VisibilityType visibility);

    @Query("SELECT d FROM Document d WHERE d.visibility = 'PUBLIC' OR d.user.id = :userId")
    List<Document> findPublicAndUserDocuments(@Param("userId") Long userId);

    /**
     * Tìm kiếm tài liệu (legacy — chỉ LIKE trên title):
     */
    @Query("SELECT d FROM Document d WHERE " +
           "(d.visibility = 'PUBLIC' OR d.user.id = :userId " +
           " OR (d.folder IS NOT NULL AND d.folder.visibility = 'PUBLIC')) AND " +
           "LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) AND " +
           "(d.folder IS NULL OR d.folder.visibility = 'PUBLIC' OR d.user.id = :userId)")
    List<Document> searchByKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * Tìm kiếm tài liệu với xếp hạng mức độ liên quan:
     *   - Tên tài liệu chứa keyword → +100
     *   - Keywords chứa keyword     → +10
     *   - Nội dung (full-text GIN)   → +1
     * Sắp xếp: relevance DESC, lượt tải DESC
     * Quyền truy cập: PUBLIC hoặc của chính user; folder NULL/PUBLIC/owner
     */
    @Query(value =
        "SELECT d.* FROM documents d " +
        "LEFT JOIN folders f ON d.folder_id = f.id " +
        "WHERE (d.visibility = 'PUBLIC' OR d.user_id = :userId " +
        "       OR (d.folder_id IS NOT NULL AND f.visibility = 'PUBLIC')) " +
        "  AND (d.folder_id IS NULL OR f.visibility = 'PUBLIC' OR d.user_id = :userId) " +
        "  AND ( " +
        "    LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
        "    OR LOWER(d.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
        "    OR to_tsvector('english', COALESCE(d.extracted_text, '')) " +
        "       @@ plainto_tsquery('english', :keyword) " +
        "  ) " +
        "ORDER BY " +
        "  ( CASE WHEN LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) THEN 100 ELSE 0 END " +
        "  + CASE WHEN LOWER(d.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')) THEN 10 ELSE 0 END " +
        "  + CASE WHEN to_tsvector('english', COALESCE(d.extracted_text, '')) " +
        "         @@ plainto_tsquery('english', :keyword) THEN 1 ELSE 0 END " +
        "  ) DESC, " +
        "  d.download_count DESC NULLS LAST " +
        "LIMIT 50",
        nativeQuery = true)
    List<Document> searchWithRelevance(@Param("userId") Long userId, @Param("keyword") String keyword);

    long countByUserIdAndVisibility(Long userId, VisibilityType visibility);
    List<Document> findByUserIdAndVisibility(Long userId, VisibilityType visibility);

    // ── Group document queries ────────────────────────────────────────────────

    /** Toàn bộ tài liệu thuộc nhóm */
    List<Document> findByGroupId(Long groupId);

    /** Tài liệu trong một folder nhóm cụ thể */
    List<Document> findByGroupIdAndGroupFolderId(Long groupId, Long groupFolderId);

    /** Tài liệu nhóm không thuộc folder nào */
    @Query("SELECT d FROM Document d WHERE d.group.id = :groupId AND d.groupFolder IS NULL")
    List<Document> findByGroupIdAndGroupFolderIsNull(@Param("groupId") Long groupId);
}
