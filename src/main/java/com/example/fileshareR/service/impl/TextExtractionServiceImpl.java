package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.service.TextExtractionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class TextExtractionServiceImpl implements TextExtractionService {

    @Override
    public String extractText(Path filePath, String fileType) {
        if (!Files.exists(filePath)) {
            log.error("File not found: {}", filePath);
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        try {
            return switch (fileType.toLowerCase()) {
                case "pdf" -> extractTextFromPdf(filePath);
                case "docx", "doc" -> extractTextFromDocx(filePath);
                case "txt" -> extractTextFromTxt(filePath);
                default -> {
                    log.error("Unsupported file type: {}", fileType);
                    throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
                }
            };
        } catch (IOException e) {
            log.error("Error extracting text from file: {}", filePath, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Trích xuất text từ file PDF
     */
    private String extractTextFromPdf(Path filePath) throws IOException {
        log.info("Extracting text from PDF: {}", filePath);

        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Loại bỏ các ký tự không hợp lệ cho PostgreSQL
            text = sanitizeText(text);

            log.info("Successfully extracted {} characters from PDF", text.length());
            return text;
        }
    }

    /**
     * Trích xuất text từ file DOCX
     */
    private String extractTextFromDocx(Path filePath) throws IOException {
        log.info("Extracting text from DOCX: {}", filePath);

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
                text.append(paragraph.getText()).append("\n");
            }

            String extractedText = sanitizeText(text.toString());
            log.info("Successfully extracted {} characters from DOCX", extractedText.length());
            return extractedText;
        }
    }

    /**
     * Trích xuất text từ file TXT
     */
    private String extractTextFromTxt(Path filePath) throws IOException {
        log.info("Extracting text from TXT: {}", filePath);

        String text = Files.readString(filePath);
        text = sanitizeText(text);
        log.info("Successfully extracted {} characters from TXT", text.length());
        return text;
    }

    /**
     * Loại bỏ các ký tự không hợp lệ cho PostgreSQL UTF-8
     * - Null bytes (0x00)
     * - Các ký tự control không hợp lệ
     */
    private String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        // Loại bỏ null bytes và các ký tự control (trừ tab, newline, carriage return)
        return text.replaceAll("\\x00", "")
                   .replaceAll("[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
