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
    private double threshold;

    @Value("${plagiarism.max-matches:10}")
    private int maxMatches;

    @Transactional
    public void checkDocument(Long documentId, PlagiarismTriggerType trigger, Long triggerContextId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.info("Plagiarism: doc {} not found, skip", documentId);
            return;
        }

        List<PlagiarismMatch> allMatches = new ArrayList<>();
        for (PlagiarismSourceProvider p : providers) {
            if (!p.isEnabled()) continue;
            try {
                List<PlagiarismMatch> m = p.findMatches(doc, threshold, maxMatches);
                log.info("Plagiarism: provider '{}' returned {} matches for doc {}",
                        p.getName(), m.size(), documentId);
                allMatches.addAll(m);
            } catch (Exception e) {
                log.warn("Plagiarism: provider '{}' failed for doc {}: {}",
                        p.getName(), documentId, e.getMessage());
            }
        }

        if (allMatches.isEmpty()) {
            log.info("Plagiarism: doc {} clean (no matches above {})", documentId, threshold);
            return;
        }

        List<PlagiarismMatch> topMatches = allMatches.stream()
                .sorted(Comparator.comparingDouble(PlagiarismMatch::similarityScore).reversed())
                .limit(maxMatches)
                .toList();

        boolean isNewReport = similarityRepository.countByDocument1IdAndStatus(
                documentId, PlagiarismStatus.PENDING) == 0;

        double maxScore = 0;
        int newRowCount = 0;
        for (PlagiarismMatch m : topMatches) {
            if (m.matchedDocumentId() == null) continue;
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
                        .status(PlagiarismStatus.PENDING)
                        .build();
                similarityRepository.save(row);
                newRowCount++;
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
                    newRowCount++;
                }
            }
            maxScore = Math.max(maxScore, m.similarityScore());
        }

        log.info("Plagiarism: doc {} -> {} new rows (isNewReport={}, maxScore={})",
                documentId, newRowCount, isNewReport, maxScore);

        if (isNewReport && newRowCount > 0) {
            String ownerEmail = doc.getUser() != null ? doc.getUser().getEmail() : "?";
            String triggerLabel = trigger == PlagiarismTriggerType.FOLDER_PUBLIC
                    ? "Folder public hóa"
                    : "Upload vào nhóm công khai";
            String message = String.format(
                    "Nghi đạo văn: \"%s\" của %s (max score %.2f, %d matches). Trigger: %s.",
                    doc.getTitle(), ownerEmail, maxScore, newRowCount, triggerLabel);
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
