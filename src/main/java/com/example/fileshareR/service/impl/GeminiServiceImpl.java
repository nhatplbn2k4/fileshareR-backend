package com.example.fileshareR.service.impl;

import com.example.fileshareR.service.GeminiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class GeminiServiceImpl implements GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiServiceImpl(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    @Override
    public String summarize(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Giới hạn độ dài input để tránh vượt token limit
        String truncatedText = truncateText(text, 10000);

        String prompt = String.format(
                "Tóm tắt nội dung chính của văn bản sau trong 1-2 câu ngắn gọn (tối đa %d từ). " +
                        "Chỉ trả về bản tóm tắt về chủ đề, không giải thích:\n\n%s",
                maxWords, truncatedText);

        try {
            String response = generateContent(prompt);
            return response != null ? response.trim() : "";
        } catch (Exception e) {
            log.error("Failed to summarize text with Gemini: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public List<String> extractKeywords(String text, int count) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String truncatedText = truncateText(text, 10000);

        String prompt = String.format(
                "Trích xuất %d từ khóa hoặc cụm từ quan trọng nhất từ văn bản sau. " +
                        "Trả về danh sách từ khóa cách nhau bởi dấu phẩy, không đánh số, không giải thích:\n\n%s",
                count, truncatedText);

        try {
            String response = generateContent(prompt);
            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            // Parse response thành danh sách từ khóa
            return Arrays.stream(response.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(count)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to extract keywords with Gemini: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String generateContent(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured");
            return null;
        }

        String url = String.format("%s/%s:generateContent?key=%s", apiUrl, model, apiKey);
        log.info("=== GEMINI API CALL ===");
        log.info("URL: {}", url.replace(apiKey, "***"));
        log.info("API Key configured: {}", apiKey.substring(0, 10) + "...");

        try {
            // Tạo request body
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, String> part = new HashMap<>();

            part.put("text", prompt);
            content.put("parts", Collections.singletonList(part));
            requestBody.put("contents", Collections.singletonList(content));

            // Cấu hình generation
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 1024);
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling Gemini API...");
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            log.info("Gemini response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String result = parseGeminiResponse(response.getBody());
                log.info("=== GEMINI SUCCESS === Result length: {}", result != null ? result.length() : 0);
                return result;
            }

            log.error("Gemini API returned non-OK status: {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("=== GEMINI FAILED === Error: {}", e.getMessage(), e);
            return null;
        }
    }

    private String parseGeminiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");

                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }

            log.warn("Unexpected Gemini response structure");
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
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
