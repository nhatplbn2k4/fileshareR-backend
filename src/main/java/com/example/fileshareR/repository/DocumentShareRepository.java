package com.example.fileshareR.repository;

import com.example.fileshareR.entity.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentShareRepository extends JpaRepository<DocumentShare, Long> {
    List<DocumentShare> findByDocumentId(Long documentId);
    List<DocumentShare> findBySharedToId(Long userId);
    List<DocumentShare> findBySharedById(Long userId);
    Optional<DocumentShare> findByDocumentIdAndSharedToId(Long documentId, Long userId);
    boolean existsByDocumentIdAndSharedToId(Long documentId, Long userId);
}
