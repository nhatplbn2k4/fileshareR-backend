package com.example.fileshareR.service;

import java.util.List;
import java.util.Map;

public interface NlpService {

    /**
     * Trích xuất từ khóa từ văn bản sử dụng TF-IDF
     * 
     * @param text Văn bản cần phân tích
     * @param topN Số lượng từ khóa muốn lấy
     * @return Danh sách từ khóa theo thứ tự điểm TF-IDF giảm dần
     */
    List<String> extractKeywords(String text, int topN);

    /**
     * Trích xuất từ khóa sử dụng AI (Gemini)
     * 
     * @param text Văn bản cần phân tích
     * @param topN Số lượng từ khóa muốn lấy
     * @return Danh sách từ khóa
     */
    List<String> extractKeywordsWithAI(String text, int topN);

    /**
     * Tính TF-IDF vector cho văn bản
     * 
     * @param text Văn bản cần phân tích
     * @return Map chứa term và điểm TF-IDF
     */
    Map<String, Double> calculateTfIdf(String text);

    /**
     * Tạo summary cho văn bản (extractive - lấy các câu quan trọng nhất)
     * 
     * @param text         Văn bản cần tạo summary
     * @param maxSentences Số câu tối đa trong summary
     * @return Summary của văn bản
     */
    String generateSummary(String text, int maxSentences);

    /**
     * Tạo summary sử dụng AI (Gemini) - abstractive summarization
     * 
     * @param text     Văn bản cần tóm tắt
     * @param maxWords Số từ tối đa của bản tóm tắt
     * @return Bản tóm tắt do AI tạo
     */
    String generateSummaryWithAI(String text, int maxWords);

    /**
     * Tính độ tương đồng cosine giữa 2 vector TF-IDF
     * 
     * @param vector1 Vector TF-IDF thứ nhất
     * @param vector2 Vector TF-IDF thứ hai
     * @return Độ tương đồng (0-1)
     */
    double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2);
}
