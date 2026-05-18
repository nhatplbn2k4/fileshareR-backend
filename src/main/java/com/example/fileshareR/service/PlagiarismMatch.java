package com.example.fileshareR.service;

/**
 * Một match tiềm năng từ provider — internal hoặc external nguồn.
 */
public record PlagiarismMatch(
        Long matchedDocumentId,
        String externalUrl,
        String matchedTitle,
        String matchedOwnerEmail,
        double similarityScore,
        String evidenceSnippet) {
}
