package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ResolvePlagiarismRequest;
import com.example.fileshareR.dto.response.PlagiarismMatchResponse;
import com.example.fileshareR.dto.response.PlagiarismReportDetailResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.DocumentSimilarity;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.enums.NotificationType;
import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.enums.PlagiarismTriggerType;
import com.example.fileshareR.enums.VisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.DocumentSimilarityRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.PlagiarismMatch;
import com.example.fileshareR.service.PlagiarismService;
import com.example.fileshareR.service.PlagiarismSourceProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlagiarismServiceImpl implements PlagiarismService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final DocumentSimilarityRepository similarityRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DocumentService documentService;
    private final List<PlagiarismSourceProvider> providers;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${plagiarism.enabled:true}")
    private boolean enabled;

    @Value("${plagiarism.similarity-threshold:0.7}")
    private double threshold;

    @Value("${plagiarism.max-matches:10}")
    private int maxMatches;

    @Value("${plagiarism.auto-ban-threshold:3}")
    private int autoBanThreshold;

    @Override
    @Async("plagiarismExecutor")
    @Transactional
    public void checkDocumentAsync(Long documentId, PlagiarismTriggerType trigger, Long triggerContextId) {
        if (!enabled) {
            log.info("Plagiarism check disabled, skip doc {}", documentId);
            return;
        }
        try {
            checkDocumentInternal(documentId, trigger, triggerContextId);
        } catch (Exception e) {
            log.error("Plagiarism check failed for doc {}: {}", documentId, e.getMessage(), e);
        }
    }

    @Override
    @Async("plagiarismExecutor")
    @Transactional
    public void checkFolderTreeAsync(Long folderId) {
        if (!enabled) {
            log.info("Plagiarism check disabled, skip folder {}", folderId);
            return;
        }
        try {
            // BFS folder tree, collect all doc IDs
            List<Long> docIds = collectDocsInFolderTree(folderId);
            log.info("Plagiarism: folder tree {} → {} docs to scan", folderId, docIds.size());
            for (Long docId : docIds) {
                try {
                    checkDocumentInternal(docId, PlagiarismTriggerType.FOLDER_PUBLIC, folderId);
                } catch (Exception e) {
                    log.warn("Plagiarism check skipped doc {} in folder tree: {}", docId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Plagiarism folder tree scan failed for folder {}: {}", folderId, e.getMessage(), e);
        }
    }

    /**
     * Sync internal — gọi từ async wrapper hoặc loop folder tree.
     */
    private void checkDocumentInternal(Long documentId, PlagiarismTriggerType trigger, Long triggerContextId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.info("Plagiarism: doc {} not found, skip", documentId);
            return;
        }

        // Aggregate matches từ tất cả providers active
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

        // Sort + limit top N
        List<PlagiarismMatch> topMatches = allMatches.stream()
                .sorted(Comparator.comparingDouble(PlagiarismMatch::similarityScore).reversed())
                .limit(maxMatches)
                .toList();

        // Dedup: nếu đã có row PENDING cho doc này → chỉ upsert thêm, không gửi notification mới.
        boolean isNewReport = similarityRepository.countByDocument1IdAndStatus(
                documentId, PlagiarismStatus.PENDING) == 0;

        double maxScore = 0;
        int newRowCount = 0;
        for (PlagiarismMatch m : topMatches) {
            if (m.matchedDocumentId() == null) continue; // external — chưa hỗ trợ persist
            DocumentSimilarity existing = similarityRepository
                    .findByDocument1IdAndDocument2Id(documentId, m.matchedDocumentId())
                    .orElse(null);
            if (existing == null) {
                Document matched = documentRepository.getReferenceById(m.matchedDocumentId());
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
            } else if (existing.getStatus() == PlagiarismStatus.PENDING) {
                // refresh score nếu cao hơn
                if (existing.getSimilarityScore() == null
                        || existing.getSimilarityScore() < m.similarityScore()) {
                    existing.setSimilarityScore((float) m.similarityScore());
                    similarityRepository.save(existing);
                }
            }
            maxScore = Math.max(maxScore, m.similarityScore());
        }

        log.info("Plagiarism: doc {} -> {} new rows (isNewReport={}, maxScore={})",
                documentId, newRowCount, isNewReport, maxScore);

        // Chỉ gửi notification 1 lần / report mới
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

    /**
     * Đệ quy gom tất cả document trong folder + sub-folders.
     */
    private List<Long> collectDocsInFolderTree(Long rootFolderId) {
        List<Long> docIds = new ArrayList<>();
        Queue<Long> q = new LinkedList<>();
        q.add(rootFolderId);
        while (!q.isEmpty()) {
            Long fid = q.poll();
            documentRepository.findByFolderId(fid).forEach(d -> docIds.add(d.getId()));
            folderRepository.findByParentId(fid).forEach(child -> q.add(child.getId()));
        }
        return docIds;
    }

    // ── Admin resolve ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlagiarismReportDetailResponse resolveReport(Long suspectedDocId,
                                                       ResolvePlagiarismRequest req,
                                                       Long adminId) {
        Document doc = documentRepository.findById(suspectedDocId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<DocumentSimilarity> rows = similarityRepository
                .findByDocument1IdOrderBySimilarityScoreDesc(suspectedDocId);
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Không tìm thấy báo cáo đạo văn cho tài liệu này.");
        }

        ResolvePlagiarismRequest.Action action = req.getAction();
        PlagiarismStatus newStatus = switch (action) {
            case KEEP -> PlagiarismStatus.RESOLVED_KEPT;
            case REMOVE -> PlagiarismStatus.RESOLVED_REMOVED;
            case PRIVATIZE -> PlagiarismStatus.RESOLVED_PRIVATIZED;
            case IGNORE -> PlagiarismStatus.IGNORED;
        };

        User docOwner = doc.getUser();
        String docTitle = doc.getTitle();

        // Cập nhật status các evidence rows (cùng suspected doc) — làm trước action chính
        // để dữ liệu nhất quán kể cả khi action sau lỗi.
        LocalDateTime now = LocalDateTime.now();
        for (DocumentSimilarity row : rows) {
            row.setStatus(newStatus);
            row.setResolvedBy(admin);
            row.setResolvedAt(now);
            row.setResolutionNote(req.getNote());
        }
        similarityRepository.saveAll(rows);

        switch (action) {
            case REMOVE -> handleRemove(doc, docOwner, docTitle);
            case PRIVATIZE -> handlePrivatize(doc, docOwner, docTitle);
            case KEEP -> log.info("Plagiarism report kept (false positive) for doc {}", suspectedDocId);
            case IGNORE -> log.info("Plagiarism report ignored for doc {}", suspectedDocId);
        }

        // Reload rows để build response (đặc biệt khi REMOVE doc → rows đã bị xóa cascade)
        if (action == ResolvePlagiarismRequest.Action.REMOVE) {
            return buildDetailAfterRemoval(suspectedDocId, docTitle, docOwner, newStatus,
                    rows, admin, req.getNote(), now);
        }
        return buildDetail(doc, rows);
    }

    private void handleRemove(Document doc, User owner, String docTitle) {
        if (owner == null) {
            documentService.adminDeleteDocument(doc.getId());
            return;
        }
        // Tăng warning counter
        int warningCount = owner.getPlagiarismWarningCount() == null
                ? 0 : owner.getPlagiarismWarningCount();
        warningCount += 1;
        owner.setPlagiarismWarningCount(warningCount);

        boolean nowBanned = warningCount >= autoBanThreshold && Boolean.TRUE.equals(owner.getIsActive());
        if (nowBanned) {
            owner.setIsActive(false);
        }
        userRepository.save(owner);

        // Xóa evidence rows trỏ tới doc TRƯỚC khi xóa doc để tránh FK violation
        similarityRepository.deleteByDocument1IdOrDocument2Id(doc.getId());
        entityManager.flush();

        // Delete doc (file + DB) — adminDeleteDocument đã có
        documentService.adminDeleteDocument(doc.getId());

        // Notify owner
        String warnMsg = String.format(
                "Tài liệu \"%s\" đã bị quản trị viên xóa do nghi đạo văn. Cảnh báo %d/%d.",
                docTitle, warningCount, autoBanThreshold);
        if (nowBanned) {
            warnMsg += " Tài khoản đã bị khóa do vượt ngưỡng cảnh báo.";
        }
        try {
            notificationService.notifyUser(owner, NotificationType.SYSTEM,
                    "Cảnh báo đạo văn", warnMsg, doc.getId(), null);
            if (nowBanned) {
                notificationService.notifyUser(owner, NotificationType.USER_BANNED_BY_PLATFORM,
                        "Tài khoản bị khóa",
                        "Tài khoản đã bị khóa do vượt " + autoBanThreshold + " lần vi phạm đạo văn.",
                        owner.getId(), null);
            }
        } catch (Exception e) {
            log.warn("Failed to notify owner after REMOVE: {}", e.getMessage());
        }
    }

    private void handlePrivatize(Document doc, User owner, String docTitle) {
        // Document level → PRIVATE
        doc.setVisibility(VisibilityType.PRIVATE);
        // Nếu doc thuộc folder PUBLIC → cũng đặt folder PRIVATE (cascade ngược lại nhỏ thôi, chỉ folder hiện tại)
        if (doc.getFolder() != null && doc.getFolder().getVisibility() != FolderVisibilityType.PRIVATE) {
            Folder f = doc.getFolder();
            f.setVisibility(FolderVisibilityType.PRIVATE);
            f.setShareToken(null);
            folderRepository.save(f);
        }
        // Nếu doc thuộc group → gỡ khỏi group (vì group public là trigger)
        if (doc.getGroup() != null) {
            doc.setGroup(null);
            doc.setGroupFolder(null);
        }
        documentRepository.save(doc);

        if (owner != null) {
            try {
                notificationService.notifyUser(owner, NotificationType.SYSTEM,
                        "Tài liệu bị ẩn",
                        "Tài liệu \"" + docTitle + "\" đã bị quản trị viên đặt về chế độ riêng tư do nghi đạo văn.",
                        doc.getId(), "/documents/" + doc.getId());
            } catch (Exception e) {
                log.warn("Failed to notify owner after PRIVATIZE: {}", e.getMessage());
            }
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private PlagiarismReportDetailResponse buildDetail(Document doc, List<DocumentSimilarity> rows) {
        DocumentSimilarity first = rows.get(0);
        return PlagiarismReportDetailResponse.builder()
                .suspectedDocumentId(doc.getId())
                .suspectedTitle(doc.getTitle())
                .suspectedOwnerEmail(doc.getUser() != null ? doc.getUser().getEmail() : null)
                .suspectedOwnerWarningCount(doc.getUser() != null ? doc.getUser().getPlagiarismWarningCount() : null)
                .suspectedSnippet(buildSnippet(doc.getExtractedText()))
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
                .matches(rows.stream()
                        .map(this::toMatchResponse)
                        .toList())
                .build();
    }

    private PlagiarismReportDetailResponse buildDetailAfterRemoval(Long docId, String title,
                                                                  User owner,
                                                                  PlagiarismStatus status,
                                                                  List<DocumentSimilarity> rowsBeforeDelete,
                                                                  User admin, String note,
                                                                  LocalDateTime resolvedAt) {
        return PlagiarismReportDetailResponse.builder()
                .suspectedDocumentId(docId)
                .suspectedTitle(title)
                .suspectedOwnerEmail(owner != null ? owner.getEmail() : null)
                .suspectedOwnerWarningCount(owner != null ? owner.getPlagiarismWarningCount() : null)
                .suspectedSnippet(null)
                .status(status)
                .triggerType(rowsBeforeDelete.isEmpty() ? null : rowsBeforeDelete.get(0).getTriggerType())
                .triggerContextId(rowsBeforeDelete.isEmpty() ? null : rowsBeforeDelete.get(0).getTriggerContextId())
                .maxScore(rowsBeforeDelete.stream()
                        .map(DocumentSimilarity::getSimilarityScore)
                        .filter(s -> s != null)
                        .max(Float::compareTo)
                        .orElse(null))
                .firstDetectedAt(rowsBeforeDelete.stream()
                        .map(DocumentSimilarity::getCalculatedAt)
                        .filter(c -> c != null)
                        .min(LocalDateTime::compareTo)
                        .orElse(null))
                .resolvedAt(resolvedAt)
                .resolverEmail(admin != null ? admin.getEmail() : null)
                .resolutionNote(note)
                .matches(List.of())
                .build();
    }

    private PlagiarismMatchResponse toMatchResponse(DocumentSimilarity row) {
        Document matched = row.getDocument2();
        return PlagiarismMatchResponse.builder()
                .matchedDocumentId(matched != null ? matched.getId() : null)
                .matchedTitle(matched != null ? matched.getTitle() : "(đã xóa)")
                .matchedOwnerEmail(matched != null && matched.getUser() != null
                        ? matched.getUser().getEmail() : null)
                .similarityScore(row.getSimilarityScore())
                .snippet(matched != null ? buildSnippet(matched.getExtractedText()) : null)
                .build();
    }

    private String buildSnippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 240) + "...";
    }

    // ── Helper exposed for controller list ────────────────────────────────────

    /** Group summary keyed by suspected doc — used by controller. */
    public Map<Long, Long> matchCountBySuspectedDoc(List<Long> docIds, PlagiarismStatus status) {
        Map<Long, Long> result = new HashMap<>();
        for (Long id : docIds) {
            result.put(id, similarityRepository.countByDocument1IdAndStatus(id, status));
        }
        return result;
    }
}
