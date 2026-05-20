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
                documentRepository, folderRepository, userService, fileStorageService,
                textExtractionService, nlpService, realObjectMapper, groupRepository,
                groupMemberRepository, groupBanRepository, groupFolderRepository,
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
    void findSimilarDocuments_notOwner_throws() {
        Document d = Document.builder().id(1L).user(owner(99L)).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.findSimilarDocuments(1L, 1L, 5))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    @Test
    void findSimilarDocuments_noTfidfVector_returnsEmpty() {
        Document d = Document.builder().id(1L).user(owner(1L)).title("T").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThat(service.findSimilarDocuments(1L, 1L, 5)).isEmpty();
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

    // ── helpers ─────────────────────────────────────────────────────────────

    private static User owner(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").fullName("U" + id).build();
    }
}
