package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateFolderRequest;
import com.example.fileshareR.dto.request.UpdateFolderRequest;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.dto.response.FolderResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.FolderService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final UserService userService;
    private final DocumentService documentService;

    /**
     * Tạo thư mục mới
     */
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        Long userId = getCurrentUserId();
        FolderResponse response = folderService.createFolder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lấy danh sách tất cả thư mục
     */
    @GetMapping
    public ResponseEntity<List<FolderResponse>> getAllFolders() {
        Long userId = getCurrentUserId();
        List<FolderResponse> folders = folderService.getAllFolders(userId);
        return ResponseEntity.ok(folders);
    }

    /**
     * Lấy danh sách thư mục gốc (không có parent)
     */
    @GetMapping("/root")
    public ResponseEntity<List<FolderResponse>> getRootFolders() {
        Long userId = getCurrentUserId();
        List<FolderResponse> folders = folderService.getRootFolders(userId);
        return ResponseEntity.ok(folders);
    }

    /**
     * Lấy danh sách thư mục con của một thư mục
     */
    @GetMapping("/{folderId}/subfolders")
    public ResponseEntity<List<FolderResponse>> getSubFolders(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        List<FolderResponse> folders = folderService.getSubFolders(folderId, userId);
        return ResponseEntity.ok(folders);
    }

    /**
     * Lấy thông tin chi tiết một thư mục
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> getFolderById(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        FolderResponse folder = folderService.getFolderById(folderId, userId);
        return ResponseEntity.ok(folder);
    }

    /**
     * Cập nhật thông tin thư mục
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable Long folderId,
            @Valid @RequestBody UpdateFolderRequest request) {
        Long userId = getCurrentUserId();
        FolderResponse folder = folderService.updateFolder(folderId, request, userId);
        return ResponseEntity.ok(folder);
    }

    /**
     * [PUBLIC] Xem thông tin folder chia sẻ (PUBLIC hoặc LINK_ONLY)
     * GET /api/folders/{folderId}/share
     * Không cần đăng nhập
     */
    @GetMapping("/{folderId}/share")
    public ResponseEntity<FolderResponse> getSharedFolder(@PathVariable Long folderId) {
        return ResponseEntity.ok(folderService.getSharedFolder(folderId));
    }

    /**
     * [PUBLIC] Lấy tài liệu trong folder chia sẻ
     * GET /api/folders/{folderId}/share/documents
     * Không cần đăng nhập
     */
    @GetMapping("/{folderId}/share/documents")
    public ResponseEntity<List<com.example.fileshareR.dto.response.DocumentResponse>> getSharedFolderDocuments(
            @PathVariable Long folderId) {
        // Validate quyền truy cập (service đã kiểm tra)
        folderService.getSharedFolder(folderId);
        List<DocumentResponse> docs = documentService.getDocumentsByFolder(folderId, null);
        return ResponseEntity.ok(docs);
    }

    /**
     * [PUBLIC] Lấy sub-folders của folder chia sẻ
     * GET /api/folders/{folderId}/share/subfolders
     */
    @GetMapping("/{folderId}/share/subfolders")
    public ResponseEntity<List<FolderResponse>> getSharedSubFolders(@PathVariable Long folderId) {
        return ResponseEntity.ok(folderService.getSharedSubFolders(folderId));
    }

    /**
     * [SHARE] Xem folder chia sẻ qua share token (URL chính thức)
     * GET /api/folders/shared/{token}
     * - PUBLIC: ai cũng truy cập
     * - LINK_ONLY: yêu cầu đăng nhập (service tự enforce)
     */
    @GetMapping("/shared/{token}")
    public ResponseEntity<FolderResponse> getSharedFolderByToken(@PathVariable String token) {
        return ResponseEntity.ok(folderService.getSharedFolderByToken(token));
    }

    /**
     * [SHARE] Lấy tài liệu trong folder chia sẻ qua token
     * GET /api/folders/shared/{token}/documents
     */
    @GetMapping("/shared/{token}/documents")
    public ResponseEntity<List<DocumentResponse>> getSharedFolderDocumentsByToken(@PathVariable String token) {
        FolderResponse folder = folderService.getSharedFolderByToken(token);
        List<DocumentResponse> docs = documentService.getDocumentsByFolder(folder.getId(), null);
        return ResponseEntity.ok(docs);
    }

    /**
     * [SHARE] Lấy sub-folders của folder chia sẻ qua token
     * GET /api/folders/shared/{token}/subfolders
     */
    @GetMapping("/shared/{token}/subfolders")
    public ResponseEntity<List<FolderResponse>> getSharedSubFoldersByToken(@PathVariable String token) {
        return ResponseEntity.ok(folderService.getSharedSubFoldersByToken(token));
    }

    /**
     * [OWNER] Sinh share token mới (revoke link cũ).
     * POST /api/folders/{folderId}/share/rotate
     */
    @PostMapping("/{folderId}/share/rotate")
    public ResponseEntity<FolderResponse> rotateShareToken(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(folderService.rotateShareToken(folderId, userId));
    }

    /**
     * Xóa thư mục
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<String> deleteFolder(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        folderService.deleteFolder(folderId, userId);
        return ResponseEntity.ok("Xóa thư mục thành công");
    }

    /**
     * Lấy userId của user đang đăng nhập
     */
    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));

        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return user.getId();
    }
}
