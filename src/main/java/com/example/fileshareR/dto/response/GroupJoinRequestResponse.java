package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.JoinRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupJoinRequestResponse {
    private Long id;
    private Long groupId;
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private List<String> answers;
    private List<String> questions; // câu hỏi tại thời điểm gửi request
    private JoinRequestStatus status;
    private LocalDateTime createdAt;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
}
