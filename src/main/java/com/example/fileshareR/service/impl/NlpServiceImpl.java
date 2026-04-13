package com.example.fileshareR.service.impl;

import com.example.fileshareR.service.GeminiService;
import com.example.fileshareR.service.NlpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NlpServiceImpl implements NlpService {

    private final GeminiService geminiService;

    // Vietnamese stopwords
    private static final Set<String> STOPWORDS = Set.of(
            // Vietnamese common stopwords
            "và", "của", "là", "có", "trong", "được", "cho", "với", "các", "một",
            "này", "đã", "để", "từ", "khi", "như", "về", "theo", "đến", "không",
            "những", "hay", "hoặc", "tại", "bởi", "vì", "nên", "cũng", "còn", "thì",
            "mà", "nhưng", "đó", "nếu", "trên", "dưới", "ra", "vào", "lên", "xuống",
            "sau", "trước", "giữa", "bên", "qua", "lại", "rồi", "sẽ", "đang", "vẫn",
            "rất", "nhiều", "ít", "hơn", "nhất", "mọi", "tất", "cả", "chỉ", "ai",
            "gì", "nào", "sao", "đâu", "bao", "mấy", "làm", "biết", "thấy", "nói",
            "người", "việc", "điều", "cách", "năm", "ngày", "lần", "phần", "chung",
            // English common stopwords
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "this", "that",
            "these", "those", "it", "its", "he", "she", "they", "we", "you", "i",
            "what", "which", "who", "whom", "where", "when", "why", "how", "all",
            "each", "every", "both", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very");

    // Minimum word length to consider
    private static final int MIN_WORD_LENGTH = 2;

    @Override
    public List<String> extractKeywords(String text, int topN) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        log.info("Extracting top {} keywords from text of {} characters", topN, text.length());

        Map<String, Double> tfidf = calculateTfIdf(text);

        List<String> keywords = tfidf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("Extracted {} keywords: {}", keywords.size(), keywords);
        return keywords;
    }

    @Override
    public Map<String, Double> calculateTfIdf(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }

        // Tokenize and normalize
        List<String> tokens = tokenize(text);

        if (tokens.isEmpty()) {
            return Collections.emptyMap();
        }

        // Calculate Term Frequency (TF)
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : tokens) {
            termFrequency.merge(token, 1, Integer::sum);
        }

        int totalTerms = tokens.size();

        // Calculate TF-IDF
        // For single document, we use augmented TF and simple IDF based on term
        // distribution
        Map<String, Double> tfidf = new HashMap<>();
        int maxFreq = termFrequency.values().stream().max(Integer::compare).orElse(1);

        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int freq = entry.getValue();

            // Augmented Term Frequency: 0.5 + 0.5 * (freq / maxFreq)
            double tf = 0.5 + 0.5 * ((double) freq / maxFreq);

            // IDF approximation based on term frequency (rarer terms get higher weight)
            // Using log(totalTerms / freq) as approximation
            double idf = Math.log((double) totalTerms / freq) + 1;

            // Boost for longer words (more specific terms)
            double lengthBoost = 1 + (term.length() > 5 ? 0.2 : 0);

            tfidf.put(term, tf * idf * lengthBoost);
        }

        return tfidf;
    }

    @Override
    public String generateSummary(String text, int maxSentences) {
        if (text == null || text.isBlank()) {
            return "";
        }

        log.info("Generating summary with max {} sentences from text of {} characters",
                maxSentences, text.length());

        // Split into sentences
        String[] sentences = text.split("[.!?]+\\s*");

        if (sentences.length == 0) {
            return "";
        }

        if (sentences.length <= maxSentences) {
            return text.trim();
        }

        // Calculate TF-IDF for the entire document
        Map<String, Double> docTfidf = calculateTfIdf(text);

        // Score each sentence based on TF-IDF of its terms
        List<Map.Entry<String, Double>> sentenceScores = new ArrayList<>();

        for (String sentence : sentences) {
            if (sentence.trim().length() < 10)
                continue; // Skip very short sentences

            List<String> tokens = tokenize(sentence);
            double score = 0;

            for (String token : tokens) {
                score += docTfidf.getOrDefault(token, 0.0);
            }

            // Normalize by sentence length to avoid bias toward longer sentences
            if (!tokens.isEmpty()) {
                score = score / Math.sqrt(tokens.size());
            }

            // Boost first sentences (usually contain important info)
            int sentenceIndex = Arrays.asList(sentences).indexOf(sentence);
            if (sentenceIndex < 3) {
                score *= 1.2;
            }

            sentenceScores.add(Map.entry(sentence.trim(), score));
        }

        // Select top sentences while maintaining original order
        Set<String> topSentences = sentenceScores.stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxSentences)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Build summary maintaining original sentence order
        StringBuilder summary = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (topSentences.contains(trimmed)) {
                if (summary.length() > 0) {
                    summary.append(". ");
                }
                summary.append(trimmed);
            }
        }

        if (summary.length() > 0 && !summary.toString().endsWith(".")) {
            summary.append(".");
        }

        String result = summary.toString();
        log.info("Generated summary of {} characters", result.length());
        return result;
    }

    @Override
    public double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        if (vector1 == null || vector2 == null || vector1.isEmpty() || vector2.isEmpty()) {
            return 0.0;
        }

        // Get all unique terms
        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(vector1.keySet());
        allTerms.addAll(vector2.keySet());

        // Calculate dot product and magnitudes
        double dotProduct = 0;
        double magnitude1 = 0;
        double magnitude2 = 0;

        for (String term : allTerms) {
            double v1 = vector1.getOrDefault(term, 0.0);
            double v2 = vector2.getOrDefault(term, 0.0);

            dotProduct += v1 * v2;
            magnitude1 += v1 * v1;
            magnitude2 += v2 * v2;
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        if (magnitude1 == 0 || magnitude2 == 0) {
            return 0.0;
        }

        return dotProduct / (magnitude1 * magnitude2);
    }

    @Override
    public List<String> extractKeywordsWithAI(String text, int topN) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        log.info("=== NLP: Attempting AI keyword extraction ===");

        List<String> keywords = geminiService.extractKeywords(text, topN);

        if (keywords.isEmpty()) {
            log.warn("=== NLP: AI failed, FALLBACK to TF-IDF ===");
            return extractKeywords(text, topN);
        }

        log.info("=== NLP: AI SUCCESS === Keywords: {}", keywords);
        return keywords;
    }

    @Override
    public String generateSummaryWithAI(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "";
        }

        log.info("=== NLP: Attempting AI summarization ===");

        String summary = geminiService.summarize(text, maxWords);

        if (summary == null || summary.isBlank()) {
            log.warn("=== NLP: AI failed, FALLBACK to extractive TF-IDF ===");
            return generateSummary(text, 3);
        }

        log.info("=== NLP: AI SUCCESS === Summary: {}", summary.substring(0, Math.min(100, summary.length())) + "...");
        return summary;
    }

    /**
     * Tokenize text: normalize, remove punctuation, split into words, filter
     * stopwords
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        // Normalize Vietnamese text (decompose -> remove diacritics for comparison is
        // NOT what we want)
        // We want to keep Vietnamese characters but normalize form
        String normalized = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFC);

        // Remove punctuation and special characters, keep letters (including
        // Vietnamese) and numbers
        String cleaned = normalized.replaceAll("[^\\p{L}\\p{N}\\s]", " ");

        // Split into words
        String[] words = cleaned.split("\\s+");

        // Filter: remove stopwords, short words, and numbers-only
        Pattern numbersOnly = Pattern.compile("^\\d+$");

        return Arrays.stream(words)
                .map(String::trim)
                .filter(word -> !word.isBlank())
                .filter(word -> word.length() >= MIN_WORD_LENGTH)
                .filter(word -> !STOPWORDS.contains(word))
                .filter(word -> !numbersOnly.matcher(word).matches())
                .collect(Collectors.toList());
    }
}
