package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.common.util.ByteArrayMultipartFile;
import com.example.fileshareR.dto.request.CreateDocumentRequest;
import com.example.fileshareR.dto.response.DocumentResponse;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.VisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.service.FileStorageService;
import com.example.fileshareR.service.PdfConvertEngine;
import com.example.fileshareR.service.PdfConvertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PdfConvertServiceImpl implements PdfConvertService {

    private static final String PREMIUM_PLAN_CODE = "PREMIUM";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;

    @Qualifier("localPdfConvertEngine")
    private final PdfConvertEngine localEngine;

    @Qualifier("cloudConvertEngine")
    private final PdfConvertEngine cloudEngine;

    @Override
    public String getEngineNameForUser(Long userId) {
        return pickEngine(userId).getEngineName();
    }

    private PdfConvertEngine pickEngine(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        boolean isPremium = user.getPlan() != null && PREMIUM_PLAN_CODE.equals(user.getPlan().getCode());
        PdfConvertEngine engine = isPremium ? cloudEngine : localEngine;
        log.info("User {} ({}) → engine={}", userId,
                isPremium ? "PREMIUM" : "FREE", engine.getEngineName());
        return engine;
    }

    @Override
    public byte[] convertUploaded(MultipartFile pdfFile, Long userId) {
        validateIsPdf(pdfFile.getOriginalFilename(), pdfFile.getContentType());
        try {
            return runWithFallback(pickEngine(userId),
                    engine -> engine.convertPdfToWord(pdfFile));
        } catch (IOException e) {
            log.error("Convert failed for user {}: {}", userId, e.getMessage(), e);
            throw new CustomException(ErrorCode.CONVERT_FAILED);
        }
    }

    @Override
    public byte[] convertFromDocument(Long documentId, Long userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        // Chỉ user sở hữu document
        if (!doc.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED);
        }
        if (!"PDF".equalsIgnoreCase(doc.getFileType().name())) {
            throw new CustomException(ErrorCode.CONVERT_PDF_ONLY);
        }

        Path filePath = fileStorageService.getFilePath(doc.getFileUrl());
        try {
            byte[] pdfBytes = Files.readAllBytes(filePath);
            return runWithFallback(pickEngine(userId),
                    engine -> engine.convertPdfToWord(pdfBytes, doc.getFileName()));
        } catch (IOException e) {
            log.error("Convert from document {} failed: {}", documentId, e.getMessage(), e);
            throw new CustomException(ErrorCode.CONVERT_FAILED);
        }
    }

    /**
     * Chạy engine, nếu là CloudConvert mà hết credit thì fallback sang Local
     * (để user PREMIUM vẫn có kết quả thay vì lỗi cứng).
     */
    private byte[] runWithFallback(PdfConvertEngine engine, ConvertCall call) throws IOException {
        try {
            return call.run(engine);
        } catch (CustomException ce) {
            if (ce.getErrorCode() == ErrorCode.CONVERT_CREDITS_EXCEEDED
                    && engine != localEngine) {
                log.warn("CloudConvert credits exhausted, falling back to local engine");
                return call.run(localEngine);
            }
            throw ce;
        }
    }

    @FunctionalInterface
    private interface ConvertCall {
        byte[] run(PdfConvertEngine engine) throws IOException;
    }

    @Override
    public DocumentResponse convertUploadedAndSave(MultipartFile pdfFile, Long folderId, Long userId) {
        byte[] docxBytes = convertUploaded(pdfFile, userId);
        String docxName = generateDocxFileName(pdfFile.getOriginalFilename());
        return saveDocxAsDocument(docxBytes, docxName, folderId, userId);
    }

    @Override
    public DocumentResponse convertFromDocumentAndSave(Long documentId, Long folderId, Long userId) {
        Document source = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        byte[] docxBytes = convertFromDocument(documentId, userId);
        String docxName = generateDocxFileName(source.getFileName());
        return saveDocxAsDocument(docxBytes, docxName, folderId, userId);
    }

    private DocumentResponse saveDocxAsDocument(byte[] docxBytes, String docxName, Long folderId, Long userId) {
        ByteArrayMultipartFile docxFile = new ByteArrayMultipartFile(
                "file", docxName, DOCX_CONTENT_TYPE, docxBytes);

        CreateDocumentRequest req = new CreateDocumentRequest();
        req.setTitle(stripExtension(docxName));
        req.setFolderId(folderId);
        req.setVisibility(VisibilityType.PRIVATE);

        return documentService.uploadDocument(docxFile, req, userId);
    }

    @Override
    public String generateDocxFileName(String pdfName) {
        if (pdfName == null || pdfName.isEmpty()) return "converted_document.docx";
        return pdfName.replaceAll("(?i)\\.pdf$", "") + ".docx";
    }

    private String stripExtension(String name) {
        if (name == null) return "Untitled";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void validateIsPdf(String filename, String contentType) {
        boolean okType = contentType != null && contentType.equalsIgnoreCase("application/pdf");
        boolean okExt = filename != null && filename.toLowerCase().endsWith(".pdf");
        if (!okType && !okExt) {
            throw new CustomException(ErrorCode.CONVERT_PDF_ONLY);
        }
    }
}
