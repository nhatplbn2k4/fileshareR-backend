package com.example.fileshareR.service;

import java.util.List;

public interface GeminiService {

    /**
     * Tạo bản tóm tắt ngắn gọn cho văn bản
     * 
     * @param text     Văn bản cần tóm tắt
     * @param maxWords Số từ tối đa của bản tóm tắt
     * @return Bản tóm tắt
     */
    String summarize(String text, int maxWords);

    /**
     * Trích xuất từ khóa chính từ văn bản
     * 
     * @param text  Văn bản cần phân tích
     * @param count Số lượng từ khóa cần trích xuất
     * @return Danh sách từ khóa
     */
    List<String> extractKeywords(String text, int count);

    /**
     * Gửi prompt tùy chỉnh đến Gemini
     * 
     * @param prompt Prompt cần gửi
     * @return Phản hồi từ Gemini
     */
    String generateContent(String prompt);
}
