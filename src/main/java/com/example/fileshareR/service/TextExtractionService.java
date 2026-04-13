package com.example.fileshareR.service;

import java.nio.file.Path;

public interface TextExtractionService {

    /**
     * Trích xuất text từ file
     * @param filePath Đường dẫn file
     * @param fileType Loại file (PDF, DOCX, TXT)
     * @return Nội dung text đã trích xuất
     */
    String extractText(Path filePath, String fileType);
}
