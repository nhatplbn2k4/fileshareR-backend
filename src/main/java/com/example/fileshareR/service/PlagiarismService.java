package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.ResolvePlagiarismRequest;
import com.example.fileshareR.dto.response.PlagiarismReportDetailResponse;
import com.example.fileshareR.enums.PlagiarismTriggerType;

public interface PlagiarismService {

    /** Scan 1 document async — lưu evidence rows + gửi notification admin nếu vượt ngưỡng. */
    void checkDocumentAsync(Long documentId, PlagiarismTriggerType trigger, Long triggerContextId);

    /** Scan toàn bộ documents trong 1 folder tree (đệ quy) async. */
    void checkFolderTreeAsync(Long folderId);

    /** Admin xử lý report (KEEP / REMOVE / PRIVATIZE / IGNORE). */
    PlagiarismReportDetailResponse resolveReport(Long suspectedDocId,
                                                 ResolvePlagiarismRequest req,
                                                 Long adminId);
}
