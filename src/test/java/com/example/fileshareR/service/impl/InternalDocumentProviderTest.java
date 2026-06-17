package com.example.fileshareR.service.impl;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.service.NlpService;
import com.example.fileshareR.service.PlagiarismMatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalDocumentProviderTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private NlpService nlpService;

    private InternalDocumentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InternalDocumentProvider(documentRepository, nlpService, new ObjectMapper());
        ReflectionTestUtils.setField(provider, "enabled", true);
    }

    @Test
    void getName_returnsInternal() {
        assertThat(provider.getName()).isEqualTo("internal");
    }

    @Test
    void isEnabled_reflectsConfig() {
        assertThat(provider.isEnabled()).isTrue();
        ReflectionTestUtils.setField(provider, "enabled", false);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void findMatches_suspectedDocHasNoVector_returnsEmpty() {
        Document suspected = Document.builder().id(1L).user(user(1L)).build();
        // tfidfVector null

        assertThat(provider.findMatches(suspected, 0.5, 10)).isEmpty();
    }

    @Test
    void findMatches_blankVector_returnsEmpty() {
        Document suspected = Document.builder().id(1L).user(user(1L)).tfidfVector("   ").build();

        assertThat(provider.findMatches(suspected, 0.5, 10)).isEmpty();
    }

    @Test
    void findMatches_unparsableJsonVector_returnsEmpty() {
        Document suspected = Document.builder().id(1L).user(user(1L)).tfidfVector("not json").build();

        assertThat(provider.findMatches(suspected, 0.5, 10)).isEmpty();
    }

    @Test
    void findMatches_filtersOwnDocumentsAndUnapproved() {
        Document suspected = Document.builder().id(1L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}").build();
        Document ownOther = Document.builder().id(2L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("Own").build();
        Document pending = Document.builder().id(3L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.PENDING).title("Pending").build();
        Document candidate = Document.builder().id(4L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("Other approved")
                .extractedText("matching text content")
                .build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, ownOther, pending, candidate));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class))).thenReturn(0.9);

        List<PlagiarismMatch> matches = provider.findMatches(suspected, 0.5, 10);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).matchedDocumentId()).isEqualTo(4L);
        assertThat(matches.get(0).similarityScore()).isEqualTo(0.9);
        assertThat(matches.get(0).evidenceSnippet()).contains("matching text content");
    }

    @Test
    void findMatches_savedCopy_skipsSourceAndOriginalAuthorDocs() {
        // Tài liệu gốc của tác giả A (id=2, user=10)
        Document source = Document.builder().id(2L).user(user(10L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("Goc cua A").build();
        // Tài liệu khác cũng của tác giả A
        Document authorOther = Document.builder().id(3L).user(user(10L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("A khac").build();
        // Tài liệu của người khác (tác giả B)
        Document unrelated = Document.builder().id(4L).user(user(20L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("Cua B")
                .extractedText("unrelated text").build();
        // suspected = bản sao user 99 lưu về từ source của A
        Document suspected = Document.builder().id(1L).user(user(99L))
                .tfidfVector("{\"foo\": 1.0}")
                .sourceDocument(source).build();

        when(documentRepository.findAll()).thenReturn(List.of(suspected, source, authorOther, unrelated));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class))).thenReturn(0.99);

        List<PlagiarismMatch> matches = provider.findMatches(suspected, 0.5, 10);

        // Chỉ tài liệu của tác giả B được tính; source + tài liệu của tác giả gốc bị bỏ qua
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).matchedDocumentId()).isEqualTo(4L);
    }

    @Test
    void findMatches_originalDoc_skipsItsCopies() {
        // suspected = tài liệu gốc (id=1, user=10) đang được check
        Document suspected = Document.builder().id(1L).user(user(10L))
                .tfidfVector("{\"foo\": 1.0}").build();
        // copy = bản sao của suspected do user khác lưu về (trỏ source = suspected)
        Document copy = Document.builder().id(2L).user(user(99L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("Copy")
                .sourceDocument(Document.builder().id(1L).user(user(10L)).build()).build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, copy));
        // copy bị lọc trước khi tính cosine → không stub cosineSimilarity

        // Bản sao của chính nó không bị tính là đạo văn
        assertThat(provider.findMatches(suspected, 0.5, 10)).isEmpty();
    }

    @Test
    void findMatches_belowThreshold_filtered() {
        Document suspected = Document.builder().id(1L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}").build();
        Document candidate = Document.builder().id(2L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("Low").build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, candidate));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class))).thenReturn(0.3);

        assertThat(provider.findMatches(suspected, 0.5, 10)).isEmpty();
    }

    @Test
    void findMatches_limitsMaxResults() {
        Document suspected = Document.builder().id(1L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}").build();
        Document c1 = Document.builder().id(2L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("C1").build();
        Document c2 = Document.builder().id(3L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("C2").build();
        Document c3 = Document.builder().id(4L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("C3").build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, c1, c2, c3));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class)))
                .thenReturn(0.9, 0.95, 0.85);

        List<PlagiarismMatch> matches = provider.findMatches(suspected, 0.5, 2);

        assertThat(matches).hasSize(2);
        // Sorted descending by score
        assertThat(matches.get(0).similarityScore())
                .isGreaterThanOrEqualTo(matches.get(1).similarityScore());
    }

    @Test
    void findMatches_skipsCandidatesWithBlankVector() {
        Document suspected = Document.builder().id(1L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}").build();
        Document blank = Document.builder().id(2L).user(user(2L)).tfidfVector("")
                .moderationStatus(ModerationStatus.APPROVED).build();
        Document nullVec = Document.builder().id(3L).user(user(2L))
                .moderationStatus(ModerationStatus.APPROVED).build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, blank, nullVec));

        assertThat(provider.findMatches(suspected, 0.5, 10)).isEmpty();
    }

    @Test
    void findMatches_longSnippetTruncated() {
        Document suspected = Document.builder().id(1L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}").build();
        String longText = "a".repeat(500);
        Document candidate = Document.builder().id(2L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("L")
                .extractedText(longText).build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, candidate));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class))).thenReturn(0.9);

        List<PlagiarismMatch> matches = provider.findMatches(suspected, 0.5, 10);

        assertThat(matches.get(0).evidenceSnippet()).endsWith("...");
        assertThat(matches.get(0).evidenceSnippet().length()).isLessThanOrEqualTo(243);
    }

    @Test
    void findMatches_nullExtractedText_snippetNull() {
        Document suspected = Document.builder().id(1L).user(user(1L))
                .tfidfVector("{\"foo\": 1.0}").build();
        Document candidate = Document.builder().id(2L).user(user(2L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("X").build();
        // extractedText null
        when(documentRepository.findAll()).thenReturn(List.of(suspected, candidate));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class))).thenReturn(0.9);

        List<PlagiarismMatch> matches = provider.findMatches(suspected, 0.5, 10);

        assertThat(matches.get(0).evidenceSnippet()).isNull();
    }

    @Test
    void findMatches_suspectedNullUser_canStillMatchAllApproved() {
        Document suspected = Document.builder().id(1L).user(null)
                .tfidfVector("{\"foo\": 1.0}").build();
        Document candidate = Document.builder().id(2L).user(user(5L))
                .tfidfVector("{\"foo\": 1.0}")
                .moderationStatus(ModerationStatus.APPROVED).title("X").build();
        when(documentRepository.findAll()).thenReturn(List.of(suspected, candidate));
        when(nlpService.cosineSimilarity(any(Map.class), any(Map.class))).thenReturn(0.9);

        List<PlagiarismMatch> matches = provider.findMatches(suspected, 0.5, 10);

        assertThat(matches).hasSize(1);
    }

    private static User user(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").build();
    }
}
