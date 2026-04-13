package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.CreateDocumentRequest;
import com.example.fileshareR.dto.request.UpdateDocumentRequest;
import com.example.fileshareR.dto.request.UploadGroupDocumentRequest;
import com.example.fileshareR.dto.response.DocumentAnalysisResponse;
import com.example.fileshareR.dto.response.DocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    /** Upload và tạo document mới (cá nhân) */
    DocumentResponse uploadDocument(MultipartFile file, CreateDocumentRequest request, Long userId);

    /** Lấy danh sách tất cả document của user */
    List<DocumentResponse> getAllDocuments(Long userId);

    /** Lấy danh sách document trong một folder */
    List<DocumentResponse> getDocumentsByFolder(Long folderId, Long userId);

    /** Lấy danh sách document không thuộc folder nào */
    List<DocumentResponse> getDocumentsWithoutFolder(Long userId);

    /** Lấy chi tiết một document */
    DocumentResponse getDocumentById(Long documentId, Long userId);

    /** Cập nhật thông tin document */
    DocumentResponse updateDocument(Long documentId, UpdateDocumentRequest request, Long userId);

    /** Xóa document */
    void deleteDocument(Long documentId, Long userId);

    /** Download document (cá nhân) */
    Resource downloadDocument(Long documentId, Long userId);

    /** Tìm kiếm document theo keyword */
    List<DocumentResponse> searchDocuments(String keyword, Long userId);

    /** Phân tích document: trả về summary, keywords và TF-IDF insights */
    DocumentAnalysisResponse analyzeDocument(Long documentId, Long userId);

    /** Tái xử lý NLP cho document */
    DocumentResponse reprocessDocumentNlp(Long documentId, Long userId);

    /** Tìm các document tương tự dựa trên TF-IDF */
    List<DocumentResponse> findSimilarDocuments(Long documentId, Long userId, int limit);

    // ── Group document methods ─────────────────────────────────────────────────

    /** Upload tài liệu vào nhóm (kiểm tra membership + ban) */
    DocumentResponse uploadGroupDocument(MultipartFile file, UploadGroupDocumentRequest request,
            Long groupId, Long userId);

    /**
     * Lấy toàn bộ tài liệu của nhóm (kiểm tra visibility). folderId=null → tất cả,
     * folderId=-1 → không thuộc folder nào
     */
    List<DocumentResponse> getGroupDocuments(Long groupId, Long folderId, Long requesterId);

    /** Download tài liệu nhóm (kiểm tra visibility) */
    Resource downloadGroupDocument(Long documentId, Long groupId, Long requesterId);

    /** Xóa tài liệu nhóm (owner doc hoặc ADMIN/OWNER nhóm) */
    void deleteGroupDocument(Long documentId, Long groupId, Long userId);

    /** Lấy danh sách tài liệu PUBLIC của một user (cho profile page) */
    List<DocumentResponse> getPublicDocumentsByUser(Long userId);

    /** Lưu bản sao tài liệu vào folder của user hiện tại */
    DocumentResponse saveDocumentToFolder(Long documentId, Long targetFolderId, Long userId);
}
