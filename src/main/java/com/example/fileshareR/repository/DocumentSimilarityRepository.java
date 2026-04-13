package com.example.fileshareR.repository;

import com.example.fileshareR.entity.DocumentSimilarity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentSimilarityRepository extends JpaRepository<DocumentSimilarity, Long> {
    @Query("SELECT ds FROM DocumentSimilarity ds WHERE " +
           "ds.document1.id = :documentId OR ds.document2.id = :documentId " +
           "ORDER BY ds.similarityScore DESC")
    List<DocumentSimilarity> findSimilarDocuments(@Param("documentId") Long documentId);
}
