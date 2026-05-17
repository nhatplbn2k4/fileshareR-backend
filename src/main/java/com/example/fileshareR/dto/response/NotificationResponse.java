package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Frontend-visible notification payload — used both by REST endpoints
 * and STOMP push payloads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private Long referenceId;
    private String link;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
