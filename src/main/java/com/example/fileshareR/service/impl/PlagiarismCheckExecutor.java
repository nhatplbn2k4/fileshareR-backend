package com.example.fileshareR.service.impl;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.DocumentSimilarity;
import com.example.fileshareR.enums.NotificationType;
import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.enums.PlagiarismTriggerType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.DocumentSimilarityRepository;
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.PlagiarismMatch;
import com.example.fileshareR.service.PlagiarismSourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tách riêng để @Transactional hoạt động khi gọi từ @Async method:
 * - PlagiarismServiceImpl.checkDocumentAsync (chạy ở thread riêng) → gọi sang bean này
 *   để qua Spring proxy → @Transactional mới được áp dụng.
 * Pattern này tránh self-invocation gotcha (Spring không apply advisor cho self-call).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlagiarismCheckExecutor {

    private final DocumentRepository documentRepository;
    private final DocumentSimilarityRepository similarityRepository;
    private final NotificationService notificationService;
    private final List<PlagiarismSourceProvider> providers;

    @Value("${plagiarism.similarity-threshold:0.7}")
    private double plagiarismThreshold;

    @Value("${plagiarism.max-matches:10}")
    private int maxPlagiarismMatches;

    /**
     * Ngưỡng thấp hơn để lưu CACHE độ tương đồng cho mục đích gợi ý tài liệu liên quan.
     * Mọi cặp (suspectedDoc, matched) có score ≥ recommendationThreshold đều được lưu:
     *   - score ≥ plagiarismThreshold → status = PENDING (workflow đạo văn — admin xem xét)
     *   - score <  plagiarismThreshold → status = null (chỉ cache cho recommendation, không alert admin)
     */
    @Value("${plagiarism.recommendation-threshold:0.1}")
    private double recommendationThreshold;

    @Value("${plagiarism.recommendation-max-matches:50}")
    private int maxRecommendationMatches;

    @Transactional
    public void checkDocument(Long documentId, PlagiarismTriggerType trigger, Long triggerContextId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.info("Plagiarism: doc {} not found, skip", documentId);
            return;
        }

        // Quét với ngưỡng THẤP để lưu toàn bộ pair vào document_similarities (cache cho recommendation).
        List<PlagiarismMatch> allMatches = new ArrayList<>();
        for (PlagiarismSourceProvider p : providers) {
            if (!p.isEnabled()) continue;
            try {
                List<PlagiarismMatch> m = p.findMatches(doc, recommendationThreshold, maxRecommendationMatches);
                log.info("Plagiarism: provider '{}' returned {} matches for doc {} (threshold={})",
                        p.getName(), m.size(), documentId, recommendationThreshold);
                allMatches.addAll(m);
            } catch (Exception e) {
                log.warn("Plagiarism: provider '{}' failed for doc {}: {}",
                        p.getName(), documentId, e.getMessage());
            }
        }

        if (allMatches.isEmpty()) {
            log.info("Plagiarism: doc {} clean (no matches above {})", documentId, recommendationThreshold);
            return;
        }

        List<PlagiarismMatch> topMatches = allMatches.stream()
                .sorted(Comparator.comparingDouble(PlagiarismMatch::similarityScore).reversed())
                .limit(maxRecommendationMatches)
                .toList();

        boolean isNewReport = similarityRepository.countByDocument1IdAndStatus(
                documentId, PlagiarismStatus.PENDING) == 0;

        double maxScore = 0;
        int plagiarismRowCount = 0;
        int cacheRowCount = 0;
        for (PlagiarismMatch m : topMatches) {
            // ── Internet match (externalUrl) ──────────────────────────────────────
            if (m.matchedDocumentId() == null && m.externalUrl() != null) {
                boolean isPlagiarismLevel = m.similarityScore() >= plagiarismThreshold;
                if (!isPlagiarismLevel) continue; // cache-only cho internet không lưu
                DocumentSimilarity existing = similarityRepository
                        .findByDocument1IdAndExternalUrl(documentId, m.externalUrl())
                        .orElse(null);
                if (existing == null) {
                    DocumentSimilarity row = DocumentSimilarity.builder()
                            .document1(doc)
                            .document2(null)
                            .externalUrl(m.externalUrl())
                            .similarityScore((float) m.similarityScore())
                            .triggerType(trigger)
                            .triggerContextId(triggerContextId)
                            .status(PlagiarismStatus.PENDING)
                            .build();
                    similarityRepository.save(row);
                    plagiarismRowCount++;
                } else if (existing.getSimilarityScore() == null
                        || existing.getSimilarityScore() < m.similarityScore()) {
                    existing.setSimilarityScore((float) m.similarityScore());
                    similarityRepository.save(existing);
                }
                maxScore = Math.max(maxScore, m.similarityScore());
                continue;
            }

            if (m.matchedDocumentId() == null) continue;
            boolean isPlagiarismLevel = m.similarityScore() >= plagiarismThreshold;
            DocumentSimilarity existing = similarityRepository
                    .findByDocument1IdAndDocument2Id(documentId, m.matchedDocumentId())
                    .orElse(null);
            if (existing == null) {
                Document matched = documentRepository.findById(m.matchedDocumentId()).orElse(null);
                if (matched == null) continue;
                DocumentSimilarity row = DocumentSimilarity.builder()
                        .document1(doc)
                        .document2(matched)
                        .similarityScore((float) m.similarityScore())
                        .triggerType(trigger)
                        .triggerContextId(triggerContextId)
                        .status(isPlagiarismLevel ? PlagiarismStatus.PENDING : null)
                        .build();
                similarityRepository.save(row);
                if (isPlagiarismLevel) plagiarismRowCount++; else cacheRowCount++;
            } else {
                PlagiarismStatus oldStatus = existing.getStatus();
                if (oldStatus == PlagiarismStatus.RESOLVED_KEPT
                        || oldStatus == PlagiarismStatus.IGNORED) {
                    // Admin đã đánh dấu cặp này không phải đạo văn — không reopen.
                    continue;
                }
                if (oldStatus == PlagiarismStatus.PENDING) {
                    if (existing.getSimilarityScore() == null
                            || existing.getSimilarityScore() < m.similarityScore()) {
                        existing.setSimilarityScore((float) m.similarityScore());
                        similarityRepository.save(existing);
                    }
                } else if (oldStatus == null) {
                    // Cache row trước đó (chỉ similarity, không phải plagiarism). Cập nhật score
                    // và nâng lên PENDING nếu score mới vượt ngưỡng plagiarism.
                    boolean changed = false;
                    if (existing.getSimilarityScore() == null
                            || existing.getSimilarityScore() < m.similarityScore()) {
                        existing.setSimilarityScore((float) m.similarityScore());
                        changed = true;
                    }
                    if (isPlagiarismLevel) {
                        existing.setStatus(PlagiarismStatus.PENDING);
                        existing.setTriggerType(trigger);
                        existing.setTriggerContextId(triggerContextId);
                        plagiarismRowCount++;
                        changed = true;
                    }
                    if (changed) {
                        similarityRepository.save(existing);
                    }
                } else {
                    // RESOLVED_PRIVATIZED (hoặc REMOVED leak): doc đã chuyển công khai trở lại
                    // → reopen về PENDING + xóa thông tin xử lý cũ + treat as new evidence.
                    existing.setStatus(PlagiarismStatus.PENDING);
                    existing.setSimilarityScore((float) m.similarityScore());
                    existing.setTriggerType(trigger);
                    existing.setTriggerContextId(triggerContextId);
                    existing.setResolvedBy(null);
                    existing.setResolvedAt(null);
                    existing.setResolutionNote(null);
                    similarityRepository.save(existing);
                    plagiarismRowCount++;
                }
            }
            maxScore = Math.max(maxScore, m.similarityScore());
        }

        log.info("Plagiarism: doc {} -> {} plagiarism rows + {} cache rows (isNewReport={}, maxScore={})",
                documentId, plagiarismRowCount, cacheRowCount, isNewReport, maxScore);

        if (isNewReport && plagiarismRowCount > 0) {
            String ownerEmail = doc.getUser() != null ? doc.getUser().getEmail() : "?";
            String triggerLabel = trigger == PlagiarismTriggerType.FOLDER_PUBLIC
                    ? "Folder public hóa"
                    : "Upload vào nhóm công khai";
            String message = String.format(
                    "Nghi đạo văn: \"%s\" của %s (max score %.2f, %d matches). Trigger: %s.",
                    doc.getTitle(), ownerEmail, maxScore, plagiarismRowCount, triggerLabel);
            try {
                notificationService.notifyAllAdmins(
                        NotificationType.PLAGIARISM_REPORT,
                        "Cảnh báo đạo văn",
                        message,
                        documentId,
                        "/admin/plagiarism/" + documentId);
            } catch (Exception e) {
                log.warn("Plagiarism: failed to notify admins (best-effort): {}", e.getMessage());
            }
        }
    }
}
