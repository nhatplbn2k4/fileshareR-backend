package com.example.fileshareR.repository;

import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Used to broadcast platform-admin notifications. */
    List<User> findAllByRole(UserRole role);

    /**
     * Paginated + filtered list for admin user management.
     * Null parameters are treated as "no filter".
     */
    @Query("""
        SELECT u FROM User u
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:planCode IS NULL OR :planCode = ''
               OR (u.plan IS NOT NULL AND u.plan.code = :planCode))
          AND (:isActive IS NULL OR u.isActive = :isActive)
        """)
    Page<User> findAllForAdmin(
            @Param("search") String search,
            @Param("planCode") String planCode,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
