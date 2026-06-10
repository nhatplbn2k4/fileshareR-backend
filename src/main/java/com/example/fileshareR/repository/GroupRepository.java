package com.example.fileshareR.repository;

import com.example.fileshareR.entity.Group;
import com.example.fileshareR.enums.GroupVisibilityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByVisibility(GroupVisibilityType visibility);

    List<Group> findByNameContainingIgnoreCaseAndVisibility(String keyword, GroupVisibilityType visibility);

    List<Group> findByNameContainingIgnoreCase(String keyword);

    List<Group> findByOwnerId(Long ownerId);

    Optional<Group> findByShareToken(String shareToken);

    /** Tổng dung lượng owner đã cấp phát ("giữ chỗ") cho tất cả nhóm mình sở hữu. */
    @Query("SELECT COALESCE(SUM(g.allocatedQuotaBytes), 0) FROM Group g WHERE g.owner.id = :ownerId")
    long sumAllocatedQuotaByOwnerId(@Param("ownerId") Long ownerId);

    /** Gợi ý autocomplete: tên nhóm PUBLIC hoặc nhóm user làm chủ khớp prefix/substring. */
    @Query("SELECT DISTINCT g.name FROM Group g WHERE " +
           "(g.visibility = com.example.fileshareR.enums.GroupVisibilityType.PUBLIC OR g.owner.id = :userId) AND " +
           "LOWER(g.name) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY g.name ASC")
    List<String> suggestNames(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    /**
     * Admin paged + filtered list. Search matches name + owner email.
     */
    @Query("""
        SELECT g FROM Group g
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(g.owner.email) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:visibility IS NULL OR g.visibility = :visibility)
        """)
    Page<Group> findAllForAdmin(
            @Param("search") String search,
            @Param("visibility") GroupVisibilityType visibility,
            Pageable pageable);
}
