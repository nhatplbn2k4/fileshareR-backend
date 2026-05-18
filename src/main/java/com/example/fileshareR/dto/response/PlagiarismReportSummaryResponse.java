package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.enums.PlagiarismTriggerType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Một row trong list "danh sách report" cho admin.
 * Một report = 1 suspected doc + nhiều matches (xem detail để xem matches).
 */
@Data
@Builder
public class PlagiarismReportSummaryResponse {
    private Long suspectedDocumentId;
    private String suspectedTitle;
    private String suspectedOwnerEmail;
    private PlagiarismStatus status;
    private PlagiarismTriggerType triggerType;
    private Long triggerContextId;
    private Float maxScore;
    private Long matchCount;
    private LocalDateTime firstDetectedAt;
}
