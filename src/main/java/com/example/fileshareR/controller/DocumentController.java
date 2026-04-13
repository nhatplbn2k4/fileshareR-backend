package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateDocumentRequest;
import com.example.fileshareR.dto.request.UpdateDocumentRequest;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.dto.response.DocumentAnalysisResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Upload document mới
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") String visibility) {

        Long userId = getCurrentUserId();

        // Tạo request DTO
        CreateDocumentRequest request = CreateDocumentRequest.builder()
                .title(title)
                .folderId(folderId)
                .visibility(com.example.fileshareR.enums.VisibilityType.valueOf(visibility.toUpperCase()))
                .build();

        DocumentResponse response = documentService.uploadDocument(file, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lấy tất cả document của user
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        Long userId = getCurrentUserId();
        List<DocumentResponse> documents = documentService.getAllDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Lấy document theo folder
     */
    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<DocumentResponse>> getDocumentsByFolder(@PathVariable Long folderId) {
        Long userId = getCurrentUserId();
        List<DocumentResponse> documents = documentService.getDocumentsByFolder(folderId, userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Lấy document không thuộc folder nào
     */
    @GetMapping("/no-folder")
    public ResponseEntity<List<DocumentResponse>> getDocumentsWithoutFolder() {
        Long userId = getCurrentUserId();
        List<DocumentResponse> documents = documentService.getDocumentsWithoutFolder(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Lấy chi tiết document
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        DocumentResponse document = documentService.getDocumentById(documentId, userId);
        return ResponseEntity.ok(document);
    }

    /**
     * Cập nhật document
     */
    @PutMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateDocumentRequest request) {
        Long userId = getCurrentUserId();
        DocumentResponse document = documentService.updateDocument(documentId, request, userId);
        return ResponseEntity.ok(document);
    }

    /**
     * Xóa document
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<String> deleteDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        documentService.deleteDocument(documentId, userId);
        return ResponseEntity.ok("Xóa tài liệu thành công");
    }

    /**
     * Download document
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        Resource resource = documentService.downloadDocument(documentId, userId);

        // Lấy filename từ resource
        String filename = resource.getFilename();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    /**
     * Tìm kiếm document
     */
    @GetMapping("/search")
    public ResponseEntity<List<DocumentResponse>> searchDocuments(@RequestParam String keyword) {
        Long userId = getCurrentUserId();
        List<DocumentResponse> documents = documentService.searchDocuments(keyword, userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * [PUBLIC] Download tài liệu PUBLIC (không cần auth).
     * GET /api/documents/{documentId}/public-download
     */
    @GetMapping("/{documentId}/public-download")
    public ResponseEntity<Resource> publicDownloadDocument(@PathVariable Long documentId) {
        // Truyền userId=null — service sẽ cho phép nếu document PUBLIC hoặc folder PUBLIC
        Resource resource = documentService.downloadDocument(documentId, null);
        String filename = resource.getFilename();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    /**
     * Lưu bản sao tài liệu vào folder của mình.
     * POST /api/documents/{documentId}/save-to-folder?folderId=xxx
     */
    @PostMapping("/{documentId}/save-to-folder")
    public ResponseEntity<DocumentResponse> saveToFolder(
            @PathVariable Long documentId,
            @RequestParam(required = false) Long folderId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(documentService.saveDocumentToFolder(documentId, folderId, userId));
    }

    /**
     * Phân tích document - trả về summary, keywords và thống kê
     */
    @GetMapping("/{documentId}/analyze")
    public ResponseEntity<DocumentAnalysisResponse> analyzeDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        DocumentAnalysisResponse analysis = documentService.analyzeDocument(documentId, userId);
        return ResponseEntity.ok(analysis);
    }

    /**
     * Tái xử lý NLP cho document
     */
    @PostMapping("/{documentId}/reprocess-nlp")
    public ResponseEntity<DocumentResponse> reprocessDocumentNlp(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        DocumentResponse document = documentService.reprocessDocumentNlp(documentId, userId);
        return ResponseEntity.ok(document);
    }

    /**
     * Tìm các document tương tự
     */
    @GetMapping("/{documentId}/similar")
    public ResponseEntity<List<DocumentResponse>> findSimilarDocuments(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "5") int limit) {
        Long userId = getCurrentUserId();
        List<DocumentResponse> documents = documentService.findSimilarDocuments(documentId, userId, limit);
        return ResponseEntity.ok(documents);
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
