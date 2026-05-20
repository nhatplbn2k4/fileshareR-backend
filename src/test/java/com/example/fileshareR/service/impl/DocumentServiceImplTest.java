package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.UpdateDocumentRequest;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.GroupMemberRole;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private UserService userService;
    @Mock private FileStorageService fileStorageService;
    @Mock private TextExtractionService textExtractionService;
    @Mock private NlpService nlpService;
    @Mock private ObjectMapper objectMapper;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupBanRepository groupBanRepository;
    @Mock private GroupFolderRepository groupFolderRepository;
    @Mock private StorageQuotaService storageQuotaService;
    @Mock private ContentModerationService contentModerationService;
    @Mock private NotificationService notificationService;
    @Mock private ObjectProvider<PlagiarismService> plagiarismServiceProvider;

    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(
                documentRepository, folderRepository, userService, fileStorageService,
                textExtractionService, nlpService, objectMapper, groupRepository,
                groupMemberRepository, groupBanRepository, groupFolderRepository,
                storageQuotaService, contentModerationService, notificationService,
                plagiarismServiceProvider);
    }

    // ── getAllDocuments / getDocumentsWithoutFolder ─────────────────────────

    @Test
    void getAllDocuments_returnsMappedList() {
        Document d1 = doc(1L, owner(1L), null, null, "T1");
        Document d2 = doc(2L, owner(1L), null, null, "T2");
        when(documentRepository.findByUserId(1L)).thenReturn(List.of(d1, d2));

        List<DocumentResponse> out = service.getAllDocuments(1L);

        assertThat(out).hasSize(2)
                .extracting(DocumentResponse::getTitle)
                .containsExactly("T1", "T2");
    }

    @Test
    void getDocumentsWithoutFolder_filtersOutDocsInFolder() {
        Folder f = Folder.builder().id(10L).build();
        Document withFolder = doc(1L, owner(1L), f, null, "InFolder");
        Document noFolder = doc(2L, owner(1L), null, null, "NoFolder");
        when(documentRepository.findByUserId(1L)).thenReturn(List.of(withFolder, noFolder));

        List<DocumentResponse> out = service.getDocumentsWithoutFolder(1L);

        assertThat(out).hasSize(1).extracting(DocumentResponse::getTitle).containsExactly("NoFolder");
    }

    // ── getDocumentsByFolder ────────────────────────────────────────────────

    @Test
    void getDocumentsByFolder_happy() {
        User u = owner(1L);
        Folder f = Folder.builder().id(10L).user(u).build();
        when(folderRepository.findById(10L)).thenReturn(Optional.of(f));
        when(documentRepository.findByFolderId(10L)).thenReturn(List.of(doc(1L, u, f, null, "X")));

        List<DocumentResponse> out = service.getDocumentsByFolder(10L, 1L);

        assertThat(out).hasSize(1);
    }

    @Test
    void getDocumentsByFolder_folderNotFound_throws() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocumentsByFolder(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    @Test
    void getDocumentsByFolder_userIsNotOwner_throws() {
        Folder f = Folder.builder().id(10L).user(owner(99L)).build();
        when(folderRepository.findById(10L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.getDocumentsByFolder(10L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void getDocumentsByFolder_nullUser_skipsOwnerCheck() {
        Folder f = Folder.builder().id(10L).user(owner(99L)).build();
        when(folderRepository.findById(10L)).thenReturn(Optional.of(f));
        when(documentRepository.findByFolderId(10L)).thenReturn(List.of());

        // null userId = public access path — bypasses owner check
        List<DocumentResponse> out = service.getDocumentsByFolder(10L, null);

        assertThat(out).isEmpty();
    }

    // ── getDocumentById ─────────────────────────────────────────────────────

    @Test
    void getDocumentById_happy() {
        Document d = doc(7L, owner(1L), null, null, "Z");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        assertThat(service.getDocumentById(7L, 1L).getTitle()).isEqualTo("Z");
    }

    @Test
    void getDocumentById_notFound_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocumentById(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void getDocumentById_notOwner_throwsAccessDenied() {
        Document d = doc(7L, owner(99L), null, null, "Z");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.getDocumentById(7L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    // ── updateDocument ──────────────────────────────────────────────────────

    @Test
    void updateDocument_happy_updatesTitleAndVisibility() {
        Document d = doc(1L, owner(1L), null, null, "Old");
        d.setVisibility(VisibilityType.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDocumentRequest req = new UpdateDocumentRequest();
        req.setTitle("New Title");
        req.setVisibility(VisibilityType.PUBLIC);
        DocumentResponse out = service.updateDocument(1L, req, 1L);

        assertThat(d.getTitle()).isEqualTo("New Title");
        assertThat(d.getVisibility()).isEqualTo(VisibilityType.PUBLIC);
        assertThat(out.getTitle()).isEqualTo("New Title");
    }

    @Test
    void updateDocument_movingToOwnedFolder_setsFolder() {
        Document d = doc(1L, owner(1L), null, null, "T");
        Folder target = Folder.builder().id(50L).user(owner(1L)).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(folderRepository.findById(50L)).thenReturn(Optional.of(target));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDocumentRequest req = new UpdateDocumentRequest();
        req.setTitle("Same");
        req.setFolderId(50L);
        service.updateDocument(1L, req, 1L);

        assertThat(d.getFolder()).isEqualTo(target);
    }

    @Test
    void updateDocument_movingToOtherUserFolder_throws() {
        Document d = doc(1L, owner(1L), null, null, "T");
        Folder hostile = Folder.builder().id(50L).user(owner(99L)).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(folderRepository.findById(50L)).thenReturn(Optional.of(hostile));

        UpdateDocumentRequest req = new UpdateDocumentRequest();
        req.setTitle("Same");
        req.setFolderId(50L);

        assertThatThrownBy(() -> service.updateDocument(1L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void updateDocument_clearsFolderWhenFolderIdNull() {
        Folder f = Folder.builder().id(10L).user(owner(1L)).build();
        Document d = doc(1L, owner(1L), f, null, "T");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDocumentRequest req = new UpdateDocumentRequest();
        req.setTitle("T");
        // folderId null → service nulls the folder
        service.updateDocument(1L, req, 1L);

        assertThat(d.getFolder()).isNull();
    }

    @Test
    void updateDocument_notOwner_throws() {
        Document d = doc(1L, owner(99L), null, null, "T");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        UpdateDocumentRequest req = new UpdateDocumentRequest();
        req.setTitle("X");

        assertThatThrownBy(() -> service.updateDocument(1L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
    }

    // ── deleteDocument ──────────────────────────────────────────────────────

    @Test
    void deleteDocument_personal_decrementsUserQuota() {
        User u = owner(1L);
        Document d = doc(7L, u, null, null, "T");
        d.setFileSize(500L);
        d.setFileUrl("path/to/file");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        service.deleteDocument(7L, 1L);

        verify(fileStorageService).deleteFile("path/to/file");
        verify(documentRepository).delete(d);
        verify(storageQuotaService).decrementUserUsage(u, 500L);
        verify(storageQuotaService, never()).decrementGroupUsage(any(), anyLongVal());
    }

    @Test
    void deleteDocument_groupOwned_decrementsGroupQuota() {
        User u = owner(1L);
        Group g = Group.builder().id(20L).build();
        Document d = doc(7L, u, null, g, "T");
        d.setFileSize(300L);
        d.setFileUrl("group/file");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        service.deleteDocument(7L, 1L);

        verify(storageQuotaService).decrementGroupUsage(g, 300L);
        verify(storageQuotaService, never()).decrementUserUsage(any(), anyLongVal());
    }

    @Test
    void deleteDocument_notOwner_throws() {
        Document d = doc(7L, owner(99L), null, null, "T");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.deleteDocument(7L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_ACCESS_DENIED);
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void deleteDocument_notFound_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDocument(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    // ── adminDeleteDocument ─────────────────────────────────────────────────

    @Test
    void adminDeleteDocument_swallowsStorageFailure() {
        User u = owner(1L);
        Document d = doc(7L, u, null, null, "T");
        d.setFileSize(100L);
        d.setFileUrl("p");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        org.mockito.Mockito.doThrow(new RuntimeException("storage offline"))
                .when(fileStorageService).deleteFile("p");

        service.adminDeleteDocument(7L);

        verify(documentRepository).delete(d);
        verify(storageQuotaService).decrementUserUsage(u, 100L);
    }

    @Test
    void adminDeleteDocument_notFound_throws() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminDeleteDocument(99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    // ── searchDocuments / getPublicDocumentsByUser ──────────────────────────

    @Test
    void searchDocuments_delegatesToRepository() {
        when(documentRepository.searchWithRelevance(1L, "DATN"))
                .thenReturn(List.of(doc(1L, owner(1L), null, null, "H")));

        assertThat(service.searchDocuments("DATN", 1L)).hasSize(1);
    }

    @Test
    void getPublicDocumentsByUser_filtersByVisibility() {
        when(documentRepository.findByUserIdAndVisibility(1L, VisibilityType.PUBLIC))
                .thenReturn(List.of(doc(1L, owner(1L), null, null, "P")));

        assertThat(service.getPublicDocumentsByUser(1L)).hasSize(1);
    }

    // ── moderation: count / approve / reject ────────────────────────────────

    @Test
    void countPendingGroupDocuments_groupMissing_throws() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.countPendingGroupDocuments(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void countPendingGroupDocuments_notAdmin_throws() {
        Group g = Group.builder().id(20L).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.countPendingGroupDocuments(20L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MODERATION_PERMISSION_DENIED);
    }

    @Test
    void countPendingGroupDocuments_admin_returnsCount() {
        Group g = Group.builder().id(20L).build();
        GroupMember m = GroupMember.builder().role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 1L)).thenReturn(Optional.of(m));
        when(documentRepository.countByGroupIdAndModerationStatus(20L, ModerationStatus.PENDING))
                .thenReturn(7L);

        assertThat(service.countPendingGroupDocuments(20L, 1L)).isEqualTo(7L);
    }

    @Test
    void approveDocument_setsApprovedStatus() {
        User reviewer = owner(99L);
        Group g = Group.builder().id(20L).build();
        Document d = Document.builder().id(7L).user(owner(1L)).group(g)
                .moderationStatus(ModerationStatus.PENDING)
                .title("T").build();
        GroupMember owner = GroupMember.builder().role(GroupMemberRole.OWNER).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 99L)).thenReturn(Optional.of(owner));
        when(userService.getUserById(99L)).thenReturn(Optional.of(reviewer));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentResponse out = service.approveDocument(7L, 99L);

        assertThat(d.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(d.getModeratedBy()).isEqualTo(reviewer);
        assertThat(out.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    void approveDocument_alreadyApproved_throwsNotPending() {
        Group g = Group.builder().id(20L).build();
        Document d = Document.builder().id(7L).user(owner(1L)).group(g)
                .moderationStatus(ModerationStatus.APPROVED).title("T").build();
        GroupMember adm = GroupMember.builder().role(GroupMemberRole.ADMIN).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 99L)).thenReturn(Optional.of(adm));

        assertThatThrownBy(() -> service.approveDocument(7L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MODERATION_NOT_PENDING);
    }

    @Test
    void approveDocument_notGroupDoc_throws() {
        Document d = Document.builder().id(7L).user(owner(1L)).group(null)
                .moderationStatus(ModerationStatus.PENDING).title("T").build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.approveDocument(7L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MODERATION_NOT_GROUP_DOCUMENT);
    }

    @Test
    void rejectDocument_setsReasonAndStatus() {
        User reviewer = owner(99L);
        Group g = Group.builder().id(20L).build();
        Document d = Document.builder().id(7L).user(owner(1L)).group(g)
                .moderationStatus(ModerationStatus.PENDING).title("T").build();
        GroupMember adm = GroupMember.builder().role(GroupMemberRole.ADMIN).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 99L)).thenReturn(Optional.of(adm));
        when(userService.getUserById(99L)).thenReturn(Optional.of(reviewer));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rejectDocument(7L, "vi phạm", 99L);

        assertThat(d.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(d.getModerationReason()).isEqualTo("vi phạm");
    }

    @Test
    void rejectDocument_blankReason_leavesReasonNull() {
        User reviewer = owner(99L);
        Group g = Group.builder().id(20L).build();
        Document d = Document.builder().id(7L).user(owner(1L)).group(g)
                .moderationStatus(ModerationStatus.PENDING).title("T").build();
        GroupMember adm = GroupMember.builder().role(GroupMemberRole.ADMIN).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 99L)).thenReturn(Optional.of(adm));
        when(userService.getUserById(99L)).thenReturn(Optional.of(reviewer));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rejectDocument(7L, "  ", 99L);

        assertThat(d.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(d.getModerationReason()).isNull();
    }

    @Test
    void rejectDocument_alreadyRejected_throws() {
        Group g = Group.builder().id(20L).build();
        Document d = Document.builder().id(7L).user(owner(1L)).group(g)
                .moderationStatus(ModerationStatus.REJECTED).title("T").build();
        GroupMember adm = GroupMember.builder().role(GroupMemberRole.ADMIN).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(d));
        when(groupMemberRepository.findByGroupIdAndUserId(20L, 99L)).thenReturn(Optional.of(adm));

        assertThatThrownBy(() -> service.rejectDocument(7L, "x", 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MODERATION_NOT_PENDING);
    }

    // ── saveDocumentToFolder ────────────────────────────────────────────────

    @Test
    void saveDocumentToFolder_targetFolderNotOwned_throws() {
        User actor = owner(1L);
        Document source = doc(7L, owner(99L), null, null, "T");
        Folder targetFolder = Folder.builder().id(50L).user(owner(99L)).build();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(source));
        when(userService.getUserById(1L)).thenReturn(Optional.of(actor));
        when(folderRepository.findById(50L)).thenReturn(Optional.of(targetFolder));

        assertThatThrownBy(() -> service.saveDocumentToFolder(7L, 50L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void saveDocumentToFolder_happy_copiesAndChargesQuota() {
        User actor = owner(1L);
        Document source = doc(7L, owner(99L), null, null, "Original");
        source.setFileSize(500L);
        source.setFileName("file.pdf");
        source.setFileUrl("u/v");
        source.setFileType(com.example.fileshareR.enums.FileType.PDF);
        source.setDownloadCount(3);
        when(documentRepository.findById(7L)).thenReturn(Optional.of(source));
        when(userService.getUserById(1L)).thenReturn(Optional.of(actor));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentResponse out = service.saveDocumentToFolder(7L, null, 1L);

        assertThat(out.getTitle()).isEqualTo("Original");
        verify(storageQuotaService).ensureUserCanUpload(actor, 500L);
        verify(storageQuotaService).incrementUserUsage(actor, 500L);
        assertThat(source.getDownloadCount()).isEqualTo(4);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static User owner(Long id) {
        return User.builder().id(id).email("u" + id + "@x.com").fullName("U" + id).build();
    }

    private static Document doc(Long id, User u, Folder f, Group g, String title) {
        return Document.builder()
                .id(id).user(u).folder(f).group(g)
                .title(title)
                .visibility(VisibilityType.PRIVATE)
                .downloadCount(0)
                .build();
    }

    /** Helper for any(long) matcher across multiple call-site signatures. */
    private static long anyLongVal() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
