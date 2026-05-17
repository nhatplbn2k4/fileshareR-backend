package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.CreateDocumentRequest;
import com.example.fileshareR.dto.request.UpdateDocumentRequest;
import com.example.fileshareR.dto.request.UploadGroupDocumentRequest;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.dto.response.DocumentAnalysisResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupFolder;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.BanType;
import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.enums.NotificationType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.repository.GroupBanRepository;
import com.example.fileshareR.repository.GroupFolderRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.service.ContentModerationService;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.FileStorageService;
import com.example.fileshareR.service.NlpService;
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.StorageQuotaService;
import com.example.fileshareR.service.TextExtractionService;
import com.example.fileshareR.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final NlpService nlpService;
    private final ObjectMapper objectMapper;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupBanRepository groupBanRepository;
    private final GroupFolderRepository groupFolderRepository;
    private final StorageQuotaService storageQuotaService;
    private final ContentModerationService contentModerationService;
    private final NotificationService notificationService;

    private static final int TOP_KEYWORDS_COUNT = 10;
    private static final int SUMMARY_MAX_WORDS = 50; // Tối đa 50 từ cho summary ngắn gọn

    @Override
    public DocumentResponse uploadDocument(MultipartFile file, CreateDocumentRequest request, Long userId) {
        log.info("Uploading document {} for user {}", request.getTitle(), userId);

        // Validate user
        User user = userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Validate folder if provided
        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

            // Kiểm tra quyền sở hữu folder
            if (!folder.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
            }
        }

        // Kiểm tra quota của user trước khi upload
        storageQuotaService.ensureUserCanUpload(user, file.getSize());

        // Lưu file vào hệ thống
        String fileUrl = fileStorageService.storeFile(file, userId);

        // Lấy thông tin file
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        FileType fileType = FileType.valueOf(fileExtension.toUpperCase());
        Long fileSize = file.getSize();

        // Trích xuất text từ file
        Path filePath = fileStorageService.getFilePath(fileUrl);
        String extractedText = textExtractionService.extractText(filePath, fileExtension);

        // Áp dụng NLP: trích xuất keywords, tạo summary với AI, tính TF-IDF vector
        String keywords = null;
        String summary = null;
        String tfidfVector = null;

        if (extractedText != null && !extractedText.isBlank()) {
            try {
                // Trích xuất từ khóa bằng AI (fallback to TF-IDF nếu thất bại)
                List<String> keywordsList = nlpService.extractKeywordsWithAI(extractedText, TOP_KEYWORDS_COUNT);
                keywords = objectMapper.writeValueAsString(keywordsList);
                log.info("Extracted {} keywords for document", keywordsList.size());

                // Tạo summary bằng AI (fallback to extractive nếu thất bại)
                summary = nlpService.generateSummaryWithAI(extractedText, SUMMARY_MAX_WORDS);
                log.info("Generated AI summary of {} characters", summary.length());

                // Tính TF-IDF vector (vẫn dùng TF-IDF cho similarity search)
                Map<String, Double> tfidf = nlpService.calculateTfIdf(extractedText);
                // Chỉ lưu top 50 terms để tiết kiệm dung lượng
                Map<String, Double> topTfidf = tfidf.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(50)
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue,
                                (e1, e2) -> e1, java.util.LinkedHashMap::new));
                tfidfVector = objectMapper.writeValueAsString(topTfidf);
                log.info("Calculated TF-IDF vector with {} terms", topTfidf.size());
            } catch (Exception e) {
                log.warn("Failed to process NLP for document: {}", e.getMessage());
            }
        }

        // Tạo document entity
        Document document = Document.builder()
                .title(request.getTitle())
                .fileName(originalFilename)
                .fileType(fileType)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .visibility(request.getVisibility())
                .extractedText(extractedText)
                .keywords(keywords)
                .summary(summary)
                .tfidfVector(tfidfVector)
                .user(user)
                .folder(folder)
                .downloadCount(0)
                .build();

        document = documentRepository.save(document);
        storageQuotaService.incrementUserUsage(user, fileSize);
        log.info("Document uploaded successfully with id: {}", document.getId());

        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments(Long userId) {
        log.info("Getting all documents for user {}", userId);

        List<Document> documents = documentRepository.findByUserId(userId);
        return documents.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByFolder(Long folderId, Long userId) {
        log.info("Getting documents in folder {} for user {}", folderId, userId);

        // Kiểm tra quyền truy cập folder (nếu userId != null = private access, cần là
        // owner)
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

        if (userId != null && !folder.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
        }

        List<Document> documents = documentRepository.findByFolderId(folderId);
        return documents.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsWithoutFolder(Long userId) {
        log.info("Getting documents without folder for user {}", userId);

        List<Document> documents = documentRepository.findByUserId(userId);
        return documents.stream()
                .filter(doc -> doc.getFolder() == null)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long documentId, Long userId) {
        log.info("Getting document {} for user {}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền truy cập
        if (!document.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        return mapToResponse(document);
    }

    @Override
    public DocumentResponse updateDocument(Long documentId, UpdateDocumentRequest request, Long userId) {
        log.info("Updating document {} for user {}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền sở hữu
        if (!document.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        // Cập nhật title
        document.setTitle(request.getTitle());

        // Cập nhật visibility
        if (request.getVisibility() != null) {
            document.setVisibility(request.getVisibility());
        }

        // Cập nhật folder
        if (request.getFolderId() != null) {
            Folder folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));

            // Kiểm tra quyền sở hữu folder
            if (!folder.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
            }

            document.setFolder(folder);
        } else {
            document.setFolder(null);
        }

        document = documentRepository.save(document);
        log.info("Document updated successfully: {}", documentId);

        return mapToResponse(document);
    }

    @Override
    public void deleteDocument(Long documentId, Long userId) {
        log.info("Deleting document {} for user {}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền sở hữu
        if (!document.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        long size = document.getFileSize() != null ? document.getFileSize() : 0L;
        Group docGroup = document.getGroup();

        // Xóa file vật lý
        fileStorageService.deleteFile(document.getFileUrl());

        // Xóa document trong database
        documentRepository.delete(document);

        // Cập nhật quota: trừ khỏi group nếu thuộc group, ngược lại trừ khỏi user
        if (docGroup != null) {
            storageQuotaService.decrementGroupUsage(docGroup, size);
        } else {
            storageQuotaService.decrementUserUsage(document.getUser(), size);
        }
        log.info("Document deleted successfully: {}", documentId);
    }

    @Override
    public void adminDeleteDocument(Long documentId) {
        log.info("Admin deleting document {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        long size = document.getFileSize() != null ? document.getFileSize() : 0L;
        Group docGroup = document.getGroup();

        // Best-effort storage cleanup — keep DB delete on if vendor storage fails.
        try {
            fileStorageService.deleteFile(document.getFileUrl());
        } catch (Exception ex) {
            log.warn("Storage delete failed for doc {} (continuing DB delete): {}", documentId, ex.getMessage());
        }

        documentRepository.delete(document);

        if (docGroup != null) {
            storageQuotaService.decrementGroupUsage(docGroup, size);
        } else {
            storageQuotaService.decrementUserUsage(document.getUser(), size);
        }
        log.info("Admin delete done for document {}", documentId);
    }

    @Override
    public Resource downloadDocument(Long documentId, Long userId) {
        log.info("Downloading document {} for user {}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Kiểm tra quyền truy cập:
        // - Owner luôn có quyền
        // - Document PUBLIC → ai cũng tải được
        // - Document trong folder PUBLIC → ai cũng tải được
        boolean isOwner = userId != null && document.getUser().getId().equals(userId);
        boolean isDocPublic = document.getVisibility() == com.example.fileshareR.enums.VisibilityType.PUBLIC;
        boolean isInPublicFolder = document.getFolder() != null
                && document.getFolder().getVisibility() == FolderVisibilityType.PUBLIC;

        if (!isOwner && !isDocPublic && !isInPublicFolder) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        // Tăng download count
        document.setDownloadCount(document.getDownloadCount() + 1);
        documentRepository.save(document);

        // Notify owner — but only when someone OTHER than the owner is downloading
        if (!isOwner) {
            String downloaderLabel = userId == null
                    ? "Một người dùng ẩn danh"
                    : "Một người dùng";
            notificationService.notifyUser(
                    document.getUser(),
                    NotificationType.DOCUMENT_DOWNLOADED,
                    "Tài liệu được tải xuống",
                    downloaderLabel + " vừa tải tài liệu \"" + document.getTitle() + "\"",
                    document.getId(),
                    "/documents/" + document.getId());
        }

        // Lấy file
        try {
            Path filePath = fileStorageService.getFilePath(document.getFileUrl());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.info("Document downloaded successfully: {}", documentId);
                return resource;
            } else {
                log.error("File not found or not readable: {}", document.getFileUrl());
                throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
            }
        } catch (MalformedURLException e) {
            log.error("Error creating file URL: {}", document.getFileUrl(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource previewDocument(Long documentId, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        boolean isOwner = userId != null && document.getUser().getId().equals(userId);
        boolean isDocPublic = document.getVisibility() == com.example.fileshareR.enums.VisibilityType.PUBLIC;
        boolean isInPublicFolder = document.getFolder() != null
                && document.getFolder().getVisibility() == FolderVisibilityType.PUBLIC;

        // Group docs: member nào cũng xem được
        boolean isGroupMember = false;
        if (document.getGroup() != null && userId != null) {
            isGroupMember = groupMemberRepository.existsByGroupIdAndUserId(document.getGroup().getId(), userId);
        }
        boolean isGroupPublic = document.getGroup() != null
                && document.getGroup().getVisibility() == GroupVisibilityType.PUBLIC;

        if (!isOwner && !isDocPublic && !isInPublicFolder && !isGroupMember && !isGroupPublic) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        // Tài liệu nhóm PENDING/REJECTED: chỉ chính chủ + admin/owner nhóm preview được
        if (document.getGroup() != null
                && document.getModerationStatus() != ModerationStatus.APPROVED) {
            boolean isAdminOrOwner = userId != null && isGroupAdminOrOwner(document.getGroup().getId(), userId);
            if (!isOwner && !isAdminOrOwner) {
                throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
            }
        }

        try {
            Path filePath = fileStorageService.getFilePath(document.getFileUrl());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) return resource;
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        } catch (MalformedURLException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> searchDocuments(String keyword, Long userId) {
        log.info("Searching documents with keyword '{}' for user {}", keyword, userId);

        List<Document> documents = documentRepository.searchWithRelevance(userId, keyword);
        return documents.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private DocumentResponse mapToResponse(Document document) {
        // Parse keywords từ JSON string
        List<String> keywordsList = null;
        if (document.getKeywords() != null) {
            try {
                keywordsList = Arrays.asList(objectMapper.readValue(document.getKeywords(), String[].class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse keywords JSON: {}", document.getKeywords());
            }
        }

        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .fileUrl(document.getFileUrl())
                .visibility(document.getVisibility())
                .summary(document.getSummary())
                .keywords(keywordsList)
                .downloadCount(document.getDownloadCount())
                .userId(document.getUser().getId())
                .userName(document.getUser().getFullName())
                .folderId(document.getFolder() != null ? document.getFolder().getId() : null)
                .folderName(document.getFolder() != null ? document.getFolder().getName() : null)
                .groupId(document.getGroup() != null ? document.getGroup().getId() : null)
                .groupName(document.getGroup() != null ? document.getGroup().getName() : null)
                .moderationStatus(document.getModerationStatus())
                .moderationReason(document.getModerationReason())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAnalysisResponse analyzeDocument(Long documentId, Long userId) {
        log.info("Analyzing document {} for user {}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (!document.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        String extractedText = document.getExtractedText();

        // Parse keywords
        List<String> keywordsList = null;
        if (document.getKeywords() != null) {
            try {
                keywordsList = Arrays.asList(objectMapper.readValue(document.getKeywords(), String[].class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse keywords JSON");
            }
        }

        // Parse TF-IDF vector và tạo topTerms
        List<DocumentAnalysisResponse.KeywordScore> topTerms = new ArrayList<>();
        if (document.getTfidfVector() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Double> tfidf = objectMapper.readValue(document.getTfidfVector(), Map.class);
                topTerms = tfidf.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(15)
                        .map(e -> DocumentAnalysisResponse.KeywordScore.builder()
                                .term(e.getKey())
                                .score(((Number) e.getValue()).doubleValue())
                                .build())
                        .collect(Collectors.toList());
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse TF-IDF vector JSON");
            }
        }

        // Tính thống kê
        DocumentAnalysisResponse.DocumentStats stats = null;
        if (extractedText != null && !extractedText.isBlank()) {
            String[] words = extractedText.split("\\s+");
            String[] sentences = extractedText.split("[.!?]+");
            Set<String> uniqueWords = Arrays.stream(words)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            stats = DocumentAnalysisResponse.DocumentStats.builder()
                    .totalWords(words.length)
                    .uniqueWords(uniqueWords.size())
                    .sentenceCount(sentences.length)
                    .characterCount(extractedText.length())
                    .build();
        }

        return DocumentAnalysisResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .summary(document.getSummary())
                .keywords(keywordsList)
                .topTerms(topTerms)
                .stats(stats)
                .build();
    }

    @Override
    public DocumentResponse reprocessDocumentNlp(Long documentId, Long userId) {
        log.info("Reprocessing NLP for document {} by user {}", documentId, userId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (!document.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        String extractedText = document.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            log.warn("No extracted text for document {}", documentId);
            return mapToResponse(document);
        }

        try {
            // Trích xuất từ khóa bằng AI
            List<String> keywordsList = nlpService.extractKeywordsWithAI(extractedText, TOP_KEYWORDS_COUNT);
            document.setKeywords(objectMapper.writeValueAsString(keywordsList));

            // Tạo summary bằng AI
            String summary = nlpService.generateSummaryWithAI(extractedText, SUMMARY_MAX_WORDS);
            document.setSummary(summary);

            // Tính TF-IDF vector
            Map<String, Double> tfidf = nlpService.calculateTfIdf(extractedText);
            Map<String, Double> topTfidf = tfidf.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(50)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, Map.Entry::getValue,
                            (e1, e2) -> e1, java.util.LinkedHashMap::new));
            document.setTfidfVector(objectMapper.writeValueAsString(topTfidf));

            document = documentRepository.save(document);
            log.info("NLP reprocessed successfully for document {}", documentId);
        } catch (Exception e) {
            log.error("Failed to reprocess NLP for document {}: {}", documentId, e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> findSimilarDocuments(Long documentId, Long userId, int limit) {
        log.info("Finding similar documents to {} for user {}", documentId, userId);

        Document sourceDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (!sourceDoc.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        if (sourceDoc.getTfidfVector() == null) {
            log.warn("Document {} has no TF-IDF vector", documentId);
            return Collections.emptyList();
        }

        // Parse source TF-IDF vector
        Map<String, Double> sourceVector;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(sourceDoc.getTfidfVector(), Map.class);
            sourceVector = new HashMap<>();
            parsed.forEach((k, v) -> sourceVector.put(k, ((Number) v).doubleValue()));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse source TF-IDF vector");
            return Collections.emptyList();
        }

        // Lấy tất cả documents của user (trừ document hiện tại)
        List<Document> allDocs = documentRepository.findByUserId(userId);

        // Tính similarity và sắp xếp
        List<Map.Entry<Document, Double>> similarities = new ArrayList<>();

        for (Document doc : allDocs) {
            if (doc.getId().equals(documentId) || doc.getTfidfVector() == null) {
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(doc.getTfidfVector(), Map.class);
                Map<String, Double> targetVector = new HashMap<>();
                parsed.forEach((k, v) -> targetVector.put(k, ((Number) v).doubleValue()));

                double similarity = nlpService.cosineSimilarity(sourceVector, targetVector);
                if (similarity > 0.1) { // Chỉ lấy các document có độ tương đồng > 10%
                    similarities.add(Map.entry(doc, similarity));
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse TF-IDF for document {}", doc.getId());
            }
        }

        // Sắp xếp theo similarity giảm dần và lấy top N
        return similarities.stream()
                .sorted(Map.Entry.<Document, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> mapToResponse(entry.getKey()))
                .collect(Collectors.toList());
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    // ── Group document methods ─────────────────────────────────────────────────

    @Override
    public DocumentResponse uploadGroupDocument(MultipartFile file,
            UploadGroupDocumentRequest request,
            Long groupId, Long userId) {
        log.info("User {} uploading group document to group {}", userId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        // Kiểm tra user là thành viên + lưu lại role để dùng cho moderation
        GroupMemberRole uploaderRole = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_ACCESS_DENIED))
                .getRole();

        // Kiểm tra ban upload còn hiệu lực
        groupBanRepository.findActiveUploadBan(groupId, userId, LocalDateTime.now())
                .ifPresent(ban -> {
                    throw new CustomException(ErrorCode.GROUP_UPLOAD_BANNED);
                });

        User user = userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Xử lý groupFolder
        GroupFolder groupFolder = null;
        if (request.getGroupFolderId() != null) {
            groupFolder = groupFolderRepository.findById(request.getGroupFolderId())
                    .orElseThrow(() -> new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND));
            if (!groupFolder.getGroup().getId().equals(groupId)) {
                throw new CustomException(ErrorCode.GROUP_FOLDER_NOT_FOUND);
            }
        }

        // Kiểm tra quota của group trước khi upload
        storageQuotaService.ensureGroupCanUpload(group, file.getSize());

        // Lưu file, trích xuất text, NLP
        String fileUrl = fileStorageService.storeFile(file, userId);
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        FileType fileType = FileType.valueOf(fileExtension.toUpperCase());
        Long fileSize = file.getSize();

        Path filePath = fileStorageService.getFilePath(fileUrl);
        String extractedText = textExtractionService.extractText(filePath, fileExtension);

        String keywords = null;
        String summary = null;
        String tfidfVector = null;
        if (extractedText != null && !extractedText.isBlank()) {
            try {
                List<String> kw = nlpService.extractKeywordsWithAI(extractedText, TOP_KEYWORDS_COUNT);
                keywords = objectMapper.writeValueAsString(kw);
                summary = nlpService.generateSummaryWithAI(extractedText, SUMMARY_MAX_WORDS);
                Map<String, Double> tfidf = nlpService.calculateTfIdf(extractedText);
                Map<String, Double> topTfidf = tfidf.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(50)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (e1, e2) -> e1, java.util.LinkedHashMap::new));
                tfidfVector = objectMapper.writeValueAsString(topTfidf);
            } catch (Exception e) {
                log.warn("NLP failed for group document: {}", e.getMessage());
            }
        }

        // ── Moderation: chỉ áp dụng cho MEMBER thường (OWNER/ADMIN tin tưởng) ────
        ModerationStatus moderationStatus = ModerationStatus.APPROVED;
        String moderationReason = null;
        Double moderationScore = null;
        LocalDateTime moderatedAt = null;
        if (uploaderRole == GroupMemberRole.MEMBER) {
            ContentModerationService.ModerationResult mr =
                    contentModerationService.moderate(extractedText);
            if (mr.getStatus() != ModerationStatus.APPROVED) {
                moderationStatus = ModerationStatus.PENDING;
                moderationReason = mr.getReason();
                moderationScore = mr.getScore();
                moderatedAt = LocalDateTime.now();
                log.info("Group document flagged PENDING: {}", moderationReason);
            }
        }

        Document document = Document.builder()
                .title(request.getTitle())
                .fileName(originalFilename)
                .fileType(fileType)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .visibility(request.getVisibility())
                .extractedText(extractedText)
                .keywords(keywords)
                .summary(summary)
                .tfidfVector(tfidfVector)
                .user(user)
                .group(group)
                .groupFolder(groupFolder)
                .downloadCount(0)
                .moderationStatus(moderationStatus)
                .moderationReason(moderationReason)
                .moderationScore(moderationScore)
                .moderatedAt(moderatedAt)
                .build();

        document = documentRepository.save(document);
        storageQuotaService.incrementGroupUsage(group, fileSize);
        log.info("Group document {} saved to group {} (status={})",
                document.getId(), groupId, moderationStatus);

        // Notify group admins + owner when doc needs moderation review
        if (moderationStatus == ModerationStatus.PENDING) {
            List<Long> adminUserIds = groupMemberRepository.findByGroupId(groupId).stream()
                    .filter(m -> m.getRole() == GroupMemberRole.ADMIN
                            || m.getRole() == GroupMemberRole.OWNER)
                    .map(m -> m.getUser().getId())
                    .toList();
            for (Long adminId : adminUserIds) {
                notificationService.notifyUser(
                        adminId,
                        NotificationType.GROUP_DOC_PENDING_REVIEW,
                        "Tài liệu cần duyệt",
                        user.getFullName() + " vừa upload tài liệu \""
                                + document.getTitle() + "\" cần admin duyệt.",
                        document.getId(),
                        "/groups/" + groupId);
            }
        }

        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getGroupDocuments(Long groupId, Long folderId, Long requesterId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getVisibility() == GroupVisibilityType.PRIVATE) {
            if (requesterId == null || !groupMemberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
                throw new CustomException(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
            }
        }

        List<Document> docs;
        if (folderId == null) {
            // Tất cả tài liệu trong nhóm
            docs = documentRepository.findByGroupId(groupId);
        } else if (folderId == -1L) {
            // Tài liệu không thuộc folder nào
            docs = documentRepository.findByGroupIdAndGroupFolderIsNull(groupId);
        } else {
            // Tài liệu trong folder cụ thể
            docs = documentRepository.findByGroupIdAndGroupFolderId(groupId, folderId);
        }

        // Moderation visibility: admin/owner thấy tất cả; member thường chỉ thấy
        // APPROVED + tài liệu của chính họ (kể cả PENDING/REJECTED — để biết file
        // của mình đang ở trạng thái nào). Guest/non-member chỉ thấy APPROVED.
        boolean isAdminOrOwner = requesterId != null && isGroupAdminOrOwner(groupId, requesterId);
        if (!isAdminOrOwner) {
            final Long uid = requesterId;
            docs = docs.stream()
                    .filter(d -> d.getModerationStatus() == ModerationStatus.APPROVED
                            || (uid != null && d.getUser().getId().equals(uid)))
                    .collect(Collectors.toList());
        }

        return docs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private boolean isGroupAdminOrOwner(Long groupId, Long userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupMemberRole.OWNER || m.getRole() == GroupMemberRole.ADMIN)
                .orElse(false);
    }

    @Override
    @Transactional
    public Resource downloadGroupDocument(Long documentId, Long groupId, Long requesterId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getVisibility() == GroupVisibilityType.PRIVATE) {
            if (requesterId == null || !groupMemberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
                throw new CustomException(ErrorCode.GROUP_PRIVATE_ACCESS_DENIED);
            }
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND));

        if (!document.getGroup().getId().equals(groupId)) {
            throw new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND);
        }

        // Tài liệu PENDING/REJECTED: chỉ chính chủ + admin/owner nhóm download được
        if (document.getModerationStatus() != ModerationStatus.APPROVED) {
            boolean isDocOwner = requesterId != null && document.getUser().getId().equals(requesterId);
            boolean isAdminOrOwner = requesterId != null && isGroupAdminOrOwner(groupId, requesterId);
            if (!isDocOwner && !isAdminOrOwner) {
                throw new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND);
            }
        }

        document.setDownloadCount(document.getDownloadCount() + 1);
        documentRepository.save(document);

        // Notify owner — only when someone OTHER than owner is downloading
        boolean isOwner = requesterId != null && document.getUser().getId().equals(requesterId);
        if (!isOwner) {
            notificationService.notifyUser(
                    document.getUser(),
                    NotificationType.DOCUMENT_DOWNLOADED,
                    "Tài liệu nhóm được tải xuống",
                    "Một thành viên nhóm \"" + group.getName()
                            + "\" vừa tải tài liệu \"" + document.getTitle() + "\"",
                    document.getId(),
                    "/groups/" + groupId);
        }

        try {
            Path filePath = fileStorageService.getFilePath(document.getFileUrl());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND);
        } catch (MalformedURLException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteGroupDocument(Long documentId, Long groupId, Long userId) {
        log.info("User {} deleting group document {} from group {}", userId, documentId, groupId);

        groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND));

        if (!document.getGroup().getId().equals(groupId)) {
            throw new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND);
        }

        // Cho phép xóa nếu: owner tài liệu HOẶC ADMIN/OWNER nhóm
        boolean isDocOwner = document.getUser().getId().equals(userId);
        boolean isGroupAdminOrOwner = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupMemberRole.ADMIN || m.getRole() == GroupMemberRole.OWNER)
                .orElse(false);

        if (!isDocOwner && !isGroupAdminOrOwner) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }

        long size = document.getFileSize() != null ? document.getFileSize() : 0L;
        Group docGroup = document.getGroup();

        fileStorageService.deleteFile(document.getFileUrl());
        documentRepository.delete(document);
        if (docGroup != null) {
            storageQuotaService.decrementGroupUsage(docGroup, size);
        }
        log.info("Group document {} deleted from group {}", documentId, groupId);
    }

    // ── Public profile + save-to-folder ──────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPublicDocumentsByUser(Long userId) {
        log.info("Getting public documents for user {}", userId);
        return documentRepository.findByUserIdAndVisibility(userId, com.example.fileshareR.enums.VisibilityType.PUBLIC)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DocumentResponse saveDocumentToFolder(Long documentId, Long targetFolderId, Long userId) {
        log.info("User {} saving document {} to folder {}", userId, documentId, targetFolderId);

        Document source = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        User user = userService.getUserById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new CustomException(ErrorCode.FOLDER_NOT_FOUND));
            if (!targetFolder.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.FOLDER_ACCESS_DENIED);
            }
        }

        long copySize = source.getFileSize() != null ? source.getFileSize() : 0L;
        storageQuotaService.ensureUserCanUpload(user, copySize);

        // Tăng download count cho document gốc
        source.setDownloadCount(source.getDownloadCount() + 1);
        documentRepository.save(source);

        Document copy = Document.builder()
                .title(source.getTitle())
                .fileName(source.getFileName())
                .fileType(source.getFileType())
                .fileSize(source.getFileSize())
                .fileUrl(source.getFileUrl())
                .visibility(com.example.fileshareR.enums.VisibilityType.PRIVATE)
                .extractedText(source.getExtractedText())
                .summary(source.getSummary())
                .keywords(source.getKeywords())
                .tfidfVector(source.getTfidfVector())
                .downloadCount(0)
                .user(user)
                .folder(targetFolder)
                .build();

        copy = documentRepository.save(copy);
        storageQuotaService.incrementUserUsage(user, copySize);
        log.info("Document {} saved as copy {} in folder {}", documentId, copy.getId(), targetFolderId);
        return mapToResponse(copy);
    }

    // ── Moderation ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPendingGroupDocuments(Long groupId, Long requesterId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        requireGroupAdminOrOwner(groupId, requesterId);

        return documentRepository
                .findByGroupIdAndModerationStatusOrderByCreatedAtDesc(groupId, ModerationStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingGroupDocuments(Long groupId, Long requesterId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        requireGroupAdminOrOwner(groupId, requesterId);
        return documentRepository.countByGroupIdAndModerationStatus(groupId, ModerationStatus.PENDING);
    }

    @Override
    public DocumentResponse approveDocument(Long documentId, Long requesterId) {
        log.info("User {} approving document {}", requesterId, documentId);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND));
        if (doc.getGroup() == null) {
            throw new CustomException(ErrorCode.MODERATION_NOT_GROUP_DOCUMENT);
        }
        requireGroupAdminOrOwner(doc.getGroup().getId(), requesterId);
        if (doc.getModerationStatus() == ModerationStatus.APPROVED) {
            throw new CustomException(ErrorCode.MODERATION_NOT_PENDING);
        }

        User reviewer = userService.getUserById(requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        doc.setModerationStatus(ModerationStatus.APPROVED);
        doc.setModerationReason(null);
        doc.setModeratedBy(reviewer);
        doc.setModeratedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);
        return mapToResponse(doc);
    }

    @Override
    public DocumentResponse rejectDocument(Long documentId, String reason, Long requesterId) {
        log.info("User {} rejecting document {} with reason '{}'", requesterId, documentId, reason);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_DOCUMENT_NOT_FOUND));
        if (doc.getGroup() == null) {
            throw new CustomException(ErrorCode.MODERATION_NOT_GROUP_DOCUMENT);
        }
        requireGroupAdminOrOwner(doc.getGroup().getId(), requesterId);
        if (doc.getModerationStatus() == ModerationStatus.REJECTED) {
            throw new CustomException(ErrorCode.MODERATION_NOT_PENDING);
        }

        User reviewer = userService.getUserById(requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        doc.setModerationStatus(ModerationStatus.REJECTED);
        if (reason != null && !reason.isBlank()) {
            doc.setModerationReason(reason);
        }
        doc.setModeratedBy(reviewer);
        doc.setModeratedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);
        return mapToResponse(doc);
    }

    private void requireGroupAdminOrOwner(Long groupId, Long userId) {
        if (userId == null || !isGroupAdminOrOwner(groupId, userId)) {
            throw new CustomException(ErrorCode.MODERATION_PERMISSION_DENIED);
        }
    }
}
