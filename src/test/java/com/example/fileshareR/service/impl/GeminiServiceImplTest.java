package com.example.fileshareR.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * After migrating GeminiServiceImpl to Vertex AI SDK, the service uses
 * {@code VertexAI} + {@code GenerativeModel} clients that auth via ADC
 * (service account JSON) and cannot be easily mocked (final classes from
 * Google Cloud SDK). Unit tests therefore exercise:
 * <ul>
 *   <li>Input-guard branches (null/blank text returns empty)</li>
 *   <li>No-init guard (project-id blank → generativeModel null → no-op)</li>
 *   <li>Helper truncateText via reflection</li>
 * </ul>
 * Integration with the real Vertex AI endpoint is covered by manual smoke
 * test post-deploy (per release-deploy-standard) — needs live GCP credentials.
 */
class GeminiServiceImplTest {

    private GeminiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GeminiServiceImpl();
        // Default: no project configured → generativeModel stays null
        ReflectionTestUtils.setField(service, "projectId", "");
        ReflectionTestUtils.setField(service, "location", "asia-southeast1");
        ReflectionTestUtils.setField(service, "model", "gemini-2.0-flash");
        // init() not called here (not a Spring context) → generativeModel null
    }

    // ── summarize ───────────────────────────────────────────────────────────

    @Test
    void summarize_emptyText_returnsEmpty() {
        assertThat(service.summarize(null, 50)).isEmpty();
        assertThat(service.summarize("", 50)).isEmpty();
        assertThat(service.summarize("   ", 50)).isEmpty();
    }

    @Test
    void summarize_noVertexInit_returnsEmpty() {
        // generateContent returns null when generativeModel uninitialized → summarize returns ""
        assertThat(service.summarize("any text", 50)).isEmpty();
    }

    @Test
    void summarize_textOver10000Chars_truncatedAndStillReturnsEmptyWithoutInit() {
        String huge = "x".repeat(15000);

        assertThat(service.summarize(huge, 100)).isEmpty();
    }

    // ── extractKeywords ─────────────────────────────────────────────────────

    @Test
    void extractKeywords_emptyText_returnsEmptyList() {
        assertThat(service.extractKeywords(null, 5)).isEmpty();
        assertThat(service.extractKeywords("", 5)).isEmpty();
        assertThat(service.extractKeywords("   ", 5)).isEmpty();
    }

    @Test
    void extractKeywords_noVertexInit_returnsEmptyList() {
        assertThat(service.extractKeywords("some text", 5)).isEmpty();
    }

    // ── generateContent ─────────────────────────────────────────────────────

    @Test
    void generateContent_noVertexInit_returnsNull() {
        assertThat(service.generateContent("test prompt")).isNull();
    }

    // ── truncateText helper ─────────────────────────────────────────────────

    @Test
    void truncateText_underMaxChars_returnsAsIs() throws Exception {
        java.lang.reflect.Method m = GeminiServiceImpl.class.getDeclaredMethod(
                "truncateText", String.class, int.class);
        m.setAccessible(true);

        Object result = m.invoke(service, "short", 100);

        assertThat(result).isEqualTo("short");
    }

    @Test
    void truncateText_overMaxChars_appendsEllipsis() throws Exception {
        java.lang.reflect.Method m = GeminiServiceImpl.class.getDeclaredMethod(
                "truncateText", String.class, int.class);
        m.setAccessible(true);

        Object result = m.invoke(service, "long string here", 4);

        assertThat(result).isEqualTo("long...");
    }
}
