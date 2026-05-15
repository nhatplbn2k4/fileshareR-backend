package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentScope;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.PurchaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusResponse {

    private Long paymentId;
    private String txnRef;
    private PaymentProvider provider;
    private PurchaseType purchaseType;
    private PaymentScope scope;
    private String planCode;
    private String addonCode;
    private Long amountVnd;
    private PaymentStatus status;
    private String providerTxnId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime ipnReceivedAt;
}
