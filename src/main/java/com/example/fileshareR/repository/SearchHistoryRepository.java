package com.example.fileshareR.repository;

import com.example.fileshareR.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId);

    @Query("SELECT sh FROM SearchHistory sh WHERE sh.user.id = :userId " +
           "ORDER BY sh.searchedAt DESC LIMIT 10")
    List<SearchHistory> findRecentSearches(@Param("userId") Long userId);

    /** Tìm bản ghi cùng từ khóa (không phân biệt hoa thường) để upsert. */
    Optional<SearchHistory> findFirstByUserIdAndKeywordIgnoreCase(Long userId, String keyword);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.user.id = :userId AND sh.id = :id")
    void deleteByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);
}
