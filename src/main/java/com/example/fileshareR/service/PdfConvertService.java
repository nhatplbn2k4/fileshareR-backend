package com.example.fileshareR.service;

import com.example.fileshareR.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface PdfConvertService {

    /** Engine sẽ dùng cho user này (theo plan). */
    String getEngineNameForUser(Long userId);

    /** Convert file PDF upload, trả về DOCX bytes. */
    byte[] convertUploaded(MultipartFile pdfFile, Long userId);

    /** Convert document có sẵn của user (chỉ PDF của user mình), trả về DOCX bytes. */
    byte[] convertFromDocument(Long documentId, Long userId);

    /** Convert upload và lưu DOCX vào folder của user. */
    DocumentResponse convertUploadedAndSave(MultipartFile pdfFile, Long folderId, Long userId);

    /** Convert document có sẵn và lưu DOCX vào folder. */
    DocumentResponse convertFromDocumentAndSave(Long documentId, Long folderId, Long userId);

    /** Tên file DOCX đầu ra từ tên PDF. */
    String generateDocxFileName(String pdfName);
}
