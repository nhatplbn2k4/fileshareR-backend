package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.InitiatePaymentRequest;
import com.example.fileshareR.dto.response.InitiatePaymentResponse;
import com.example.fileshareR.dto.response.PaymentStatusResponse;
import com.example.fileshareR.enums.PaymentProvider;

import java.util.Map;

public interface PaymentService {

    /**
     * Validate inputs, resolve price from Plan/Addon repo, persist Payment(PENDING),
     * delegate URL generation to the matching {@link com.example.fileshareR.service.payment.PaymentProviderStrategy}.
     */
    InitiatePaymentResponse initiate(Long userId, InitiatePaymentRequest request, String clientIp);

    /**
     * Verify provider IPN callback (server-to-server). Idempotent — multiple calls
     * with same successful payload apply purchase ONCE.
     *
     * @return PaymentStatusResponse with terminal status
     */
    PaymentStatusResponse handleIpn(PaymentProvider provider, Map<String, String> params);

    /**
     * Verify provider browser-return callback. Read-only — does NOT apply purchase
     * (IPN owns that for reliability). Just updates the Payment row's status.
     */
    PaymentStatusResponse handleReturn(PaymentProvider provider, Map<String, String> params);

    PaymentStatusResponse getStatus(Long paymentId, Long requesterUserId);
}
