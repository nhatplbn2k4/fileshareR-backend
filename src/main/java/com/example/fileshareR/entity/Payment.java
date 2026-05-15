package com.example.fileshareR.entity;

import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentScope;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.PurchaseType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_txn_ref", columnList = "txn_ref", unique = true),
                @Index(name = "idx_payments_user_status", columnList = "user_id,status"),
                @Index(name = "idx_payments_provider_provider_txn", columnList = "provider,provider_txn_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, length = 20)
    private PurchaseType purchaseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentScope scope;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Column(name = "addon_code", length = 50)
    private String addonCode;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "txn_ref", nullable = false, length = 50)
    private String txnRef;

    @Column(name = "provider_txn_id", length = 255)
    private String providerTxnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "return_url", length = 500)
    private String returnUrl;

    @Column(name = "ipn_received_at")
    private LocalDateTime ipnReceivedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
