package com.example.fileshareR.service.impl;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.service.NlpService;
import com.example.fileshareR.service.PlagiarismMatch;
import com.example.fileshareR.service.PlagiarismSourceProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * So sánh tài liệu với nội dung internet thông qua Serper.dev (Google Search API wrapper).
 *
 * Luồng:
 * 1. Trích 2 cụm từ đặc trưng (exact-phrase) từ nội dung tài liệu
 * 2. Gọi Serper API (2.500 query miễn phí khi đăng ký) với từng cụm
 * 3. Fetch HTML trang kết quả → trích text → tính cosine similarity TF-IDF
 * 4. Trả về PlagiarismMatch với externalUrl (không có matchedDocumentId)
 *
 * Bật bằng cách set env:
 *   PLAGIARISM_WEB_ENABLED=true
 *   WEB_SEARCH_API_KEY=<key từ serper.dev — đăng ký miễn phí, 2500 query>
 *
 * Hướng dẫn lấy key:
 *   1. Vào serper.dev → Sign up (Google/GitHub)
 *   2. Dashboard → copy API Key
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSearchPlagiarismProvider implements PlagiarismSourceProvider {

    private static final String SERPER_URL = "https://google.serper.dev/search";
    private static final int MAX_PAGE_TEXT_CHARS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int RESULTS_PER_QUERY = 5;

    @Value("${plagiarism.providers.web-search.enabled:false}")
    private boolean enabled;

    @Value("${plagiarism.providers.web-search.api-key:}")
    private String apiKey;

    @Value("${plagiarism.providers.web-search.max-queries-per-doc:2}")
    private int maxQueriesPerDoc;

    private final NlpService nlpService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "web-search";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !apiKey.isBlank();
    }

    @Override
    public List<PlagiarismMatch> findMatches(Document doc, double threshold, int maxResults) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            log.info("Plagiarism[web]: doc {} has no extracted text → skip", doc.getId());
            return List.of();
        }

        List<SearchQuery> queries = buildSearchQueries(text);
        if (queries.isEmpty()) {
            log.info("Plagiarism[web]: doc {} — could not build search queries", doc.getId());
            return List.of();
        }

        Map<String, PlagiarismMatch> results = new LinkedHashMap<>();

        int queryCount = 0;
        for (SearchQuery sq : queries) {
            if (queryCount >= maxQueriesPerDoc) break;
            queryCount++;

            log.info("Plagiarism[web]: doc {} query #{}: \"{}\"", doc.getId(), queryCount, sq.query());
            List<SearchResult> hits = callSerperApi(sq.query());

            Map<String, Double> contextVector = nlpService.calculateTfIdf(sq.context());
            String queryLower = sq.query().toLowerCase();

            for (SearchResult hit : hits) {
                if (results.containsKey(hit.url())) continue;
                String pageText = fetchPageText(hit.url(), hit.snippet());
                if (pageText.isBlank()) continue;

                // Kiểm tra 2 tầng:
                // 1. Exact phrase: câu query có xuất hiện trực tiếp trong trang không?
                // 2. Cosine (context): đo mức độ tương đồng nội dung xung quanh
                boolean exactMatch = pageText.toLowerCase().contains(queryLower);
                Map<String, Double> pageVector = nlpService.calculateTfIdf(pageText);
                double cosine = nlpService.cosineSimilarity(contextVector, pageVector);

                // Score cuối: nếu có exact match → thưởng thêm 0.4 (gần như chắc chắn đạo văn)
                double score = exactMatch ? Math.min(1.0, cosine + 0.4) : cosine;
                log.debug("Plagiarism[web]: doc {} vs {} → cosine={} exact={} score={}",
                        doc.getId(), hit.url(), String.format("%.3f", cosine), exactMatch, String.format("%.3f", score));

                if (score >= threshold) {
                    results.put(hit.url(), new PlagiarismMatch(
                            null,
                            hit.url(),
                            hit.name(),
                            null,
                            score,
                            hit.snippet()));
                }
            }
        }

        return results.values().stream()
                .sorted(Comparator.comparingDouble(PlagiarismMatch::similarityScore).reversed())
                .limit(maxResults)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Trích câu thực từ văn bản làm search query, kèm đoạn văn context (~500 ký tự)
     * xung quanh câu đó. Context được dùng để so sánh cosine thay vì toàn bộ tài liệu,
     * giúp similarity cao hơn khi trang web chứa đoạn văn tương tự.
     */
    private List<SearchQuery> buildSearchQueries(String text) {
        String[] sentences = text.split("[.!?\\n]+");
        List<SearchQuery> queries = new ArrayList<>();

        for (int i = 0; i < sentences.length; i++) {
            String trimmed = sentences[i].replaceAll("\\s+", " ").trim();
            String[] words = trimmed.split(" ");
            if (words.length >= 6 && words.length <= 18) {
                // Lấy 3 câu xung quanh làm context để so sánh
                StringBuilder ctx = new StringBuilder();
                for (int j = Math.max(0, i - 1); j <= Math.min(sentences.length - 1, i + 2); j++) {
                    ctx.append(sentences[j].trim()).append(" ");
                }
                String context = ctx.toString().trim();
                queries.add(new SearchQuery(trimmed, context.isBlank() ? trimmed : context));
                if (queries.size() >= maxQueriesPerDoc) break;
            }
        }

        // Fallback: lấy 10 từ đầu tiên + 500 ký tự làm context
        if (queries.isEmpty()) {
            String[] words = text.trim().split("\\s+");
            if (words.length >= 5) {
                int end = Math.min(10, words.length);
                String query = String.join(" ", java.util.Arrays.copyOfRange(words, 0, end));
                String context = text.length() > 500 ? text.substring(0, 500) : text;
                queries.add(new SearchQuery(query, context));
            }
        }
        return queries;
    }

    /**
     * Gọi Serper.dev API (Google Search wrapper).
     * POST https://google.serper.dev/search
     * Header: X-API-KEY: {key}
     * Body: {"q": "...", "num": 5}
     * Response: {"organic": [{"link", "title", "snippet"}]}
     */
    private List<SearchResult> callSerperApi(String query) {
        try {
            // gl=us + hl=en để nhận kết quả tiếng Anh từ Google US (giống search thủ công)
            String body = objectMapper.writeValueAsString(
                    Map.of("q", query, "num", RESULTS_PER_QUERY, "gl", "us", "hl", "en"));

            RestClient client = RestClient.builder().build();
            ResponseEntity<String> resp = client.post()
                    .uri(URI.create(SERPER_URL))
                    .header("X-API-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (resp.getBody() == null) return List.of();
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode organic = root.path("organic");
            if (!organic.isArray()) return List.of();

            List<SearchResult> hits = new ArrayList<>();
            for (JsonNode item : organic) {
                String link = item.path("link").asText("");
                String title = item.path("title").asText("");
                String snippet = item.path("snippet").asText("");
                if (!link.isBlank()) {
                    hits.add(new SearchResult(link, title, snippet));
                    log.info("Plagiarism[web]: Serper result → [{}] {}", title, link);
                }
            }
            return hits;
        } catch (Exception e) {
            log.warn("Plagiarism[web]: Serper API error: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch nội dung trang web → text thuần (tối đa MAX_PAGE_TEXT_CHARS ký tự).
     * Nếu fetch thất bại → dùng snippet từ Bing làm fallback.
     */
    private String fetchPageText(String url, String fallbackSnippet) {
        try {
            org.jsoup.Connection.Response resp = Jsoup.connect(url)
                    .timeout(READ_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .userAgent("Mozilla/5.0 (compatible; PlagiarismBot/1.0)")
                    .execute();

            if (resp.statusCode() >= 400) {
                return fallbackSnippet != null ? fallbackSnippet : "";
            }

            String bodyText = resp.parse().text();
            return bodyText.length() > MAX_PAGE_TEXT_CHARS
                    ? bodyText.substring(0, MAX_PAGE_TEXT_CHARS)
                    : bodyText;
        } catch (Exception e) {
            log.debug("Plagiarism[bing]: cannot fetch {}: {}", url, e.getMessage());
            return fallbackSnippet != null ? fallbackSnippet : "";
        }
    }

    /** Query + đoạn văn context xung quanh để so sánh cosine. */
    private record SearchQuery(String query, String context) {}

    /** DTO nội bộ cho kết quả Bing Search. */
    private record SearchResult(String url, String name, String snippet) {}
}
