package com.example.fileshareR.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PdfConvertEngine {
    byte[] convertPdfToWord(MultipartFile pdfFile) throws IOException;

    byte[] convertPdfToWord(byte[] pdfBytes, String originalName) throws IOException;

    String getEngineName();
}
