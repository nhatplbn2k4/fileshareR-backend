package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResponse {
    private Long documentId;
    private String title;

    // Summary của document
    private String summary;

    // Danh sách từ khóa theo TF-IDF
    private List<String> keywords;

    // Top terms với điểm TF-IDF
    private List<KeywordScore> topTerms;

    // Thống kê cơ bản
    private DocumentStats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordScore {
        private String term;
        private Double score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStats {
        private Integer totalWords;
        private Integer uniqueWords;
        private Integer sentenceCount;
        private Integer characterCount;
    }
}
