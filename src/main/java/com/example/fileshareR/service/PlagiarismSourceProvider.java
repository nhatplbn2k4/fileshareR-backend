package com.example.fileshareR.service;

import com.example.fileshareR.entity.Document;

import java.util.List;

/**
 * Nguồn cung cấp tài liệu để so sánh đạo văn.
 * MVP có 1 implementation: InternalDocumentProvider (so với DB nội bộ).
 * Tương lai: GoogleSearchProvider, CopyleaksProvider — plug-in qua interface này.
 */
public interface PlagiarismSourceProvider {

    /**
     * Trả về các match có score >= threshold, đã sort desc, tối đa maxResults.
     */
    List<PlagiarismMatch> findMatches(Document suspectedDoc, double threshold, int maxResults);

    /** Provider có active không (qua config). */
    boolean isEnabled();

    /** Tên provider để log + identify. */
    String getName();
}
