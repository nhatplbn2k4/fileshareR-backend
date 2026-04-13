package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateFolderRequest;
import com.example.fileshareR.dto.request.UpdateFolderRequest;
import com.example.fileshareR.dto.response.FolderResponse;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceImplShareTest {

    @Mock private FolderRepository folderRepository;
    @Mock private UserService userService;

    @InjectMocks private FolderServiceImpl folderService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        // mock save trả về chính entity truyền vào (id giả lập)
        lenient().when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
        // mặc định không có folder con
        lenient().when(folderRepository.findByParentId(anyLong())).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", Collections.emptyList()));
    }

    private Folder folder(long id, FolderVisibilityType visibility, String token) {
        Folder f = Folder.builder()
                .id(id)
                .name("f" + id)
                .user(owner)
                .visibility(visibility)
                .shareToken(token)
                .build();
        return f;
    }

    // ─── createFolder ────────────────────────────────────────────────────────

    @Test
    void createFolder_publicVisibility_generatesShareToken() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateFolderRequest req = CreateFolderRequest.builder()
                .name("docs")
                .visibility(FolderVisibilityType.PUBLIC)
                .build();

        FolderResponse res = folderService.createFolder(req, 1L);

        assertThat(res.getVisibility()).isEqualTo(FolderVisibilityType.PUBLIC);
        assertThat(res.getShareToken()).isNotBlank();
    }

    @Test
    void createFolder_privateVisibility_noShareToken() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(owner));

        CreateFolderRequest req = CreateFolderRequest.builder()
                .name("secret")
                .visibility(FolderVisibilityType.PRIVATE)
                .build();

        FolderResponse res = folderService.createFolder(req, 1L);

        assertThat(res.getVisibility()).isEqualTo(FolderVisibilityType.PRIVATE);
        assertThat(res.getShareToken()).isNull();
    }

    // ─── getSharedFolderByToken ──────────────────────────────────────────────

    @Test
    void getSharedFolderByToken_public_okWithoutLogin() {
        Folder f = folder(10L, FolderVisibilityType.PUBLIC, "tok-public");
        when(folderRepository.findByShareToken("tok-public")).thenReturn(Optional.of(f));

        FolderResponse res = folderService.getSharedFolderByToken("tok-public");

        assertThat(res.getId()).isEqualTo(10L);
        assertThat(res.getShareToken()).isEqualTo("tok-public");
    }

    @Test
    void getSharedFolderByToken_linkOnly_withoutLogin_throwsLoginRequired() {
        Folder f = folder(11L, FolderVisibilityType.LINK_ONLY, "tok-link");
        when(folderRepository.findByShareToken("tok-link")).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> folderService.getSharedFolderByToken("tok-link"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHARE_LOGIN_REQUIRED);
    }

    @Test
    void getSharedFolderByToken_linkOnly_anonymousAuth_throwsLoginRequired() {
        // Mô phỏng request đi qua endpoint permitAll() không kèm JWT — Spring set AnonymousAuthenticationToken
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        Folder f = folder(13L, FolderVisibilityType.LINK_ONLY, "tok-anon");
        when(folderRepository.findByShareToken("tok-anon")).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> folderService.getSharedFolderByToken("tok-anon"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHARE_LOGIN_REQUIRED);
    }

    @Test
    void getSharedFolderByToken_linkOnly_withLogin_ok() {
        loginAs("anyuser@example.com");
        Folder f = folder(12L, FolderVisibilityType.LINK_ONLY, "tok-link2");
        when(folderRepository.findByShareToken("tok-link2")).thenReturn(Optional.of(f));

        FolderResponse res = folderService.getSharedFolderByToken("tok-link2");

        assertThat(res.getId()).isEqualTo(12L);
    }

    @Test
    void getSharedFolderByToken_tokenNotFound_throwsNotFound() {
        when(folderRepository.findByShareToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderService.getSharedFolderByToken("ghost"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_NOT_FOUND);
    }

    // ─── rotateShareToken ────────────────────────────────────────────────────

    @Test
    void rotateShareToken_owner_generatesNewToken() {
        Folder f = folder(20L, FolderVisibilityType.PUBLIC, "old-token");
        when(folderRepository.findById(20L)).thenReturn(Optional.of(f));

        FolderResponse res = folderService.rotateShareToken(20L, 1L);

        assertThat(res.getShareToken()).isNotBlank().isNotEqualTo("old-token");
    }

    @Test
    void rotateShareToken_notOwner_throwsAccessDenied() {
        Folder f = folder(21L, FolderVisibilityType.PUBLIC, "tok");
        when(folderRepository.findById(21L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> folderService.rotateShareToken(21L, 999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void rotateShareToken_privateFolder_throwsBadRequest() {
        Folder f = folder(22L, FolderVisibilityType.PRIVATE, null);
        when(folderRepository.findById(22L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> folderService.rotateShareToken(22L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // ─── updateFolder cascade ────────────────────────────────────────────────

    @Test
    void updateFolder_changeToPublic_cascadesTokenToChildren() {
        Folder root = folder(30L, FolderVisibilityType.PRIVATE, null);
        Folder child1 = folder(31L, FolderVisibilityType.PRIVATE, null);
        Folder child2 = folder(32L, FolderVisibilityType.PRIVATE, null);

        when(folderRepository.findById(30L)).thenReturn(Optional.of(root));
        when(folderRepository.findByParentId(30L)).thenReturn(List.of(child1));
        when(folderRepository.findByParentId(31L)).thenReturn(List.of(child2));
        when(folderRepository.findByParentId(32L)).thenReturn(Collections.emptyList());

        UpdateFolderRequest req = UpdateFolderRequest.builder()
                .name("renamed")
                .visibility(FolderVisibilityType.PUBLIC)
                .build();

        folderService.updateFolder(30L, req, 1L);

        assertThat(root.getVisibility()).isEqualTo(FolderVisibilityType.PUBLIC);
        assertThat(root.getShareToken()).isNotBlank();
        assertThat(child1.getVisibility()).isEqualTo(FolderVisibilityType.PUBLIC);
        assertThat(child1.getShareToken()).isNotBlank();
        assertThat(child2.getVisibility()).isEqualTo(FolderVisibilityType.PUBLIC);
        assertThat(child2.getShareToken()).isNotBlank();
    }

    @Test
    void updateFolder_changeToPrivate_clearsTokensCascade() {
        Folder root = folder(40L, FolderVisibilityType.PUBLIC, "root-tok");
        Folder child = folder(41L, FolderVisibilityType.PUBLIC, "child-tok");

        when(folderRepository.findById(40L)).thenReturn(Optional.of(root));
        when(folderRepository.findByParentId(40L)).thenReturn(List.of(child));
        when(folderRepository.findByParentId(41L)).thenReturn(Collections.emptyList());

        UpdateFolderRequest req = UpdateFolderRequest.builder()
                .name("root")
                .visibility(FolderVisibilityType.PRIVATE)
                .build();

        folderService.updateFolder(40L, req, 1L);

        assertThat(root.getVisibility()).isEqualTo(FolderVisibilityType.PRIVATE);
        assertThat(root.getShareToken()).isNull();
        assertThat(child.getVisibility()).isEqualTo(FolderVisibilityType.PRIVATE);
        assertThat(child.getShareToken()).isNull();
    }
}
