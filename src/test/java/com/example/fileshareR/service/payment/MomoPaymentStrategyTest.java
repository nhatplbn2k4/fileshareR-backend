package com.example.fileshareR.service.payment;

import com.example.fileshareR.config.PaymentProperties;
import com.example.fileshareR.enums.PaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MomoPaymentStrategyTest {

    private static final String TEST_PARTNER_CODE = "MOMOTEST";
    private static final String TEST_ACCESS_KEY = "F8BBA842ECF85";
    private static final String TEST_SECRET_KEY = "K951B6PE1waDMi640xX08PD3vg6EkVlz";

    private MomoPaymentStrategy strategy;
    private PaymentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        PaymentProperties.Momo momo = properties.getMomo();
        momo.setPartnerCode(TEST_PARTNER_CODE);
        momo.setAccessKey(TEST_ACCESS_KEY);
        momo.setSecretKey(TEST_SECRET_KEY);
        momo.setEndpoint("https://test-payment.momo.vn/v2/gateway/api/create");
        momo.setReturnUrl("http://localhost:8080/api/payments/momo/return");
        momo.setIpnUrl("http://localhost:8080/api/payments/momo/ipn");
        strategy = new MomoPaymentStrategy(properties, new ObjectMapper());
    }

    @Test
    void getProvider_returnsMOMO() {
        assertThat(strategy.getProvider()).isEqualTo(PaymentProvider.MOMO);
    }

    @Test
    void isConfigured_trueWhenCredentialsPresent() {
        assertThat(strategy.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_falseWhenSecretMissing() {
        properties.getMomo().setSecretKey("");
        assertThat(strategy.isConfigured()).isFalse();
    }

    @Test
    void verifyCallback_validSignatureSucceedsOnResultCodeZero() {
        Map<String, String> params = signedIpn("ORDER1", 100_000L, 0L);

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTxnRef()).isEqualTo("ORDER1");
        assertThat(result.getProviderTxnId()).isEqualTo("9999999");
        assertThat(result.getAmountVnd()).isEqualTo(100_000L);
    }

    @Test
    void verifyCallback_rejectsTamperedAmount() {
        Map<String, String> params = signedIpn("ORDER2", 100_000L, 0L);
        params.put("amount", "999999999");

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void verifyCallback_signatureValidButResultCodeNonZeroMeansFailed() {
        Map<String, String> params = signedIpn("ORDER3", 100_000L, 1006L);

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("1006");
    }

    @Test
    void verifyCallback_emptyParamsFails() {
        VerificationResult result = strategy.verifyCallback(Map.of());
        assertThat(result.isSignatureValid()).isFalse();
    }

    @Test
    void verifyCallback_missingSignatureFails() {
        Map<String, String> params = new HashMap<>();
        params.put("orderId", "X");
        VerificationResult result = strategy.verifyCallback(params);
        assertThat(result.isSignatureValid()).isFalse();
    }

    @Test
    void verifyCallback_handlesNullFieldsViaEmptyString() {
        // Field ordering in MoMo's raw signature requires every key to be present.
        // Missing fields must be treated as "" (empty), not omitted.
        Map<String, String> params = new HashMap<>();
        params.put("amount", "100000");
        params.put("orderId", "ORDER4");
        params.put("partnerCode", TEST_PARTNER_CODE);
        params.put("requestId", "REQ1");
        params.put("responseTime", "1700000000000");
        params.put("resultCode", "0");
        params.put("transId", "12345");
        // intentionally omit extraData, message, orderInfo, orderType, payType
        params.put("signature", computeSignature(params));

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.isSuccess()).isTrue();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, String> signedIpn(String orderId, long amount, long resultCode) {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", TEST_PARTNER_CODE);
        params.put("orderId", orderId);
        params.put("requestId", "REQ-" + orderId);
        params.put("amount", String.valueOf(amount));
        params.put("orderInfo", "Test order");
        params.put("orderType", "momo_wallet");
        params.put("transId", "9999999");
        params.put("resultCode", String.valueOf(resultCode));
        params.put("message", resultCode == 0 ? "Successful." : "Failed.");
        params.put("payType", "qr");
        params.put("responseTime", "1700000000000");
        params.put("extraData", "");
        params.put("signature", computeSignature(params));
        return params;
    }

    /**
     * MoMo IPN signature spec: alphabetically ordered fields with fixed key set.
     * Re-implemented in tests deliberately so the strategy's signature logic
     * is verified against an independent computation, not its own helper.
     */
    private String computeSignature(Map<String, String> params) {
        String raw = "accessKey=" + TEST_ACCESS_KEY
                + "&amount=" + nz(params.get("amount"))
                + "&extraData=" + nz(params.get("extraData"))
                + "&message=" + nz(params.get("message"))
                + "&orderId=" + nz(params.get("orderId"))
                + "&orderInfo=" + nz(params.get("orderInfo"))
                + "&orderType=" + nz(params.get("orderType"))
                + "&partnerCode=" + nz(params.get("partnerCode"))
                + "&payType=" + nz(params.get("payType"))
                + "&requestId=" + nz(params.get("requestId"))
                + "&responseTime=" + nz(params.get("responseTime"))
                + "&resultCode=" + nz(params.get("resultCode"))
                + "&transId=" + nz(params.get("transId"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
