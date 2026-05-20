package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.InitiatePaymentRequest;
import com.example.fileshareR.dto.response.InitiatePaymentResponse;
import com.example.fileshareR.dto.response.PaymentStatusResponse;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
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
import com.example.fileshareR.service.NotificationService;
import com.example.fileshareR.service.payment.PaymentProviderStrategy;
import com.example.fileshareR.service.payment.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplExtraTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private StorageAddonRepository addonRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private BillingService billingService;
    @Mock private NotificationService notificationService;
    @Mock private PaymentProviderStrategy vnpayStrategy;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(vnpayStrategy.getProvider()).thenReturn(PaymentProvider.VNPAY);
        org.mockito.Mockito.lenient().when(vnpayStrategy.isConfigured()).thenReturn(true);
        service = new PaymentServiceImpl(paymentRepository, userRepository, planRepository,
                addonRepository, groupRepository, billingService, notificationService,
                List.of(vnpayStrategy));
    }

    // ── initiate ────────────────────────────────────────────────────────────

    @Test
    void initiate_userMissing_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.USER);
        req.setPlanCode("PREMIUM");

        assertThatThrownBy(() -> service.initiate(99L, req, "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void initiate_planRequest_missingCode_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.USER);
        // no planCode set

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_REQUIRED);
    }

    @Test
    void initiate_addonRequest_missingCode_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.ADDON);
        req.setScope(PaymentScope.USER);
        // no addonCode

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_REQUIRED);
    }

    @Test
    void initiate_groupScope_missingGroupId_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.GROUP);
        req.setPlanCode("PRO");
        // no groupId

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GROUP_ID_REQUIRED);
    }

    @Test
    void initiate_zeroAmount_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(
                Plan.builder().code("FREE").priceVnd(0L).build()));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.USER);
        req.setPlanCode("FREE");

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_INVALID);
    }

    @Test
    void initiate_groupScope_notOwner_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(
                Plan.builder().code("PRO").priceVnd(100_000L).build()));
        Group group = Group.builder().id(20L).owner(User.builder().id(99L).build()).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.GROUP);
        req.setPlanCode("PRO");
        req.setGroupId(20L);

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void initiate_userPlan_happy_persistsPaymentAndReturnsRedirect() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(planRepository.findByCode("PREMIUM")).thenReturn(Optional.of(
                Plan.builder().code("PREMIUM").priceVnd(100_000L).build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(vnpayStrategy.createPaymentUrl(any(Payment.class), anyString()))
                .thenReturn("https://vnpay/pay?txn=XYZ");

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.USER);
        req.setPlanCode("PREMIUM");

        InitiatePaymentResponse out = service.initiate(1L, req, "127.0.0.1");

        assertThat(out.getRedirectUrl()).startsWith("https://vnpay/pay");
        assertThat(out.getAmountVnd()).isEqualTo(100_000L);
    }

    @Test
    void initiate_strategyThrows_marksPaymentFailedAndWraps() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(planRepository.findByCode("PREMIUM")).thenReturn(Optional.of(
                Plan.builder().code("PREMIUM").priceVnd(100_000L).build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vnpayStrategy.createPaymentUrl(any(Payment.class), anyString()))
                .thenThrow(new RuntimeException("provider down"));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.USER);
        req.setPlanCode("PREMIUM");

        assertThatThrownBy(() -> service.initiate(1L, req, "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_INITIATE_FAILED);
    }

    @Test
    void initiate_addonHappy_resolvesPriceFromAddonRepo() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(addonRepository.findByCode("X10")).thenReturn(Optional.of(
                StorageAddon.builder().code("X10").priceVnd(50_000L).build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vnpayStrategy.createPaymentUrl(any(Payment.class), anyString())).thenReturn("ok");

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.ADDON);
        req.setScope(PaymentScope.USER);
        req.setAddonCode("X10");

        InitiatePaymentResponse out = service.initiate(1L, req, "ip");

        assertThat(out.getAmountVnd()).isEqualTo(50_000L);
    }

    @Test
    void initiate_addonMissing_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(addonRepository.findByCode("MISS")).thenReturn(Optional.empty());

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.ADDON);
        req.setScope(PaymentScope.USER);
        req.setAddonCode("MISS");

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDON_NOT_FOUND);
    }

    @Test
    void initiate_unconfiguredProvider_throws() {
        when(vnpayStrategy.isConfigured()).thenReturn(false);
        // Need to rebuild service after changing strategy state
        service = new PaymentServiceImpl(paymentRepository, userRepository, planRepository,
                addonRepository, groupRepository, billingService, notificationService,
                List.of(vnpayStrategy));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setProvider(PaymentProvider.VNPAY);
        req.setPurchaseType(PurchaseType.PLAN);
        req.setScope(PaymentScope.USER);
        req.setPlanCode("PRO");

        assertThatThrownBy(() -> service.initiate(1L, req, "ip"))
                .isInstanceOf(CustomException.class);
    }

    // ── handleIpn additional branches ──────────────────────────────────────

    @Test
    void handleIpn_invalidSignature_throws() {
        when(vnpayStrategy.verifyCallback(any())).thenReturn(
                VerificationResult.builder().signatureValid(false).build());

        assertThatThrownBy(() -> service.handleIpn(PaymentProvider.VNPAY, java.util.Map.of()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SIGNATURE_INVALID);
    }

    @Test
    void handleIpn_blankTxnRef_throwsPaymentNotFound() {
        when(vnpayStrategy.verifyCallback(any())).thenReturn(
                VerificationResult.builder().signatureValid(true).txnRef("").build());

        assertThatThrownBy(() -> service.handleIpn(PaymentProvider.VNPAY, java.util.Map.of()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    // ── getStatus ───────────────────────────────────────────────────────────

    @Test
    void getStatus_notFound_throws() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void getStatus_notOwner_throwsAccessDenied() {
        Payment p = Payment.builder().id(1L).user(User.builder().id(99L).build()).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getStatus(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void getStatus_owner_returnsResponse() {
        User u = User.builder().id(1L).build();
        Payment p = Payment.builder().id(1L).user(u).status(PaymentStatus.SUCCESS)
                .provider(PaymentProvider.VNPAY).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(p));

        PaymentStatusResponse out = service.getStatus(1L, 1L);

        assertThat(out.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // ── handleReturn delegation to handleIpn for PENDING ────────────────────

    @Test
    void handleReturn_pending_delegatesToHandleIpn() {
        Payment p = Payment.builder().id(1L).user(user()).txnRef("T1")
                .status(PaymentStatus.PENDING)
                .amountVnd(100_000L).planCode("PRO")
                .purchaseType(PurchaseType.PLAN).scope(PaymentScope.USER).build();
        when(vnpayStrategy.verifyCallback(any())).thenReturn(VerificationResult.builder()
                .signatureValid(true).success(true).txnRef("T1")
                .amountVnd(100_000L).build());
        when(paymentRepository.findByTxnRef("T1")).thenReturn(Optional.of(p));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleReturn(PaymentProvider.VNPAY, java.util.Map.of("vnp_TxnRef", "T1"));

        verify(billingService).purchaseUserPlan(1L, "PRO");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static User user() {
        return User.builder().id(1L).email("u@x.com").fullName("U").build();
    }
}
