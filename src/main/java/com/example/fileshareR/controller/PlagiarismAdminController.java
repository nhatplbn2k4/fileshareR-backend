package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ResolvePlagiarismRequest;
import com.example.fileshareR.dto.response.PlagiarismMatchResponse;
import com.example.fileshareR.dto.response.PlagiarismReportDetailResponse;
import com.example.fileshareR.dto.response.PlagiarismReportSummaryResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.DocumentSimilarity;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.DocumentSimilarityRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.PlagiarismService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints để xem + xử lý các báo cáo nghi đạo văn.
 * Security: mounted dưới /api/admin/** đã ADMIN-only ở SecurityConfiguration.
 */
@RestController
@RequestMapping("/api/admin/plagiarism")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class PlagiarismAdminController {

    private final PlagiarismService plagiarismService;
    private final DocumentSimilarityRepository similarityRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * Danh sách báo cáo (group theo suspected doc), filter theo status.
     */
    @GetMapping
    public Page<PlagiarismReportSummaryResponse> listReports(
            @RequestParam(defaultValue = "PENDING") PlagiarismStatus status,
            Pageable pageable) {

        Page<DocumentSimilarityRepository.PlagiarismReportProjection> page =
                similarityRepository.findReportSummaries(status, pageable);

        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Bulk-fetch suspected docs + first row (cho trigger info) để map response
        List<Long> docIds = page.getContent().stream()
                .map(DocumentSimilarityRepository.PlagiarismReportProjection::getSuspectedDocumentId)
                .toList();
        Map<Long, Document> docMap = documentRepository.findAllById(docIds).stream()
                .collect(java.util.stream.Collectors.toMap(Document::getId, d -> d));

        List<PlagiarismReportSummaryResponse> rows = page.getContent().stream()
                .map(p -> {
                    Document d = docMap.get(p.getSuspectedDocumentId());
                    List<DocumentSimilarity> sims = similarityRepository
                            .findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(p.getSuspectedDocumentId());
                    DocumentSimilarity first = sims.isEmpty() ? null : sims.get(0);
                    return PlagiarismReportSummaryResponse.builder()
                            .suspectedDocumentId(p.getSuspectedDocumentId())
                            .suspectedTitle(d != null ? d.getTitle() : "(đã xóa)")
                            .suspectedOwnerEmail(d != null && d.getUser() != null
                                    ? d.getUser().getEmail() : null)
                            .status(status)
                            .triggerType(first != null ? first.getTriggerType() : null)
                            .triggerContextId(first != null ? first.getTriggerContextId() : null)
                            .maxScore(p.getMaxScore())
                            .matchCount(p.getMatchCount())
                            .firstDetectedAt(p.getFirstDetectedAt())
                            .build();
                })
                .toList();

        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /**
     * Chi tiết 1 report + danh sách matches.
     */
    @GetMapping("/{docId}")
    public PlagiarismReportDetailResponse getReportDetail(@PathVariable Long docId) {
        List<DocumentSimilarity> rows = similarityRepository
                .findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(docId);
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Không có báo cáo đạo văn cho tài liệu này.");
        }
        Document suspected = documentRepository.findById(docId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        DocumentSimilarity first = rows.get(0);
        return PlagiarismReportDetailResponse.builder()
                .suspectedDocumentId(suspected.getId())
                .suspectedTitle(suspected.getTitle())
                .suspectedOwnerEmail(suspected.getUser() != null ? suspected.getUser().getEmail() : null)
                .suspectedOwnerWarningCount(suspected.getUser() != null
                        ? suspected.getUser().getPlagiarismWarningCount() : null)
                .suspectedSnippet(snippet(suspected.getExtractedText()))
                .status(first.getStatus())
                .triggerType(first.getTriggerType())
                .triggerContextId(first.getTriggerContextId())
                .maxScore(rows.stream()
                        .map(DocumentSimilarity::getSimilarityScore)
                        .filter(s -> s != null)
                        .max(Float::compareTo)
                        .orElse(null))
                .firstDetectedAt(rows.stream()
                        .map(DocumentSimilarity::getCalculatedAt)
                        .filter(c -> c != null)
                        .min(LocalDateTime::compareTo)
                        .orElse(null))
                .resolvedAt(first.getResolvedAt())
                .resolverEmail(first.getResolvedBy() != null ? first.getResolvedBy().getEmail() : null)
                .resolutionNote(first.getResolutionNote())
                .matches(rows.stream().map(r -> {
                    Document m = r.getDocument2();
                    // Internet plagiarism match: document2 = null nhưng có externalUrl
                    if (m == null && r.getExternalUrl() != null) {
                        String url = r.getExternalUrl();
                        String host = url;
                        try {
                            String h = java.net.URI.create(url).getHost();
                            if (h != null) host = h;
                        } catch (Exception ignored) {}
                        return PlagiarismMatchResponse.builder()
                                .matchedDocumentId(null)
                                .matchedTitle(host)
                                .matchedOwnerEmail(null)
                                .similarityScore(r.getSimilarityScore())
                                .snippet(url)
                                .externalUrl(url)
                                .build();
                    }
                    return PlagiarismMatchResponse.builder()
                            .matchedDocumentId(m != null ? m.getId() : null)
                            .matchedTitle(m != null ? m.getTitle() : "(đã xóa)")
                            .matchedOwnerEmail(m != null && m.getUser() != null ? m.getUser().getEmail() : null)
                            .similarityScore(r.getSimilarityScore())
                            .snippet(m != null ? snippet(m.getExtractedText()) : null)
                            .externalUrl(null)
                            .build();
                }).toList())
                .build();
    }

    /**
     * Admin xử lý report.
     */
    @PatchMapping("/{docId}/resolve")
    public PlagiarismReportDetailResponse resolve(@PathVariable Long docId,
                                                 @Valid @RequestBody ResolvePlagiarismRequest req,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Long adminId = resolveAdminId(jwt);
        return plagiarismService.resolveReport(docId, req, adminId);
    }

    /**
     * Số báo cáo PENDING — cho badge ở sidebar admin.
     */
    @GetMapping("/pending-count")
    public Map<String, Long> pendingCount() {
        long count = similarityRepository.countDistinctReportsByStatus(PlagiarismStatus.PENDING);
        return Map.of("count", count);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Long resolveAdminId(Jwt jwt) {
        if (jwt == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        String email = jwt.getSubject();
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return u.getId();
    }

    private String snippet(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 240) + "...";
    }
}
