package com.example.fileshareR.service.impl;

import com.example.fileshareR.service.ContentModerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * Hai tầng kiểm duyệt:
 *
 *  1. Keyword blacklist (offline, instant) — đọc từ classpath:moderation/blacklist.txt.
 *     Mỗi entry được khớp dạng substring trên text đã chuẩn hóa (lowercase + strip dấu).
 *
 *  2. OpenAI Moderation API (nếu có api-key + qua tầng 1 vẫn nghi/cần xác nhận).
 *     Endpoint /v1/moderations là MIỄN PHÍ với các project có API key thông thường,
 *     trả về flagged + score cho từng category (sexual, violence, hate, ...).
 *
 * Logic ra quyết định:
 *   - Tầng 1 hit  → PENDING ngay (không cần gọi AI)
 *   - Tầng 1 miss → gọi tầng 2 nếu có; nếu AI flag với score ≥ threshold → PENDING
 *   - Cả hai không flag → APPROVED
 *   - Lỗi tầng 2 → log + treat as APPROVED (fail-open) để upload không bị chặn vì AI lỗi
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentModerationServiceImpl implements ContentModerationService {

    private final ObjectMapper objectMapper;

    @Value("${openai.moderation.api-key:}")
    private String apiKey;

    @Value("${openai.moderation.api-url:https://api.openai.com/v1/moderations}")
    private String apiUrl;

    @Value("${openai.moderation.model:omni-moderation-latest}")
    private String model;

    @Value("${openai.moderation.threshold:0.5}")
    private double threshold;

    @Value("${openai.moderation.max-chars:8000}")
    private int maxChars;

    private List<String> blacklist = Collections.emptyList();

    @PostConstruct
    void loadBlacklist() {
        List<String> result = new ArrayList<>();
        ClassPathResource res = new ClassPathResource("moderation/blacklist.txt");
        if (!res.exists()) {
            log.warn("Moderation blacklist file not found at classpath:moderation/blacklist.txt — keyword stage disabled");
            return;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                result.add(normalize(trimmed));
            }
        } catch (Exception e) {
            log.error("Failed to load moderation blacklist", e);
        }
        this.blacklist = result;
        log.info("Loaded {} moderation keywords", blacklist.size());
    }

    @Override
    public ModerationResult moderate(String text) {
        if (text == null || text.isBlank()) {
            return ModerationResult.approved();
        }

        // Stage 1: keyword blacklist (fast, offline)
        String normalized = normalize(text);
        for (String keyword : blacklist) {
            if (normalized.contains(keyword)) {
                log.info("Moderation FLAG (keyword): '{}'", keyword);
                return ModerationResult.flagged(
                        "Phát hiện từ khóa nhạy cảm: " + keyword,
                        1.0);
            }
        }

        // Stage 2: OpenAI Moderation API
        if (apiKey == null || apiKey.isBlank()) {
            // Không cấu hình AI → coi như sạch
            return ModerationResult.approved();
        }

        try {
            return callOpenAiModeration(text);
        } catch (Exception e) {
            // Fail-open: không chặn upload chỉ vì AI lỗi
            log.warn("OpenAI moderation failed, fail-open: {}", e.getMessage());
            return ModerationResult.approved();
        }
    }

    private ModerationResult callOpenAiModeration(String text) {
        String input = (maxChars > 0 && text.length() > maxChars) ? text.substring(0, maxChars) : text;

        Map<String, Object> body = Map.of(
                "model", model,
                "input", input);

        RestClient client = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        String resp;
        try {
            resp = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            log.error("OpenAI moderation API error [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("OpenAI moderation API error", ex);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(resp);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse OpenAI response", e);
        }

        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return ModerationResult.approved();
        }

        JsonNode first = results.get(0);
        boolean flagged = first.path("flagged").asBoolean(false);

        // Tìm category có score cao nhất để báo cáo
        JsonNode categoryScores = first.path("category_scores");
        String topCategory = null;
        double topScore = 0.0;
        Iterator<Map.Entry<String, JsonNode>> it = categoryScores.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            double s = e.getValue().asDouble(0.0);
            if (s > topScore) {
                topScore = s;
                topCategory = e.getKey();
            }
        }

        // Flag nếu (flagged=true theo OpenAI) HOẶC (score cao nhất ≥ threshold)
        if (flagged || topScore >= threshold) {
            String reason = topCategory != null
                    ? String.format("AI phát hiện nội dung '%s' (score=%.2f)", topCategory, topScore)
                    : "AI gắn cờ nội dung nhạy cảm";
            log.info("Moderation FLAG (AI): {}", reason);
            return ModerationResult.flagged(reason, topScore);
        }

        return ModerationResult.approved();
    }

    /**
     * Chuẩn hóa: bỏ dấu tiếng Việt + lowercase. Giúp keyword "địt" khớp "dit",
     * "ma túy" khớp "ma tuy", v.v.
     */
    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Đặc thù tiếng Việt: đ/Đ không phải dấu nhưng cần thay
        n = n.replace('đ', 'd').replace('Đ', 'd');
        return n.toLowerCase(Locale.ROOT);
    }
}
