package com.example.fileshareR.service.impl;

import com.example.fileshareR.service.GeminiService;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gemini qua Vertex AI — dùng credit GCP $300 free trial thay vì AI Studio paid.
 * <p>
 * Auth: Application Default Credentials (ADC) — đọc service account JSON từ
 * env var {@code GOOGLE_APPLICATION_CREDENTIALS} (đường dẫn file).
 * <p>
 * Project + region cấu hình qua application.yaml ({@code gemini.vertex.*}).
 */
@Slf4j
@Service
public class GeminiServiceImpl implements GeminiService {

    @Value("${gemini.vertex.project-id:}")
    private String projectId;

    @Value("${gemini.vertex.location:asia-southeast1}")
    private String location;

    @Value("${gemini.vertex.model:gemini-2.5-flash}")
    private String model;

    private VertexAI vertexAi;
    private GenerativeModel generativeModel;

    @PostConstruct
    void init() {
        if (projectId == null || projectId.isBlank()) {
            log.warn("Vertex AI project-id chưa cấu hình — GeminiService sẽ no-op");
            return;
        }
        try {
            this.vertexAi = new VertexAI(projectId, location);
            this.generativeModel = new GenerativeModel(model, vertexAi);
            log.info("Vertex AI initialized: project={}, location={}, model={}",
                    projectId, location, model);
        } catch (Exception e) {
            log.error("Vertex AI init failed: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (vertexAi != null) {
            try {
                vertexAi.close();
            } catch (Exception e) {
                log.warn("Vertex AI close failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public String summarize(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String truncated = truncateText(text, 10000);
        String prompt = String.format(
                "Tóm tắt nội dung chính của văn bản sau trong 1-2 câu ngắn gọn (tối đa %d từ). " +
                        "Chỉ trả về bản tóm tắt về chủ đề, không giải thích:\n\n%s",
                maxWords, truncated);
        try {
            String response = generateContent(prompt);
            return response != null ? response.trim() : "";
        } catch (Exception e) {
            log.error("Vertex AI summarize failed: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public List<String> extractKeywords(String text, int count) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String truncated = truncateText(text, 10000);
        String prompt = String.format(
                "Trích xuất %d từ khóa hoặc cụm từ quan trọng nhất từ văn bản sau. " +
                        "Trả về danh sách từ khóa cách nhau bởi dấu phẩy, không đánh số, không giải thích:\n\n%s",
                count, truncated);
        try {
            String response = generateContent(prompt);
            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.stream(response.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(count)
                    .toList();
        } catch (Exception e) {
            log.error("Vertex AI extractKeywords failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String generateContent(String prompt) {
        if (generativeModel == null) {
            log.warn("Vertex AI chưa khởi tạo (thiếu project-id hoặc credentials) — bỏ qua");
            return null;
        }
        try {
            GenerateContentResponse response = generativeModel.generateContent(prompt);
            String result = ResponseHandler.getText(response);
            log.debug("Vertex AI response length={}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("Vertex AI generateContent failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private String truncateText(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
