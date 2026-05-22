package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.DocumentAnalysisResponse;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.enums.VisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.DocumentSimilarityRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.repository.GroupBanRepository;
import com.example.fileshareR.repository.GroupFolderRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.ContentModerationService;
import com.example.fileshareR.service.FileStorageService;
import com.example.fileshareR.service.NlpService;
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.PlagiarismService;
import com.example.fileshareR.service.StorageQuotaService;
import com.example.fileshareR.service.TextExtractionService;
import com.example.fileshareR.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplExtraTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentSimilarityRepository similarityRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private UserService userService;
    @Mock private FileStorageService fileStorageService;
    @Mock private TextExtractionService textExtractionService;
    @Mock private NlpService nlpService;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupBanRepository groupBanRepository;
    @Mock private GroupFolderRepository groupFolderRepository;
    @Mock private StorageQuotaService storageQuotaService;
    @Mock private ContentModerationService contentModerationService;
    @Mock private NotificationService notificationService;
    @Mock private ObjectProvider<PlagiarismService> plagiarismServiceProvider;

    private DocumentServiceImpl service;
    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(
                documentRepository, similarityRepository, folderRepository, userService,
                fileStorageService, textExtractionService, nlpService, realObjectMapper,
                groupRepository, groupMemberRepository, groupBanRepository, groupFolderRepository,
                storageQuotaService, contentModerationService, notificationService,
                plagiarismServiceProvider);
    }

    // ── downloadDocument ────────────────────────────────────────────────────

    @Test
    void downloadDocument_notFound_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadDocument(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void downloadDocument_privateNotOwner_throws() {
        Document d = Document.builder().id(1L).user(owner(99L))
                .visibility(VisibilityType.PRIVATE).fileUrl("p").downloadCount(0).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.downloadDocument(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void downloadDocument_publicDoc_anonymousAccess_works(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.pdf");
        Files.writeString(file, "data");
        Document d = Document.builder().id(1L).user(owner(99L))
                .title("T").visibility(VisibilityType.PUBLIC)
                .fileUrl("test.pdf").downloadCount(0).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(fileStorageService.getFilePath("test.pdf")).thenReturn(file);

        Resource r = service.downloadDocument(1L, null);

        assertThat(r).isNotNull();
        assertThat(d.getDownloadCount()).isEqualTo(1); // incremented
        verify(notificationService).notifyUser(eq(d.getUser()), any(), any(), any(), eq(1L), any());
    }

    @Test
    void downloadDocument_owner_doesNotNotifyItself(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.pdf");
        Files.writeString(file, "data");
        Document d = Document.builder().id(1L).user(owner(1L))
                .title("T").visibility(VisibilityType.PRIVATE)
                .fileUrl("test.pdf").downloadCount(5).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(fileStorageService.getFilePath("test.pdf")).thenReturn(file);

        service.downloadDocument(1L, 1L);

        assertThat(d.getDownloadCount()).isEqualTo(6);
        verify(notificationService, never()).notifyUser(any(User.class), any(), any(), any(), any(), any());
    }

    @Test
    void downloadDocument_publicFolderAccess(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.pdf");
        Files.writeString(file, "data");
        Folder publicFolder = Folder.builder().visibility(FolderVisibilityType.PUBLIC).build();
        Document d = Document.builder().id(1L).user(owner(99L))
                .title("T").visibility(VisibilityType.PRIVATE)
                .folder(publicFolder)
                .fileUrl("test.pdf").downloadCount(0).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(fileStorageService.getFilePath("test.pdf")).thenReturn(file);

        // Non-owner accessing via public folder
        Resource r = service.downloadDocument(1L, 1L);

        assertThat(r).isNotNull();
    }

    @Test
    void downloadDocument_fileNotFound_throws() {
        Document d = Document.builder().id(1L).user(owner(1L))
                .title("T").visibility(VisibilityType.PRIVATE)
                .fileUrl("missing").downloadCount(0).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(fileStorageService.getFilePath("missing")).thenReturn(Path.of("/nonexistent/missing.pdf"));

        assertThatThrownBy(() -> service.downloadDocument(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    // ── previewDocument ─────────────────────────────────────────────────────

    @Test
    void previewDocument_groupMember_canPreview(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("p.pdf");
        Files.writeString(file, "data");
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PRIVATE).build();
        Document d = Document.builder().id(1L).user(owner(99L))
                .visibility(VisibilityType.PRIVATE)
                .group(group)
                .moderationStatus(ModerationStatus.APPROVED)
                .fileUrl("p.pdf").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.existsByGroupIdAndUserId(20L, 1L)).thenReturn(true);
        when(fileStorageService.getFilePath("p.pdf")).thenReturn(file);

        assertThat(service.previewDocument(1L, 1L)).isNotNull();
    }

    @Test
    void previewDocument_groupPublic_anyoneCanPreview(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("p.pdf");
        Files.writeString(file, "data");
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PUBLIC).build();
        Document d = Document.builder().id(1L).user(owner(99L))
                .visibility(VisibilityType.PRIVATE)
                .group(group)
                .moderationStatus(ModerationStatus.APPROVED)
                .fileUrl("p.pdf").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(fileStorageService.getFilePath("p.pdf")).thenReturn(file);

        assertThat(service.previewDocument(1L, null)).isNotNull();
    }

    @Test
    void previewDocument_noAccess_throws() {
        Document d = Document.builder().id(1L).user(owner(99L))
                .visibility(VisibilityType.PRIVATE).fileUrl("p.pdf").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.previewDocument(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void previewDocument_pendingModeration_nonOwnerNonAdmin_throws() {
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PUBLIC).build();
        Document d = Document.builder().id(1L).user(owner(99L))
                .visibility(VisibilityType.PRIVATE)
                .group(group)
                .moderationStatus(ModerationStatus.PENDING)
                .fileUrl("p.pdf").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewDocument(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void previewDocument_pendingModeration_adminCanPreview(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("p.pdf");
        Files.writeString(file, "data");
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PUBLIC).build();
        Document d = Document.builder().id(1L).user(owner(99L))
                .visibility(VisibilityType.PRIVATE)
                .group(group)
                .moderationStatus(ModerationStatus.PENDING)
                .fileUrl("p.pdf").build();
        GroupMember adminMember = GroupMember.builder().role(GroupMemberRole.ADMIN).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L)).thenReturn(Optional.of(adminMember));
        when(fileStorageService.getFilePath("p.pdf")).thenReturn(file);

        assertThat(service.previewDocument(1L, 1L)).isNotNull();
    }

    // ── analyzeDocument ─────────────────────────────────────────────────────

    @Test
    void analyzeDocument_notOwner_throws() {
        Document d = Document.builder().id(1L).user(owner(99L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.analyzeDocument(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void analyzeDocument_owner_returnsAnalysis() {
        Document d = Document.builder().id(1L).user(owner(1L)).title("T")
                .extractedText("Some text here")
                .keywords("[\"alpha\",\"beta\"]")
                .tfidfVector("{\"alpha\":0.9,\"beta\":0.5,\"gamma\":0.3}")
                .build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        DocumentAnalysisResponse out = service.analyzeDocument(1L, 1L);

        assertThat(out).isNotNull();
        assertThat(out.getTopTerms()).isNotEmpty();
    }

    @Test
    void analyzeDocument_malformedKeywordsJson_continuesGracefully() {
        Document d = Document.builder().id(1L).user(owner(1L)).title("T")
                .extractedText("text").keywords("not-json")
                .build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        // Should not throw despite parse failure
        DocumentAnalysisResponse out = service.analyzeDocument(1L, 1L);

        assertThat(out).isNotNull();
    }

    // ── reprocessDocumentNlp ────────────────────────────────────────────────

    @Test
    void reprocessDocumentNlp_notOwner_throws() {
        Document d = Document.builder().id(1L).user(owner(99L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.reprocessDocumentNlp(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void reprocessDocumentNlp_extractsAndPersistsNewKeywords() throws Exception {
        Document d = Document.builder().id(1L).user(owner(1L)).title("T")
                .extractedText("Existing extracted text content").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(nlpService.extractKeywordsWithAI(anyString(), anyInt()))
                .thenReturn(List.of("alpha", "beta"));
        when(nlpService.generateSummaryWithAI(anyString(), anyInt()))
                .thenReturn("Summary text");
        when(nlpService.calculateTfIdf(anyString())).thenReturn(Map.of("alpha", 0.9));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentResponse out = service.reprocessDocumentNlp(1L, 1L);

        assertThat(out).isNotNull();
        assertThat(d.getKeywords()).contains("alpha").contains("beta");
        assertThat(d.getSummary()).isEqualTo("Summary text");
    }

    // ── findSimilarDocuments ────────────────────────────────────────────────

    @Test
    void findSimilarDocuments_notFound_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSimilarDocuments(99L, 1L, 5))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void findSimilarDocuments_anonymousAccessAllowed_returnsEmptyWhenNoSimilarDocs() {
        // Semantic mới: gợi ý tài liệu PUBLIC liên quan, anonymous access OK (userId=null)
        Document d = Document.builder().id(1L).user(owner(99L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(similarityRepository.findRelatedByDocumentId(1L)).thenReturn(List.of());

        // userId=null (anonymous) — không throw ACCESS_DENIED
        assertThat(service.findSimilarDocuments(1L, null, 5)).isEmpty();
    }

    @Test
    void findSimilarDocuments_noTfidfVector_returnsEmpty() {
        Document d = Document.builder().id(1L).user(owner(1L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        // Cache miss → fallback live-compute, source không có tfidfVector → empty
        when(similarityRepository.findRelatedByDocumentId(1L)).thenReturn(List.of());

        assertThat(service.findSimilarDocuments(1L, 1L, 5)).isEmpty();
    }

    @Test
    void findSimilarDocuments_cacheHit_returnsPublicApproved_filtersPrivate() {
        // Source doc id=1; cache có 3 rows trỏ tới 3 doc khác:
        //   - doc 10 PUBLIC + APPROVED  → pass
        //   - doc 11 PRIVATE            → filter out
        //   - doc 12 PUBLIC + REJECTED  → filter out
        Document source = Document.builder().id(1L).user(owner(1L)).title("Source").build();
        Document pub = Document.builder().id(10L).user(owner(2L)).title("Pub")
                .visibility(VisibilityType.PUBLIC)
                .moderationStatus(ModerationStatus.APPROVED).build();
        Document priv = Document.builder().id(11L).user(owner(2L)).title("Priv")
                .visibility(VisibilityType.PRIVATE)
                .moderationStatus(ModerationStatus.APPROVED).build();
        Document rejected = Document.builder().id(12L).user(owner(2L)).title("Rej")
                .visibility(VisibilityType.PUBLIC)
                .moderationStatus(ModerationStatus.REJECTED).build();

        com.example.fileshareR.entity.DocumentSimilarity row1 =
                com.example.fileshareR.entity.DocumentSimilarity.builder()
                        .document1(source).document2(pub).similarityScore(0.5f).build();
        com.example.fileshareR.entity.DocumentSimilarity row2 =
                com.example.fileshareR.entity.DocumentSimilarity.builder()
                        .document1(source).document2(priv).similarityScore(0.4f).build();
        com.example.fileshareR.entity.DocumentSimilarity row3 =
                com.example.fileshareR.entity.DocumentSimilarity.builder()
                        .document1(rejected).document2(source).similarityScore(0.3f).build();

        when(documentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(similarityRepository.findRelatedByDocumentId(1L))
                .thenReturn(List.of(row1, row2, row3));

        List<DocumentResponse> out = service.findSimilarDocuments(1L, null, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getTitle()).isEqualTo("Pub");
    }

    @Test
    void findSimilarDocuments_liveCompute_returnsAboveThreshold_sortedDesc() {
        // Source doc có tfidfVector, cache empty → fallback live-compute.
        // 2 candidates: similar (>0.1) + dissimilar (≤0.1). Chỉ similar trả về.
        Document source = Document.builder().id(1L).user(owner(1L)).title("Source")
                .tfidfVector("{\"a\":0.9}").build();
        Document similar = Document.builder().id(10L).user(owner(2L)).title("Similar")
                .visibility(VisibilityType.PUBLIC)
                .moderationStatus(ModerationStatus.APPROVED)
                .tfidfVector("{\"a\":0.8}").build();
        Document dissimilar = Document.builder().id(11L).user(owner(2L)).title("Other")
                .visibility(VisibilityType.PUBLIC)
                .moderationStatus(ModerationStatus.APPROVED)
                .tfidfVector("{\"b\":0.9}").build();

        when(documentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(similarityRepository.findRelatedByDocumentId(1L)).thenReturn(List.of());
        when(documentRepository.findByVisibilityAndModerationStatus(
                VisibilityType.PUBLIC, ModerationStatus.APPROVED))
                .thenReturn(List.of(source, similar, dissimilar));
        when(nlpService.cosineSimilarity(any(), any())).thenReturn(0.7, 0.05);

        List<DocumentResponse> out = service.findSimilarDocuments(1L, null, 5);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getTitle()).isEqualTo("Similar");
    }

    // ── getGroupDocuments ───────────────────────────────────────────────────

    @Test
    void getGroupDocuments_groupNotFound_throws() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupDocuments(99L, null, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void getGroupDocuments_privateGroup_nonMember_throws() {
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PRIVATE).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(20L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.getGroupDocuments(20L, null, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
    }

    @Test
    void getGroupDocuments_publicGroup_anyoneCanView() {
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PUBLIC).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(documentRepository.findByGroupId(20L))
                .thenReturn(List.of(Document.builder().id(1L).user(owner(2L))
                        .title("PublicDoc")
                        .moderationStatus(ModerationStatus.APPROVED).build()));

        List<DocumentResponse> out = service.getGroupDocuments(20L, null, null);

        assertThat(out).hasSize(1);
    }

    // ── deleteGroupDocument ─────────────────────────────────────────────────

    @Test
    void deleteGroupDocument_docNotFound_throws() {
        Group group = Group.builder().id(20L).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteGroupDocument(99L, 20L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_DOCUMENT_NOT_FOUND);
    }

    @Test
    void deleteGroupDocument_ownerCanDelete() {
        User owner = owner(1L);
        Group group = Group.builder().id(20L).build();
        Document d = Document.builder().id(1L).user(owner).group(group)
                .fileSize(500L).fileUrl("p").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.MEMBER).build()));

        service.deleteGroupDocument(1L, 20L, 1L);

        verify(documentRepository).delete(d);
        verify(storageQuotaService).decrementGroupUsage(group, 500L);
    }

    @Test
    void deleteGroupDocument_nonOwnerNonAdmin_throws() {
        User owner = owner(99L);
        Group group = Group.builder().id(20L).build();
        Document d = Document.builder().id(1L).user(owner).group(group).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.MEMBER).build()));

        assertThatThrownBy(() -> service.deleteGroupDocument(1L, 20L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void deleteGroupDocument_groupAdmin_canDelete() {
        Group group = Group.builder().id(20L).build();
        Document d = Document.builder().id(1L).user(owner(99L)).group(group)
                .fileSize(0L).fileUrl("p").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.ADMIN).build()));

        service.deleteGroupDocument(1L, 20L, 1L);

        verify(documentRepository).delete(d);
    }

    // ── downloadGroupDocument ───────────────────────────────────────────────

    @Test
    void downloadGroupDocument_docNotFound_throws() {
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PUBLIC).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadGroupDocument(99L, 20L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_DOCUMENT_NOT_FOUND);
    }

    @Test
    void downloadGroupDocument_privateGroup_nonMember_throws() {
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PRIVATE).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(20L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.downloadGroupDocument(1L, 20L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
    }

    @Test
    void downloadGroupDocument_publicGroup_anyAccess(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("g.pdf");
        Files.writeString(file, "data");
        Group group = Group.builder().id(20L).visibility(GroupVisibilityType.PUBLIC).build();
        Document d = Document.builder().id(1L).group(group).user(owner(99L))
                .title("X")
                .moderationStatus(ModerationStatus.APPROVED)
                .fileUrl("g.pdf").downloadCount(0).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(fileStorageService.getFilePath("g.pdf")).thenReturn(file);

        org.springframework.core.io.Resource r = service.downloadGroupDocument(1L, 20L, null);

        assertThat(r).isNotNull();
        assertThat(d.getDownloadCount()).isEqualTo(1);
    }

    // ── getPendingGroupDocuments ────────────────────────────────────────────

    @Test
    void getPendingGroupDocuments_admin_listsPending() {
        Group group = Group.builder().id(20L).build();
        Document pending = Document.builder().id(1L).user(owner(2L))
                .group(group).title("P")
                .moderationStatus(ModerationStatus.PENDING).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(GroupMember.builder().role(GroupMemberRole.OWNER).build()));
        when(documentRepository.findByGroupIdAndModerationStatusOrderByCreatedAtDesc(
                20L, ModerationStatus.PENDING)).thenReturn(List.of(pending));

        List<DocumentResponse> out = service.getPendingGroupDocuments(20L, 1L);

        assertThat(out).hasSize(1);
    }

    @Test
    void getPendingGroupDocuments_nonAdmin_throws() {
        Group group = Group.builder().id(20L).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPendingGroupDocuments(20L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MODERATION_PERMISSION_DENIED);
    }

    // ── saveDocumentToFolder additional branches ────────────────────────────

    @Test
    void saveDocumentToFolder_sourceMissing_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveDocumentToFolder(99L, null, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void saveDocumentToFolder_userMissing_throws() {
        Document source = Document.builder().id(7L).user(owner(99L)).title("T").build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(source));
        when(userService.getUserById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveDocumentToFolder(7L, null, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static User owner(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").fullName("U" + id).build();
    }
}
