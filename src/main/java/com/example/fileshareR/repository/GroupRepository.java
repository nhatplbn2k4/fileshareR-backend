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
