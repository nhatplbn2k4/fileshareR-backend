package com.example.fileshareR.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlagiarismMatchResponse {
    private Long matchedDocumentId;
    private String matchedTitle;
    private String matchedOwnerEmail;
    private Float similarityScore;
    private String snippet;
    /** Chỉ có giá trị khi match từ internet (document2 = null). */
    private String externalUrl;
}
