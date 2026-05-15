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
import com.example.fileshareR.service.PaymentService;
import com.example.fileshareR.service.payment.PaymentProviderStrategy;
import com.example.fileshareR.service.payment.VerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final StorageAddonRepository addonRepository;
    private final GroupRepository groupRepository;
    private final BillingService billingService;

    private final List<PaymentProviderStrategy> strategies;
    private Map<PaymentProvider, PaymentProviderStrategy> strategyMap;

    private Map<PaymentProvider, PaymentProviderStrategy> resolveStrategies() {
        if (strategyMap == null) {
            Map<PaymentProvider, PaymentProviderStrategy> map = new EnumMap<>(PaymentProvider.class);
            for (PaymentProviderStrategy s : strategies) {
                map.put(s.getProvider(), s);
            }
            strategyMap = map;
        }
        return strategyMap;
    }

    private PaymentProviderStrategy requireStrategy(PaymentProvider provider) {
        PaymentProviderStrategy s = resolveStrategies().get(provider);
        if (s == null) throw new CustomException(ErrorCode.PAYMENT_PROVIDER_NOT_SUPPORTED);
        if (!s.isConfigured()) throw new CustomException(ErrorCode.PAYMENT_PROVIDER_CONFIG_MISSING);
        return s;
    }

    @Override
    public InitiatePaymentResponse initiate(Long userId, InitiatePaymentRequest req, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        validateRequest(req);
        PaymentProviderStrategy strategy = requireStrategy(req.getProvider());

        long amount = resolveAmount(req);
        if (amount <= 0) throw new CustomException(ErrorCode.PAYMENT_AMOUNT_INVALID);

        if (req.getScope() == PaymentScope.GROUP) {
            Group group = groupRepository.findById(req.getGroupId())
                    .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
            if (group.getOwner() == null || !group.getOwner().getId().equals(userId)) {
                throw new CustomException(ErrorCode.GROUP_OWNER_REQUIRED);
            }
        }

        Payment payment = Payment.builder()
                .user(user)
                .provider(req.getProvider())
                .purchaseType(req.getPurchaseType())
                .scope(req.getScope())
                .planCode(req.getPlanCode())
                .addonCode(req.getAddonCode())
                .groupId(req.getGroupId())
                .amountVnd(amount)
                .currency("VND")
                .txnRef(generateTxnRef())
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        String redirectUrl;
        try {
            redirectUrl = strategy.createPaymentUrl(payment, clientIp);
        } catch (RuntimeException ex) {
            log.error("Initiate payment failed for txnRef={}", payment.getTxnRef(), ex);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Initiate failed: " + ex.getMessage());
            paymentRepository.save(payment);
            throw new CustomException(ErrorCode.PAYMENT_INITIATE_FAILED, ex.getMessage());
        }
        payment.setReturnUrl(redirectUrl);
        paymentRepository.save(payment);

        return InitiatePaymentResponse.builder()
                .paymentId(payment.getId())
                .txnRef(payment.getTxnRef())
                .provider(payment.getProvider())
                .amountVnd(payment.getAmountVnd())
                .redirectUrl(redirectUrl)
                .build();
    }

    @Override
    public PaymentStatusResponse handleIpn(PaymentProvider provider, Map<String, String> params) {
        PaymentProviderStrategy strategy = requireStrategy(provider);
        VerificationResult result = strategy.verifyCallback(params);
        if (!result.isSignatureValid()) {
            throw new CustomException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }

        String txnRef = result.getTxnRef();
        if (txnRef == null || txnRef.isBlank()) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        Payment payment = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        // Idempotency: if already terminal, return current state without re-applying.
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("IPN replay for txnRef={} already terminal status={}", txnRef, payment.getStatus());
            return toResponse(payment);
        }

        payment.setProviderTxnId(result.getProviderTxnId());
        payment.setIpnReceivedAt(LocalDateTime.now());
        payment.setRawResponse(truncate(params.toString(), 8000));

        if (result.isSuccess()) {
            // Amount-mismatch guard — provider could replay older payment ref with smaller amount.
            if (result.getAmountVnd() != null && !result.getAmountVnd().equals(payment.getAmountVnd())) {
                log.warn("Amount mismatch txnRef={} expected={} received={}",
                        txnRef, payment.getAmountVnd(), result.getAmountVnd());
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Amount mismatch: expected " + payment.getAmountVnd()
                        + " got " + result.getAmountVnd());
                paymentRepository.save(payment);
                return toResponse(payment);
            }
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            applyPurchase(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.getMessage());
            paymentRepository.save(payment);
        }
        return toResponse(payment);
    }

    @Override
    public PaymentStatusResponse handleReturn(PaymentProvider provider, Map<String, String> params) {
        PaymentProviderStrategy strategy = requireStrategy(provider);
        VerificationResult result = strategy.verifyCallback(params);
        if (!result.isSignatureValid()) {
            throw new CustomException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }

        String txnRef = result.getTxnRef();
        Payment payment = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        // Browser return is informational — do NOT apply purchase here (IPN owns that).
        // Just surface current status to frontend so the result page can render.
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getStatus(Long paymentId, Long requesterUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getUser().getId().equals(requesterUserId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return toResponse(payment);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void validateRequest(InitiatePaymentRequest req) {
        if (req.getPurchaseType() == PurchaseType.PLAN
                && (req.getPlanCode() == null || req.getPlanCode().isBlank())) {
            throw new CustomException(ErrorCode.PAYMENT_TARGET_REQUIRED);
        }
        if (req.getPurchaseType() == PurchaseType.ADDON
                && (req.getAddonCode() == null || req.getAddonCode().isBlank())) {
            throw new CustomException(ErrorCode.PAYMENT_TARGET_REQUIRED);
        }
        if (req.getScope() == PaymentScope.GROUP && req.getGroupId() == null) {
            throw new CustomException(ErrorCode.PAYMENT_GROUP_ID_REQUIRED);
        }
    }

    private long resolveAmount(InitiatePaymentRequest req) {
        if (req.getPurchaseType() == PurchaseType.PLAN) {
            Plan plan = planRepository.findByCode(req.getPlanCode())
                    .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
            return plan.getPriceVnd() == null ? 0L : plan.getPriceVnd();
        }
        StorageAddon addon = addonRepository.findByCode(req.getAddonCode())
                .orElseThrow(() -> new CustomException(ErrorCode.ADDON_NOT_FOUND));
        return addon.getPriceVnd() == null ? 0L : addon.getPriceVnd();
    }

    /**
     * Apply purchase post-payment via existing BillingService methods.
     * Keeps quota-update logic single-sourced.
     */
    private void applyPurchase(Payment payment) {
        Long userId = payment.getUser().getId();
        try {
            if (payment.getScope() == PaymentScope.USER) {
                if (payment.getPurchaseType() == PurchaseType.PLAN) {
                    billingService.purchaseUserPlan(userId, payment.getPlanCode());
                } else {
                    billingService.purchaseUserAddon(userId, payment.getAddonCode());
                }
            } else {
                if (payment.getPurchaseType() == PurchaseType.PLAN) {
                    billingService.purchaseGroupPlan(payment.getGroupId(), userId, payment.getPlanCode());
                } else {
                    billingService.purchaseGroupAddon(payment.getGroupId(), userId, payment.getAddonCode());
                }
            }
        } catch (RuntimeException ex) {
            log.error("Apply purchase failed AFTER successful payment txnRef={} — manual reconciliation needed",
                    payment.getTxnRef(), ex);
            // Payment row stays SUCCESS so finance reconciliation is preserved;
            // failureReason captures the apply-side issue for ops.
            payment.setFailureReason("Apply purchase failed: " + ex.getMessage());
            paymentRepository.save(payment);
        }
    }

    private String generateTxnRef() {
        return "FSR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private PaymentStatusResponse toResponse(Payment p) {
        return PaymentStatusResponse.builder()
                .paymentId(p.getId())
                .txnRef(p.getTxnRef())
                .provider(p.getProvider())
                .purchaseType(p.getPurchaseType())
                .scope(p.getScope())
                .planCode(p.getPlanCode())
                .addonCode(p.getAddonCode())
                .amountVnd(p.getAmountVnd())
                .status(p.getStatus())
                .providerTxnId(p.getProviderTxnId())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .ipnReceivedAt(p.getIpnReceivedAt())
                .build();
    }
}
