package com.example.fileshareR.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeminiServiceImpl constructs its own {@code RestTemplate} so the live HTTP
 * call cannot be mocked without refactoring. We exercise the input-guard +
 * api-key-missing branches plus parseGeminiResponse via reflection on the
 * private helper.
 */
class GeminiServiceImplTest {

    private GeminiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GeminiServiceImpl(new ObjectMapper());
        // Default: no API key configured → generateContent short-circuits
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "apiUrl",
                "https://generativelanguage.googleapis.com/v1beta/models");
        ReflectionTestUtils.setField(service, "model", "gemini-1.5-flash");
    }

    // ── summarize ───────────────────────────────────────────────────────────

    @Test
    void summarize_emptyText_returnsEmpty() {
        assertThat(service.summarize(null, 50)).isEmpty();
        assertThat(service.summarize("", 50)).isEmpty();
        assertThat(service.summarize("   ", 50)).isEmpty();
    }

    @Test
    void summarize_noApiKey_returnsEmpty() {
        // generateContent returns null when apiKey blank → summarize returns ""
        assertThat(service.summarize("any text", 50)).isEmpty();
    }

    @Test
    void summarize_textOver10000Chars_truncatedAndStillReturnsEmptyWithoutApiKey() {
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
    void extractKeywords_noApiKey_returnsEmptyList() {
        assertThat(service.extractKeywords("some text", 5)).isEmpty();
    }

    // ── generateContent ─────────────────────────────────────────────────────

    @Test
    void generateContent_blankApiKey_returnsNull() {
        assertThat(service.generateContent("test prompt")).isNull();
    }

    @Test
    void generateContent_nullApiKey_returnsNull() {
        ReflectionTestUtils.setField(service, "apiKey", null);

        assertThat(service.generateContent("test prompt")).isNull();
    }

    @Test
    void parseGeminiResponse_validResponse_returnsText() throws Exception {
        // Invoke private parseGeminiResponse via reflection
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello world\"}]}}]}";
        java.lang.reflect.Method m = GeminiServiceImpl.class.getDeclaredMethod(
                "parseGeminiResponse", String.class);
        m.setAccessible(true);

        Object result = m.invoke(service, json);

        assertThat(result).isEqualTo("Hello world");
    }

    @Test
    void parseGeminiResponse_emptyCandidates_returnsNull() throws Exception {
        String json = "{\"candidates\":[]}";
        java.lang.reflect.Method m = GeminiServiceImpl.class.getDeclaredMethod(
                "parseGeminiResponse", String.class);
        m.setAccessible(true);

        Object result = m.invoke(service, json);

        assertThat(result).isNull();
    }

    @Test
    void parseGeminiResponse_malformedJson_returnsNull() throws Exception {
        java.lang.reflect.Method m = GeminiServiceImpl.class.getDeclaredMethod(
                "parseGeminiResponse", String.class);
        m.setAccessible(true);

        Object result = m.invoke(service, "not json");

        assertThat(result).isNull();
    }

    @Test
    void parseGeminiResponse_missingParts_returnsNull() throws Exception {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[]}}]}";
        java.lang.reflect.Method m = GeminiServiceImpl.class.getDeclaredMethod(
                "parseGeminiResponse", String.class);
        m.setAccessible(true);

        Object result = m.invoke(service, json);

        assertThat(result).isNull();
    }

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

    @Test
    void generateContent_invalidApiKey_swallowsExceptionAndReturnsNull() {
        // Set a fake key — RestTemplate will hit real network and likely error
        // (timeout / 403 / DNS); the catch-Exception path returns null.
        ReflectionTestUtils.setField(service, "apiKey", "FAKEKEYABCDEFGHIJKLMNOPQRSTUV");
        ReflectionTestUtils.setField(service, "apiUrl",
                "http://localhost:1/nonexistent-endpoint-for-test"); // localhost:1 = refused

        assertThat(service.generateContent("hi")).isNull();
    }
}
