package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.PlagiarismStatus;
import com.example.fileshareR.enums.PlagiarismTriggerType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlagiarismReportDetailResponse {
    private Long suspectedDocumentId;
    private String suspectedTitle;
    private com.example.fileshareR.enums.FileType suspectedFileType;
    private String suspectedFileName;
    private String suspectedOwnerEmail;
    private Integer suspectedOwnerWarningCount;
    private String suspectedSnippet;
    private PlagiarismStatus status;
    private PlagiarismTriggerType triggerType;
    private Long triggerContextId;
    private Float maxScore;
    private LocalDateTime firstDetectedAt;
    private LocalDateTime resolvedAt;
    private String resolverEmail;
    private String resolutionNote;
    private List<PlagiarismMatchResponse> matches;
}
