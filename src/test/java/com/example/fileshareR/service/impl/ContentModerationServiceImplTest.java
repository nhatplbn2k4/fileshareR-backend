package com.example.fileshareR.service.impl;

import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.service.ContentModerationService.ModerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContentModerationService two-stage logic. We test stage 1 (offline keyword
 * blacklist) and the no-api-key short-circuit path. Stage 2 (OpenAI HTTP call)
 * isn't covered here because the RestClient is built inline; integration test
 * would require WireMock.
 */
class ContentModerationServiceImplTest {

    private ContentModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContentModerationServiceImpl(new ObjectMapper());
        // Provide an in-memory blacklist; bypass loadBlacklist() PostConstruct.
        ReflectionTestUtils.setField(service, "blacklist",
                List.of("ma tuy", "khung bo", "dit"));
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.openai.com/v1/moderations");
        ReflectionTestUtils.setField(service, "model", "omni-moderation-latest");
        ReflectionTestUtils.setField(service, "threshold", 0.5);
        ReflectionTestUtils.setField(service, "maxChars", 8000);
    }

    @Test
    void moderate_nullOrBlank_returnsApproved() {
        assertThat(service.moderate(null).getStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(service.moderate("").getStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(service.moderate("   ").getStatus()).isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    void moderate_cleanText_returnsApproved() {
        ModerationResult r = service.moderate("Đây là văn bản hoàn toàn vô hại về toán học và lập trình.");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    void moderate_keywordHit_returnsPending() {
        // "ma túy" normalises to "ma tuy" → matches blacklist
        ModerationResult r = service.moderate("Bài viết về buôn bán ma túy và hậu quả");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(r.getScore()).isEqualTo(1.0);
        assertThat(r.getReason()).contains("ma tuy");
    }

    @Test
    void moderate_diacriticInsensitive_stillMatches() {
        // 'khủng bố' → normalised 'khung bo' matches blacklist entry
        ModerationResult r = service.moderate("hành vi khủng bố bị nghiêm cấm");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    void moderate_uppercaseInputMatchesLowercaseKeyword() {
        ModerationResult r = service.moderate("MA TÚY là chất cấm");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    void moderate_dCharacterNormalised() {
        // "đít" (with đ) → normalised contains "dit" → matches blacklist entry
        ModerationResult r = service.moderate("nội dung chứa đít");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    void moderate_noApiKey_skipsStage2_returnsApproved() {
        // No blacklist hit + no API key → APPROVED short-circuit (no HTTP call)
        ReflectionTestUtils.setField(service, "blacklist", List.of("xxx"));

        ModerationResult r = service.moderate("Hello world, nothing suspicious here.");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    void moderate_emptyBlacklist_andNoApiKey_returnsApproved() {
        ReflectionTestUtils.setField(service, "blacklist", List.of());

        assertThat(service.moderate("text without any keyword").getStatus())
                .isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    void moderate_apiKeySetButEndpointUnreachable_failOpen() {
        // Stage 2 exception → caught → fail-open APPROVED
        ReflectionTestUtils.setField(service, "apiKey", "FAKE");
        ReflectionTestUtils.setField(service, "apiUrl", "http://localhost:1/no-such-endpoint");

        ModerationResult r = service.moderate("Some text with no banned keywords");

        assertThat(r.getStatus()).isEqualTo(ModerationStatus.APPROVED);
    }
}
