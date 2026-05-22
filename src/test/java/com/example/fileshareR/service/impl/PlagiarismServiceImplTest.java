package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.ResolvePlagiarismRequest;
import com.example.fileshareR.dto.request.ResolvePlagiarismRequest.Action;
import com.example.fileshareR.dto.response.PlagiarismReportDetailResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.DocumentSimilarity;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.Group;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlagiarismServiceImplTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentSimilarityRepository similarityRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private DocumentService documentService;
    @Mock private PlagiarismCheckExecutor checkExecutor;

    private PlagiarismServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlagiarismServiceImpl(documentRepository, folderRepository,
                similarityRepository, userRepository, notificationService,
                documentService, checkExecutor);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "autoBanThreshold", 3);
    }

    // ── checkDocumentAsync ──────────────────────────────────────────────────

    @Test
    void checkDocumentAsync_disabled_skips() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.checkDocumentAsync(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(checkExecutor, never()).checkDocument(any(), any(), any());
    }

    @Test
    void checkDocumentAsync_enabled_delegatesToExecutor() {
        service.checkDocumentAsync(1L, PlagiarismTriggerType.FOLDER_PUBLIC, 5L);

        verify(checkExecutor).checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, 5L);
    }

    @Test
    void checkDocumentAsync_executorThrows_swallowed() {
        doThrow(new RuntimeException("db down")).when(checkExecutor)
                .checkDocument(any(), any(), any());

        // No throw expected
        service.checkDocumentAsync(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(checkExecutor).checkDocument(any(), any(), any());
    }

    // ── checkFolderTreeAsync ────────────────────────────────────────────────

    @Test
    void checkFolderTreeAsync_disabled_skips() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.checkFolderTreeAsync(1L);

        verify(checkExecutor, never()).checkDocument(any(), any(), any());
    }

    @Test
    void checkFolderTreeAsync_recursivelyVisitsSubFolders() {
        Folder child = Folder.builder().id(2L).build();
        Document d1 = Document.builder().id(10L).build();
        Document d2 = Document.builder().id(11L).build();

        when(documentRepository.findByFolderId(1L)).thenReturn(List.of(d1));
        when(documentRepository.findByFolderId(2L)).thenReturn(List.of(d2));
        when(folderRepository.findByParentId(1L)).thenReturn(List.of(child));
        when(folderRepository.findByParentId(2L)).thenReturn(List.of());

        service.checkFolderTreeAsync(1L);

        verify(checkExecutor).checkDocument(10L, PlagiarismTriggerType.FOLDER_PUBLIC, 1L);
        verify(checkExecutor).checkDocument(11L, PlagiarismTriggerType.FOLDER_PUBLIC, 1L);
    }

    @Test
    void checkFolderTreeAsync_executorThrowsPerDoc_otherDocsContinue() {
        Document d1 = Document.builder().id(10L).build();
        Document d2 = Document.builder().id(11L).build();
        when(documentRepository.findByFolderId(1L)).thenReturn(List.of(d1, d2));
        when(folderRepository.findByParentId(1L)).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(checkExecutor)
                .checkDocument(eq(10L), any(), any());

        service.checkFolderTreeAsync(1L);

        verify(checkExecutor).checkDocument(eq(11L), any(), any());
    }

    // ── resolveReport: not-found ─────────────────────────────────────────────

    @Test
    void resolveReport_documentMissing_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        ResolvePlagiarismRequest req = req(Action.IGNORE, "note");

        assertThatThrownBy(() -> service.resolveReport(99L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void resolveReport_adminMissing_throws() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(
                Document.builder().id(1L).title("X").user(user(2L)).build()));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveReport(1L, req(Action.IGNORE, "n"), 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void resolveReport_noSimilarityRows_throwsBadRequest() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(
                Document.builder().id(1L).title("X").user(user(2L)).build()));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveReport(1L, req(Action.IGNORE, "n"), 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // ── resolveReport: KEEP (false positive) ────────────────────────────────

    @Test
    void resolveReport_keep_marksResolvedKept() {
        Document doc = Document.builder().id(1L).title("Doc").user(user(2L)).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).title("M").user(user(3L)).build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        PlagiarismReportDetailResponse resp = service.resolveReport(1L,
                req(Action.KEEP, "false positive"), 99L);

        assertThat(row.getStatus()).isEqualTo(PlagiarismStatus.RESOLVED_KEPT);
        assertThat(row.getResolutionNote()).isEqualTo("false positive");
        assertThat(resp.getStatus()).isEqualTo(PlagiarismStatus.RESOLVED_KEPT);
        verify(similarityRepository).saveAll(List.of(row));
    }

    // ── resolveReport: IGNORE ───────────────────────────────────────────────

    @Test
    void resolveReport_ignore_marksIgnoredAndPersists() {
        Document doc = Document.builder().id(1L).title("Doc").user(user(2L)).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).title("M").user(user(3L)).build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.IGNORE, "ok"), 99L);

        assertThat(row.getStatus()).isEqualTo(PlagiarismStatus.IGNORED);
    }

    // ── resolveReport: PRIVATIZE ────────────────────────────────────────────

    @Test
    void resolveReport_privatize_setsDocPrivate_andNotifies() {
        User owner = user(2L);
        Folder publicFolder = Folder.builder().id(10L)
                .visibility(FolderVisibilityType.PUBLIC)
                .shareToken("tok").build();
        Document doc = Document.builder().id(1L).title("Doc").user(owner)
                .visibility(VisibilityType.PUBLIC)
                .folder(publicFolder).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).title("M").user(user(3L)).build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.PRIVATIZE, "hide"), 99L);

        assertThat(doc.getVisibility()).isEqualTo(VisibilityType.PRIVATE);
        assertThat(publicFolder.getVisibility()).isEqualTo(FolderVisibilityType.PRIVATE);
        assertThat(publicFolder.getShareToken()).isNull();
        verify(folderRepository).save(publicFolder);
        verify(documentRepository).save(doc);
        verify(notificationService).notifyUser(eq(owner),
                eq(NotificationType.SYSTEM), any(), any(), any(), any());
    }

    @Test
    void resolveReport_privatize_groupDoc_removesFromGroup() {
        User owner = user(2L);
        Group group = Group.builder().id(20L).build();
        Document doc = Document.builder().id(1L).title("Doc").user(owner)
                .visibility(VisibilityType.PUBLIC)
                .group(group).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).user(user(3L)).title("M").build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.PRIVATIZE, "x"), 99L);

        assertThat(doc.getGroup()).isNull();
    }

    @Test
    void resolveReport_privatize_notificationFailure_swallowed() {
        User owner = user(2L);
        Document doc = Document.builder().id(1L).title("Doc").user(owner)
                .visibility(VisibilityType.PUBLIC).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).user(user(3L)).title("M").build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));
        doThrow(new RuntimeException("noti down"))
                .when(notificationService).notifyUser(any(User.class), any(), any(), any(), any(), any());

        // Should not throw
        service.resolveReport(1L, req(Action.PRIVATIZE, "x"), 99L);
    }

    // ── resolveReport: REMOVE ───────────────────────────────────────────────

    @Test
    void resolveReport_remove_incrementsWarningAndDeletes() {
        User owner = user(2L);
        owner.setPlagiarismWarningCount(1);
        owner.setIsActive(true);
        Document doc = Document.builder().id(1L).title("Doc").user(owner).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).user(user(3L)).title("M").build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.REMOVE, "violation"), 99L);

        assertThat(owner.getPlagiarismWarningCount()).isEqualTo(2);
        assertThat(owner.getIsActive()).isTrue(); // Below threshold (3)
        verify(userRepository).save(owner);
        verify(similarityRepository).deleteAll(List.of(row));
        verify(documentService).adminDeleteDocument(1L);
    }

    @Test
    void resolveReport_remove_atThreshold_bansUser() {
        User owner = user(2L);
        owner.setPlagiarismWarningCount(2); // After +1 = 3 = threshold
        owner.setIsActive(true);
        Document doc = Document.builder().id(1L).title("Doc").user(owner).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).user(user(3L)).title("M").build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.REMOVE, "violation"), 99L);

        assertThat(owner.getPlagiarismWarningCount()).isEqualTo(3);
        assertThat(owner.getIsActive()).isFalse(); // Auto-banned
        verify(notificationService).notifyUser(eq(owner),
                eq(NotificationType.USER_BANNED_BY_PLATFORM), any(), any(), any(), any());
    }

    @Test
    void resolveReport_remove_nullOwnerWarning_initialisesAsZero() {
        User owner = user(2L); // plagiarismWarningCount null by default
        owner.setIsActive(true);
        Document doc = Document.builder().id(1L).title("Doc").user(owner).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).user(user(3L)).title("M").build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.REMOVE, "v"), 99L);

        assertThat(owner.getPlagiarismWarningCount()).isEqualTo(1);
    }

    @Test
    void resolveReport_remove_nullOwner_stillDeletesDoc() {
        Document doc = Document.builder().id(1L).title("Doc").user(null).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).title("M").user(user(3L)).build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));

        service.resolveReport(1L, req(Action.REMOVE, "v"), 99L);

        verify(documentService).adminDeleteDocument(1L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolveReport_remove_notifyOwnerFailsSwallowed() {
        User owner = user(2L);
        owner.setPlagiarismWarningCount(0);
        owner.setIsActive(true);
        Document doc = Document.builder().id(1L).title("Doc").user(owner).build();
        DocumentSimilarity row = DocumentSimilarity.builder().status(PlagiarismStatus.PENDING)
                .document2(Document.builder().id(5L).user(user(3L)).title("M").build())
                .similarityScore(0.9f).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L)));
        when(similarityRepository.findByDocument1IdAndStatusIsNotNullOrderBySimilarityScoreDesc(1L))
                .thenReturn(List.of(row));
        doThrow(new RuntimeException("smtp"))
                .when(notificationService).notifyUser(any(User.class), any(), any(), any(), any(), any());

        // Should not throw
        service.resolveReport(1L, req(Action.REMOVE, "v"), 99L);

        verify(documentService).adminDeleteDocument(1L);
    }

    // ── matchCountBySuspectedDoc helper ─────────────────────────────────────

    @Test
    void matchCountBySuspectedDoc_aggregatesPerId() {
        when(similarityRepository.countByDocument1IdAndStatus(1L, PlagiarismStatus.PENDING)).thenReturn(3L);
        when(similarityRepository.countByDocument1IdAndStatus(2L, PlagiarismStatus.PENDING)).thenReturn(0L);

        Map<Long, Long> result = service.matchCountBySuspectedDoc(
                List.of(1L, 2L), PlagiarismStatus.PENDING);

        assertThat(result).containsEntry(1L, 3L).containsEntry(2L, 0L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static User user(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").fullName("U" + id).build();
    }

    private static ResolvePlagiarismRequest req(Action action, String note) {
        ResolvePlagiarismRequest r = new ResolvePlagiarismRequest();
        r.setAction(action);
        r.setNote(note);
        return r;
    }
}
