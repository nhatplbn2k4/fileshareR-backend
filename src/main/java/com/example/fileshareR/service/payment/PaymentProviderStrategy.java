package com.example.fileshareR.service.payment;

import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.enums.PaymentProvider;

import java.util.Map;

/**
 * Strategy interface — one implementation per payment gateway (VNPay, MoMo, ...).
 *
 * Selected by {@link com.example.fileshareR.service.impl.PaymentServiceImpl}
 * via {@link #getProvider()} match.
 */
public interface PaymentProviderStrategy {

    PaymentProvider getProvider();

    /**
     * Build the URL to redirect the user to (VNPay) OR call the provider's create API
     * and return its payUrl (MoMo). Caller redirects browser to this URL.
     *
     * @param payment persisted Payment entity in PENDING status with txnRef assigned
     * @param clientIp originating client IP, required by VNPay; nullable for MoMo
     * @return absolute URL to redirect to
     */
    String createPaymentUrl(Payment payment, String clientIp);

    /**
     * Verify provider callback (browser return OR server-to-server IPN).
     * Caller passes flat key→value map of all params received.
     */
    VerificationResult verifyCallback(Map<String, String> params);

    /**
     * True if all required credentials configured. Used by PaymentService to
     * fail fast with {@code PAYMENT_PROVIDER_CONFIG_MISSING} instead of NPE.
     */
    boolean isConfigured();
}
