package com.example.fileshareR.service.impl;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.DocumentSimilarity;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.NotificationType;
import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.enums.PlagiarismTriggerType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.DocumentSimilarityRepository;
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.PlagiarismMatch;
import com.example.fileshareR.service.PlagiarismSourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlagiarismCheckExecutorTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentSimilarityRepository similarityRepository;
    @Mock private NotificationService notificationService;
    @Mock private PlagiarismSourceProvider provider;

    private PlagiarismCheckExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PlagiarismCheckExecutor(documentRepository, similarityRepository,
                notificationService, List.of(provider));
        ReflectionTestUtils.setField(executor, "threshold", 0.7);
        ReflectionTestUtils.setField(executor, "maxMatches", 10);
    }

    // ── early-exit ──────────────────────────────────────────────────────────

    @Test
    void checkDocument_documentMissing_noop() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        executor.checkDocument(99L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(similarityRepository, never()).save(any());
        verify(notificationService, never()).notifyAllAdmins(any(), any(), any(), any(), any());
    }

    @Test
    void checkDocument_disabledProvider_skipped() {
        Document doc = Document.builder().id(1L).user(user(1L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(provider.isEnabled()).thenReturn(false);

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(provider, never()).findMatches(any(), anyDouble(), anyInt());
        verify(similarityRepository, never()).save(any());
    }

    @Test
    void checkDocument_providerThrows_caughtAndContinues() {
        Document doc = Document.builder().id(1L).user(user(1L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        doThrow(new RuntimeException("boom"))
                .when(provider).findMatches(any(), anyDouble(), anyInt());

        // No throw expected — caught best-effort
        executor.checkDocument(1L, PlagiarismTriggerType.GROUP_PUBLIC_UPLOAD, null);

        verify(similarityRepository, never()).save(any());
    }

    @Test
    void checkDocument_noMatches_noPersistenceNoNotification() {
        Document doc = Document.builder().id(1L).user(user(1L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(List.of());

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(similarityRepository, never()).save(any());
        verify(notificationService, never()).notifyAllAdmins(any(), any(), any(), any(), any());
    }

    // ── new row + notification path ─────────────────────────────────────────

    @Test
    void checkDocument_newMatch_persistsRowAndNotifiesAdmins() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("Suspect").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(matched));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PlagiarismMatch(2L, null, "M", "u2@x.com", 0.9, "snippet")));
        when(similarityRepository.countByDocument1IdAndStatus(eq(1L), eq(PlagiarismStatus.PENDING)))
                .thenReturn(0L); // first report
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.empty());

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        ArgumentCaptor<DocumentSimilarity> cap = ArgumentCaptor.forClass(DocumentSimilarity.class);
        verify(similarityRepository).save(cap.capture());
        DocumentSimilarity saved = cap.getValue();
        assertThat(saved.getDocument1()).isEqualTo(suspect);
        assertThat(saved.getDocument2()).isEqualTo(matched);
        assertThat(saved.getStatus()).isEqualTo(PlagiarismStatus.PENDING);
        assertThat(saved.getSimilarityScore()).isEqualTo(0.9f);
        verify(notificationService).notifyAllAdmins(eq(NotificationType.PLAGIARISM_REPORT),
                any(), any(), eq(1L), any());
    }

    @Test
    void checkDocument_matchedDocMissing_skipped() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PlagiarismMatch(999L, null, "Ghost", "g@x.com", 0.9, null)));
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 999L))
                .thenReturn(Optional.empty());

        executor.checkDocument(1L, PlagiarismTriggerType.GROUP_PUBLIC_UPLOAD, null);

        verify(similarityRepository, never()).save(any());
    }

    @Test
    void checkDocument_externalMatchOnlyNoMatchedId_skipped() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(null, "http://example.com", "Ext", null, 0.95, null)));

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(similarityRepository, never()).save(any());
    }

    // ── existing-row branches ───────────────────────────────────────────────

    @Test
    void checkDocument_existingPendingRow_lowerScore_updates() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        DocumentSimilarity existing = DocumentSimilarity.builder()
                .document1(suspect).document2(matched)
                .similarityScore(0.7f)
                .status(PlagiarismStatus.PENDING).build();

        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(2L, null, "M", "u@x.com", 0.95, null)));
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.of(existing));

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        assertThat(existing.getSimilarityScore()).isEqualTo(0.95f);
        verify(similarityRepository).save(existing);
        // Updated existing row, NOT counted as newRow → no notify since isNewReport may be true but newRowCount=0
    }

    @Test
    void checkDocument_existingResolvedKeptRow_noChange() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        DocumentSimilarity existing = DocumentSimilarity.builder()
                .document1(suspect).document2(matched)
                .similarityScore(0.8f)
                .status(PlagiarismStatus.RESOLVED_KEPT).build();

        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(2L, null, "M", "u@x.com", 0.95, null)));
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.of(existing));

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        // RESOLVED_KEPT must not be touched
        assertThat(existing.getStatus()).isEqualTo(PlagiarismStatus.RESOLVED_KEPT);
        assertThat(existing.getSimilarityScore()).isEqualTo(0.8f);
        verify(similarityRepository, never()).save(any());
    }

    @Test
    void checkDocument_existingIgnoredRow_noChange() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        DocumentSimilarity existing = DocumentSimilarity.builder()
                .document1(suspect).document2(matched)
                .similarityScore(0.8f)
                .status(PlagiarismStatus.IGNORED).build();

        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(2L, null, "M", "u@x.com", 0.95, null)));
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.of(existing));

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(similarityRepository, never()).save(any());
    }

    @Test
    void checkDocument_existingResolvedPrivatized_reopensToPending() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        DocumentSimilarity existing = DocumentSimilarity.builder()
                .document1(suspect).document2(matched)
                .similarityScore(0.7f)
                .status(PlagiarismStatus.RESOLVED_PRIVATIZED)
                .resolvedBy(user(99L))
                .resolutionNote("admin cleared").build();

        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(2L, null, "M", "u@x.com", 0.92, null)));
        when(similarityRepository.countByDocument1IdAndStatus(eq(1L), eq(PlagiarismStatus.PENDING)))
                .thenReturn(0L);
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.of(existing));

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        // Reopened
        assertThat(existing.getStatus()).isEqualTo(PlagiarismStatus.PENDING);
        assertThat(existing.getSimilarityScore()).isEqualTo(0.92f);
        assertThat(existing.getResolvedBy()).isNull();
        assertThat(existing.getResolutionNote()).isNull();
        verify(similarityRepository).save(existing);
    }

    // ── notification suppression on non-new reports ─────────────────────────

    @Test
    void checkDocument_alreadyReported_noNewNotification() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(matched));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(2L, null, "M", "u@x.com", 0.9, null)));
        when(similarityRepository.countByDocument1IdAndStatus(eq(1L), eq(PlagiarismStatus.PENDING)))
                .thenReturn(3L); // already reports pending → not new
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.empty());

        executor.checkDocument(1L, PlagiarismTriggerType.FOLDER_PUBLIC, null);

        verify(similarityRepository).save(any(DocumentSimilarity.class));
        verify(notificationService, never()).notifyAllAdmins(any(), any(), any(), any(), any());
    }

    @Test
    void checkDocument_notifyFailure_swallowed() {
        Document suspect = Document.builder().id(1L).user(user(1L)).title("S").build();
        Document matched = Document.builder().id(2L).user(user(2L)).title("M").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(suspect));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(matched));
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getName()).thenReturn("internal");
        when(provider.findMatches(any(), anyDouble(), anyInt())).thenReturn(
                List.of(new PlagiarismMatch(2L, null, "M", "u@x.com", 0.9, null)));
        when(similarityRepository.countByDocument1IdAndStatus(anyLong(), any())).thenReturn(0L);
        when(similarityRepository.findByDocument1IdAndDocument2Id(1L, 2L))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("noti down"))
                .when(notificationService).notifyAllAdmins(any(), any(), any(), any(), any());

        // Should not throw
        executor.checkDocument(1L, PlagiarismTriggerType.GROUP_PUBLIC_UPLOAD, 50L);

        verify(notificationService).notifyAllAdmins(any(), any(), any(), any(), any());
    }

    private static User user(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").build();
    }
}
