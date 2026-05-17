package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentSummary {
    private Long id;
    private String txnRef;
    private String providerTxnId;
    private String provider;
    private String purchaseType;
    private String scope;
    private String planCode;
    private String addonCode;
    private Long amountVnd;
    private String status;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime ipnReceivedAt;

    private Long userId;
    private String userEmail;
    private String userFullName;
    private Long groupId;
}
