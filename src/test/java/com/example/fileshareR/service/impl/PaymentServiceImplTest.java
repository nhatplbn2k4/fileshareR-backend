package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentScope;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.PurchaseType;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PaymentRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.StorageAddonRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.BillingService;
import com.example.fileshareR.service.payment.PaymentProviderStrategy;
import com.example.fileshareR.service.payment.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private StorageAddonRepository addonRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private BillingService billingService;
    @Mock private PaymentProviderStrategy vnpayStrategy;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        when(vnpayStrategy.getProvider()).thenReturn(PaymentProvider.VNPAY);
        when(vnpayStrategy.isConfigured()).thenReturn(true);
        service = new PaymentServiceImpl(
                paymentRepository, userRepository, planRepository,
                addonRepository, groupRepository, billingService,
                List.of(vnpayStrategy));
    }

    @Test
    void handleIpn_appliesPurchaseOnce_andIsIdempotentOnReplay() {
        Payment payment = pendingUserPlanPayment("TXN1", 100_000L);
        when(paymentRepository.findByTxnRef("TXN1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.builder()
                .success(true).signatureValid(true)
                .txnRef("TXN1").providerTxnId("VNP-99")
                .amountVnd(100_000L).responseCode("00").build());

        service.handleIpn(PaymentProvider.VNPAY, Map.of("vnp_TxnRef", "TXN1"));

        verify(billingService, times(1)).purchaseUserPlan(1L, "PREMIUM");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Replay: same IPN params arrive again — provider doesn't always deliver once-exactly.
        // Payment is now SUCCESS, so applyPurchase must NOT be called again.
        service.handleIpn(PaymentProvider.VNPAY, Map.of("vnp_TxnRef", "TXN1"));
        verify(billingService, times(1)).purchaseUserPlan(1L, "PREMIUM");
    }

    @Test
    void handleIpn_amountMismatchFailsPaymentWithoutApplying() {
        Payment payment = pendingUserPlanPayment("TXN2", 100_000L);
        when(paymentRepository.findByTxnRef("TXN2")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        // Provider reports a DIFFERENT amount than what we recorded — replay attack defense.
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.builder()
                .success(true).signatureValid(true)
                .txnRef("TXN2").amountVnd(50_000L).build());

        service.handleIpn(PaymentProvider.VNPAY, Map.of("vnp_TxnRef", "TXN2"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(billingService, never()).purchaseUserPlan(any(), any());
    }

    @Test
    void handleIpn_invalidSignatureThrows() {
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.signatureMismatch());

        assertThatThrownBy(() -> service.handleIpn(PaymentProvider.VNPAY, Map.of()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_SIGNATURE_INVALID);
        verify(billingService, never()).purchaseUserPlan(any(), any());
    }

    @Test
    void handleIpn_unknownTxnRefThrows() {
        when(paymentRepository.findByTxnRef("MISSING")).thenReturn(Optional.empty());
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.builder()
                .success(true).signatureValid(true).txnRef("MISSING").build());

        assertThatThrownBy(() -> service.handleIpn(PaymentProvider.VNPAY, Map.of()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void handleIpn_providerReportedFailureMarksFailed_doesNotApply() {
        Payment payment = pendingUserPlanPayment("TXN3", 100_000L);
        when(paymentRepository.findByTxnRef("TXN3")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.builder()
                .success(false).signatureValid(true)
                .txnRef("TXN3").responseCode("24").message("User cancelled").build());

        service.handleIpn(PaymentProvider.VNPAY, Map.of());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(billingService, never()).purchaseUserPlan(any(), any());
    }

    @Test
    void handleReturn_neverAppliesPurchase() {
        // Even if signature + result code say success, browser return must not apply —
        // IPN owns that to survive cases where user closes browser mid-redirect.
        Payment payment = pendingUserPlanPayment("TXN4", 100_000L);
        when(paymentRepository.findByTxnRef("TXN4")).thenReturn(Optional.of(payment));
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.builder()
                .success(true).signatureValid(true).txnRef("TXN4").build());

        service.handleReturn(PaymentProvider.VNPAY, Map.of());

        verify(billingService, never()).purchaseUserPlan(any(), any());
        // Status untouched — handleReturn is read-only
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Payment pendingUserPlanPayment(String txnRef, long amount) {
        return Payment.builder()
                .id(1L)
                .user(User.builder().id(1L).email("test@x.com").build())
                .provider(PaymentProvider.VNPAY)
                .purchaseType(PurchaseType.PLAN)
                .scope(PaymentScope.USER)
                .planCode("PREMIUM")
                .amountVnd(amount)
                .txnRef(txnRef)
                .status(PaymentStatus.PENDING)
                .build();
    }
}
