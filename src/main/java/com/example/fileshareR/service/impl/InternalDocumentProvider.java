package com.example.fileshareR.service.impl;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.service.NlpService;
import com.example.fileshareR.service.PlagiarismMatch;
import com.example.fileshareR.service.PlagiarismSourceProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * So sánh suspected doc với toàn bộ tài liệu nội bộ (trừ doc của chính user)
 * dùng cosine similarity trên TF-IDF vector đã được lưu sẵn ở mỗi Document.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalDocumentProvider implements PlagiarismSourceProvider {

    private final DocumentRepository documentRepository;
    private final NlpService nlpService;
    private final ObjectMapper objectMapper;

    @Value("${plagiarism.providers.internal.enabled:true}")
    private boolean enabled;

    @Override
    public String getName() {
        return "internal";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<PlagiarismMatch> findMatches(Document suspectedDoc, double threshold, int maxResults) {
        if (suspectedDoc.getTfidfVector() == null || suspectedDoc.getTfidfVector().isBlank()) {
            log.info("Plagiarism[internal]: doc {} has no tfidfVector → skip", suspectedDoc.getId());
            return List.of();
        }

        Map<String, Double> sourceVec = parseVector(suspectedDoc.getTfidfVector());
        if (sourceVec.isEmpty()) {
            return List.of();
        }

        Long suspectUserId = suspectedDoc.getUser() != null ? suspectedDoc.getUser().getId() : null;

        // Quan hệ "lưu về": nếu suspectedDoc là bản sao thì biết tài liệu gốc + tác giả gốc.
        Long suspectedSourceId = suspectedDoc.getSourceDocument() != null
                ? suspectedDoc.getSourceDocument().getId() : null;
        Long originalAuthorId = (suspectedDoc.getSourceDocument() != null
                && suspectedDoc.getSourceDocument().getUser() != null)
                ? suspectedDoc.getSourceDocument().getUser().getId() : null;

        // Quét toàn bộ docs trong hệ thống, lọc trong memory.
        // Với N nhỏ (<1k) đây là chấp nhận. Optimize sau bằng keyword pre-filter nếu cần.
        List<Document> candidates = documentRepository.findAll().stream()
                .filter(d -> !d.getId().equals(suspectedDoc.getId()))
                .filter(d -> d.getTfidfVector() != null && !d.getTfidfVector().isBlank())
                .filter(d -> d.getModerationStatus() == ModerationStatus.APPROVED)
                .filter(d -> suspectUserId == null
                        || d.getUser() == null
                        || !suspectUserId.equals(d.getUser().getId()))
                // Bỏ qua tài liệu liên quan quan hệ "lưu về" — bản sao hợp lệ KHÔNG tính đạo văn:
                .filter(d -> !isSaveLinked(d, suspectedDoc, suspectedSourceId, originalAuthorId))
                .toList();

        log.info("Plagiarism[internal]: scanning doc {} against {} candidates, threshold {}",
                suspectedDoc.getId(), candidates.size(), threshold);

        return candidates.stream()
                .map(c -> {
                    Map<String, Double> targetVec = parseVector(c.getTfidfVector());
                    double score = nlpService.cosineSimilarity(sourceVec, targetVec);
                    return new PlagiarismMatch(
                            c.getId(),
                            null,
                            c.getTitle(),
                            c.getUser() != null ? c.getUser().getEmail() : null,
                            score,
                            buildSnippet(c.getExtractedText()));
                })
                .filter(m -> m.similarityScore() >= threshold)
                .sorted(Comparator.comparingDouble(PlagiarismMatch::similarityScore).reversed())
                .limit(maxResults)
                .toList();
    }

    /**
     * Cặp (candidate, suspected) có liên kết "lưu về" → bỏ qua khi check đạo văn:
     *   (a) candidate chính là tài liệu gốc của suspected;
     *   (b) candidate thuộc tác giả gốc (suspected là bản sao tài liệu của họ);
     *   (c) candidate là bản sao của suspected (chiều ngược lại);
     *   (d) candidate và suspected là hai bản sao của cùng một gốc.
     */
    private boolean isSaveLinked(Document candidate, Document suspected,
                                 Long suspectedSourceId, Long originalAuthorId) {
        Long candSourceId = candidate.getSourceDocument() != null
                ? candidate.getSourceDocument().getId() : null;
        if (suspectedSourceId != null && suspectedSourceId.equals(candidate.getId())) return true;   // (a)
        if (originalAuthorId != null && candidate.getUser() != null
                && originalAuthorId.equals(candidate.getUser().getId())) return true;                 // (b)
        if (candSourceId != null && candSourceId.equals(suspected.getId())) return true;              // (c)
        if (candSourceId != null && suspectedSourceId != null
                && candSourceId.equals(suspectedSourceId)) return true;                               // (d)
        return false;
    }

    private Map<String, Double> parseVector(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("Plagiarism[internal]: cannot parse tfidfVector json: {}", e.getMessage());
            return Map.of();
        }
    }

    private String buildSnippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 240) + "...";
    }
}
