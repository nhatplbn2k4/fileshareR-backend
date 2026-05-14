package com.example.fileshareR.service;

import com.example.fileshareR.enums.ModerationStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Kiểm duyệt tự động nội dung văn bản (NSFW, ngôn từ thù địch, bạo lực...).
 * Pipeline: keyword blacklist → OpenAI Moderation API (nếu cấu hình).
 */
public interface ContentModerationService {

    /**
     * Đánh giá đoạn text. Trả về APPROVED nếu sạch, PENDING nếu nghi vấn.
     * Engine không tự REJECT — quyết định REJECT là của admin nhóm.
     *
     * @param text nội dung đã trích xuất từ file (có thể null/blank)
     * @return ModerationResult (status + reason + score)
     */
    ModerationResult moderate(String text);

    @Getter
    @Builder
    class ModerationResult {
        /** APPROVED (sạch) hoặc PENDING (nghi vấn) — engine không bao giờ tự REJECT */
        private final ModerationStatus status;
        /** Lý do flag (keyword bị bắt / category OpenAI). Null nếu APPROVED. */
        private final String reason;
        /** Score nguy cơ 0.0–1.0. Null nếu không qua AI. */
        private final Double score;

        public static ModerationResult approved() {
            return ModerationResult.builder()
                    .status(ModerationStatus.APPROVED)
                    .build();
        }

        public static ModerationResult flagged(String reason, Double score) {
            return ModerationResult.builder()
                    .status(ModerationStatus.PENDING)
                    .reason(reason)
                    .score(score)
                    .build();
        }
    }
}
