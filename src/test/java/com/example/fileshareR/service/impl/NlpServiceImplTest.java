package com.example.fileshareR.service.impl;

import com.example.fileshareR.service.GeminiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NlpServiceImplTest {

    @Mock private GeminiService geminiService;

    private NlpServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NlpServiceImpl(geminiService);
    }

    // ── extractKeywords (TF-IDF) ────────────────────────────────────────────

    @Test
    void extractKeywords_emptyOrNull_returnsEmpty() {
        assertThat(service.extractKeywords(null, 5)).isEmpty();
        assertThat(service.extractKeywords("", 5)).isEmpty();
        assertThat(service.extractKeywords("   ", 5)).isEmpty();
    }

    @Test
    void extractKeywords_typicalText_returnsRelevantTerms() {
        String text = "Machine learning machine learning deep learning algorithm algorithm algorithm "
                + "computer science neural network artificial intelligence data science";
        List<String> keywords = service.extractKeywords(text, 5);

        assertThat(keywords).hasSize(5);
        // Most frequent + longest are scored highest
        assertThat(keywords).contains("algorithm");
    }

    @Test
    void extractKeywords_filterStopwords() {
        String text = "the quick brown fox jumps over the lazy dog and the cat";
        List<String> keywords = service.extractKeywords(text, 5);

        // Stopwords like 'the', 'and', 'over' must be excluded
        assertThat(keywords).doesNotContain("the", "and");
    }

    // ── calculateTfIdf ──────────────────────────────────────────────────────

    @Test
    void calculateTfIdf_emptyOrNull_returnsEmptyMap() {
        assertThat(service.calculateTfIdf(null)).isEmpty();
        assertThat(service.calculateTfIdf("")).isEmpty();
    }

    @Test
    void calculateTfIdf_uniqueTerms_havePositiveScores() {
        Map<String, Double> tfidf = service.calculateTfIdf(
                "blockchain blockchain finance finance market analysis");

        assertThat(tfidf).isNotEmpty();
        tfidf.values().forEach(score -> assertThat(score).isPositive());
        assertThat(tfidf).containsKey("blockchain");
    }

    @Test
    void calculateTfIdf_longerWords_getLengthBoost() {
        // Word strictly >5 chars gets 20% boost. Compare "longer" (6 chars, boosted)
        // vs "abcde" (5 chars, NOT boosted — boost is `length > 5`).
        Map<String, Double> tfidf = service.calculateTfIdf("longer abcde");

        // Both occur once → TF & IDF identical; only length boost differs.
        assertThat(tfidf.get("longer")).isGreaterThan(tfidf.get("abcde"));
    }

    // ── generateSummary (extractive) ────────────────────────────────────────

    @Test
    void generateSummary_emptyOrNull_returnsEmpty() {
        assertThat(service.generateSummary(null, 3)).isEmpty();
        assertThat(service.generateSummary("", 3)).isEmpty();
    }

    @Test
    void generateSummary_singleSentence_returnsAsIs() {
        String text = "This is a single sentence which has enough content to score.";
        String summary = service.generateSummary(text, 3);

        assertThat(summary).contains("This is a single sentence");
    }

    @Test
    void generateSummary_multipleSentences_selectsTopByTfIdf() {
        String text = "Machine learning algorithm is powerful. "
                + "The cat sat. "
                + "Deep learning neural network classifier excels. "
                + "Data science pipeline processing analyzes data."; // 4 sentences
        String summary = service.generateSummary(text, 2);

        // Skips "The cat sat" — too short (<10 chars trimmed actually 11; but lower TF-IDF anyway)
        assertThat(summary).isNotEmpty();
        assertThat(summary).endsWith(".");
    }

    @Test
    void generateSummary_atOrUnderMaxSentences_returnsFullText() {
        String text = "First sentence here. Second sentence here.";
        // Only 2 sentences ≤ maxSentences=3 → return text.trim()
        assertThat(service.generateSummary(text, 3)).isEqualTo(text.trim());
    }

    // ── cosineSimilarity ────────────────────────────────────────────────────

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        Map<String, Double> v = Map.of("a", 1.0, "b", 1.0);

        assertThat(service.cosineSimilarity(v, v)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        Map<String, Double> v1 = Map.of("a", 1.0);
        Map<String, Double> v2 = Map.of("b", 1.0);

        assertThat(service.cosineSimilarity(v1, v2)).isZero();
    }

    @Test
    void cosineSimilarity_partialOverlap_betweenZeroAndOne() {
        Map<String, Double> v1 = Map.of("a", 1.0, "b", 1.0);
        Map<String, Double> v2 = Map.of("a", 1.0, "c", 1.0);

        double sim = service.cosineSimilarity(v1, v2);
        assertThat(sim).isStrictlyBetween(0.0, 1.0);
        assertThat(sim).isCloseTo(0.5, within(0.01));
    }

    @Test
    void cosineSimilarity_emptyOrNullVector_returnsZero() {
        assertThat(service.cosineSimilarity(null, Map.of("a", 1.0))).isZero();
        assertThat(service.cosineSimilarity(Map.of("a", 1.0), null)).isZero();
        assertThat(service.cosineSimilarity(Map.of(), Map.of("a", 1.0))).isZero();
        assertThat(service.cosineSimilarity(Map.of("a", 1.0), Map.of())).isZero();
    }

    @Test
    void cosineSimilarity_zeroMagnitudeVector_returnsZero() {
        Map<String, Double> v1 = Map.of("a", 0.0);
        Map<String, Double> v2 = Map.of("a", 1.0);

        assertThat(service.cosineSimilarity(v1, v2)).isZero();
    }

    // ── extractKeywordsWithAI ───────────────────────────────────────────────

    @Test
    void extractKeywordsWithAI_emptyOrNull_returnsEmpty() {
        assertThat(service.extractKeywordsWithAI(null, 5)).isEmpty();
        assertThat(service.extractKeywordsWithAI("", 5)).isEmpty();
    }

    @Test
    void extractKeywordsWithAI_aiReturnsKeywords_usesAI() {
        when(geminiService.extractKeywords(anyString(), anyInt()))
                .thenReturn(List.of("blockchain", "finance", "ai"));

        List<String> out = service.extractKeywordsWithAI("Some text about blockchain finance", 5);

        assertThat(out).containsExactly("blockchain", "finance", "ai");
    }

    @Test
    void extractKeywordsWithAI_aiReturnsEmpty_fallsBackToTfIdf() {
        when(geminiService.extractKeywords(anyString(), anyInt())).thenReturn(Collections.emptyList());

        List<String> out = service.extractKeywordsWithAI(
                "blockchain blockchain finance finance market analysis", 3);

        assertThat(out).isNotEmpty();
        // Verify AI was called first
        verify(geminiService).extractKeywords(anyString(), anyInt());
    }

    // ── generateSummaryWithAI ───────────────────────────────────────────────

    @Test
    void generateSummaryWithAI_emptyOrNull_returnsEmpty() {
        assertThat(service.generateSummaryWithAI(null, 100)).isEmpty();
        assertThat(service.generateSummaryWithAI("", 100)).isEmpty();
    }

    @Test
    void generateSummaryWithAI_aiReturnsSummary_usesAI() {
        when(geminiService.summarize(anyString(), anyInt())).thenReturn("AI-generated summary text");

        String out = service.generateSummaryWithAI("Long input text content", 50);

        assertThat(out).isEqualTo("AI-generated summary text");
    }

    @Test
    void generateSummaryWithAI_aiReturnsNull_fallsBackToExtractive() {
        when(geminiService.summarize(anyString(), anyInt())).thenReturn(null);

        String out = service.generateSummaryWithAI("First sentence. Second sentence. Third sentence here.", 50);

        assertThat(out).isNotEmpty();
    }

    @Test
    void generateSummaryWithAI_aiReturnsBlank_fallsBackToExtractive() {
        when(geminiService.summarize(anyString(), anyInt())).thenReturn("   ");

        String out = service.generateSummaryWithAI("First sentence with content. Second sentence here also has content.", 50);

        assertThat(out).isNotBlank();
    }
}
