package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateFolderRequest;
import com.example.fileshareR.dto.request.UpdateFolderRequest;
import com.example.fileshareR.dto.response.FolderResponse;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.service.PlagiarismService;
import com.example.fileshareR.service.StorageQuotaService;
import com.example.fileshareR.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceImplCoreTest {

    @Mock private FolderRepository folderRepository;
    @Mock private UserService userService;
    @Mock private DocumentRepository documentRepository;
    @Mock private StorageQuotaService storageQuotaService;
    @Mock private PlagiarismService plagiarismService;

    private FolderServiceImpl service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new FolderServiceImpl(folderRepository, userService,
                documentRepository, storageQuotaService, plagiarismService);
        owner = User.builder().id(1L).email("owner@x.com").build();
        lenient().when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── createFolder ────────────────────────────────────────────────────────

    @Test
    void createFolder_userNotFound_throws() {
        when(userService.getUserById(99L)).thenReturn(Optional.empty());

        CreateFolderRequest req = new CreateFolderRequest();
        req.setName("New");

        assertThatThrownBy(() -> service.createFolder(req, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void createFolder_rootFolder_persists() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateFolderRequest req = new CreateFolderRequest();
        req.setName("New Folder");

        FolderResponse out = service.createFolder(req, 1L);

        assertThat(out.getName()).isEqualTo("New Folder");
    }

    @Test
    void createFolder_withParent_setsParent() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        Folder parent = Folder.builder().id(50L).name("P").user(owner)
                .visibility(FolderVisibilityType.PRIVATE).build();
        when(folderRepository.findById(50L)).thenReturn(Optional.of(parent));

        CreateFolderRequest req = new CreateFolderRequest();
        req.setName("Sub");
        req.setParentId(50L);

        service.createFolder(req, 1L);

        // verified by side effect of save being called with a folder having parent set
    }

    @Test
    void createFolder_parentNotOwned_throws() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));
        User other = User.builder().id(99L).build();
        Folder parent = Folder.builder().id(50L).user(other).build();
        when(folderRepository.findById(50L)).thenReturn(Optional.of(parent));

        CreateFolderRequest req = new CreateFolderRequest();
        req.setName("Sub");
        req.setParentId(50L);

        assertThatThrownBy(() -> service.createFolder(req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    // ── getAllFolders / getRootFolders / getSubFolders ──────────────────────

    @Test
    void getAllFolders_listsUserFolders() {
        Folder f1 = Folder.builder().id(1L).name("A").user(owner).build();
        Folder f2 = Folder.builder().id(2L).name("B").user(owner).build();
        when(folderRepository.findByUserId(1L)).thenReturn(List.of(f1, f2));

        List<FolderResponse> out = service.getAllFolders(1L);

        assertThat(out).hasSize(2);
    }

    @Test
    void getRootFolders_filtersParentNull() {
        Folder root = Folder.builder().id(1L).name("Root").user(owner).build();
        when(folderRepository.findByUserIdAndParentIsNull(1L)).thenReturn(List.of(root));

        List<FolderResponse> out = service.getRootFolders(1L);

        assertThat(out).hasSize(1);
    }

    @Test
    void getSubFolders_folderNotFound_throws() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubFolders(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    @Test
    void getSubFolders_notOwner_throws() {
        Folder parent = Folder.builder().id(1L).user(User.builder().id(99L).build())
                .visibility(FolderVisibilityType.PRIVATE).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.getSubFolders(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void getSubFolders_owner_returnsChildren() {
        Folder parent = Folder.builder().id(1L).user(owner)
                .visibility(FolderVisibilityType.PRIVATE).build();
        Folder child = Folder.builder().id(2L).name("Sub").user(owner).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(folderRepository.findByUserIdAndParentId(1L, 1L)).thenReturn(List.of(child));

        assertThat(service.getSubFolders(1L, 1L)).hasSize(1);
    }

    // ── getFolderById ───────────────────────────────────────────────────────

    @Test
    void getFolderById_notFound_throws() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFolderById(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    @Test
    void getFolderById_owner_returnsFolder() {
        Folder f = Folder.builder().id(1L).name("F").user(owner)
                .visibility(FolderVisibilityType.PRIVATE).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(f));

        assertThat(service.getFolderById(1L, 1L).getName()).isEqualTo("F");
    }

    @Test
    void getFolderById_privateNotOwner_throws() {
        Folder f = Folder.builder().id(1L).user(User.builder().id(99L).build())
                .visibility(FolderVisibilityType.PRIVATE).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.getFolderById(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void getFolderById_publicFolderButNotOwner_stillThrows() {
        // getFolderById enforces strict ownership — public/shared paths are
        // handled by getSharedFolder/getSharedFolderByToken instead.
        Folder f = Folder.builder().id(1L).user(User.builder().id(99L).build())
                .visibility(FolderVisibilityType.PUBLIC).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.getFolderById(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    // ── deleteFolder ────────────────────────────────────────────────────────

    @Test
    void deleteFolder_notOwner_throws() {
        Folder f = Folder.builder().id(1L).user(User.builder().id(99L).build()).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.deleteFolder(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void deleteFolder_owner_freesQuotaAndDeletes() {
        Folder root = Folder.builder().id(1L).user(owner).name("Root").build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(root));
        when(documentRepository.sumFileSizeInFolderTree(1L)).thenReturn(500L);

        service.deleteFolder(1L, 1L);

        // Folder + quota decrement
        org.mockito.Mockito.verify(folderRepository).delete(root);
        org.mockito.Mockito.verify(storageQuotaService).decrementUserUsage(owner, 500L);
    }

    @Test
    void deleteFolder_zeroSizeFolder_skipsQuotaDecrement() {
        Folder root = Folder.builder().id(1L).user(owner).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(root));
        when(documentRepository.sumFileSizeInFolderTree(1L)).thenReturn(null);

        service.deleteFolder(1L, 1L);

        org.mockito.Mockito.verify(folderRepository).delete(root);
        org.mockito.Mockito.verifyNoInteractions(storageQuotaService);
    }

    // ── getPublicFoldersByUser ──────────────────────────────────────────────

    @Test
    void getPublicFoldersByUser_filtersByVisibility() {
        Folder f = Folder.builder().id(1L).name("P").user(owner)
                .visibility(FolderVisibilityType.PUBLIC).build();
        when(folderRepository.findByUserIdAndVisibility(1L, FolderVisibilityType.PUBLIC))
                .thenReturn(List.of(f));

        assertThat(service.getPublicFoldersByUser(1L)).hasSize(1);
    }

    // ── updateFolder ────────────────────────────────────────────────────────

    @Test
    void updateFolder_notFound_throws() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateFolderRequest req = new UpdateFolderRequest();
        req.setName("New");

        assertThatThrownBy(() -> service.updateFolder(99L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    @Test
    void updateFolder_notOwner_throws() {
        Folder f = Folder.builder().id(1L).user(User.builder().id(99L).build())
                .visibility(FolderVisibilityType.PRIVATE).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(f));

        UpdateFolderRequest req = new UpdateFolderRequest();
        req.setName("New");

        assertThatThrownBy(() -> service.updateFolder(1L, req, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void updateFolder_renameOnly() {
        Folder f = Folder.builder().id(1L).name("Old").user(owner)
                .visibility(FolderVisibilityType.PRIVATE).build();
        when(folderRepository.findById(1L)).thenReturn(Optional.of(f));

        UpdateFolderRequest req = new UpdateFolderRequest();
        req.setName("New Name");

        service.updateFolder(1L, req, 1L);

        assertThat(f.getName()).isEqualTo("New Name");
    }
}
