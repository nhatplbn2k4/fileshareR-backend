package com.example.fileshareR.repository;

import com.example.fileshareR.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId);
    
    @Query("SELECT sh FROM SearchHistory sh WHERE sh.user.id = :userId " +
           "ORDER BY sh.searchedAt DESC LIMIT 10")
    List<SearchHistory> findRecentSearches(@Param("userId") Long userId);
}
