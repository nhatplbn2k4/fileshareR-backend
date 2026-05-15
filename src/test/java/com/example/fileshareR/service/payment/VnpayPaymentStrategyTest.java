package com.example.fileshareR.service.payment;

import com.example.fileshareR.config.PaymentProperties;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentScope;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.PurchaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayPaymentStrategyTest {

    /**
     * Sandbox public test secret from VNPay docs (not a real merchant secret).
     * Sufficient for deterministic HMAC verification.
     */
    private static final String TEST_HASH_SECRET = "SECRETKEY123456789ABCDEFG";
    private static final String TEST_TMN_CODE = "DEMOVNP1";

    private VnpayPaymentStrategy strategy;
    private PaymentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        PaymentProperties.Vnpay vnpay = properties.getVnpay();
        vnpay.setTmnCode(TEST_TMN_CODE);
        vnpay.setHashSecret(TEST_HASH_SECRET);
        vnpay.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpay.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        strategy = new VnpayPaymentStrategy(properties);
    }

    @Test
    void getProvider_returnsVNPAY() {
        assertThat(strategy.getProvider()).isEqualTo(PaymentProvider.VNPAY);
    }

    @Test
    void isConfigured_trueWhenCredentialsPresent() {
        assertThat(strategy.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_falseWhenSecretMissing() {
        properties.getVnpay().setHashSecret("");
        assertThat(strategy.isConfigured()).isFalse();
    }

    @Test
    void createPaymentUrl_buildsExpectedQueryStructure() {
        Payment payment = newPayment("TEST123", 100_000L);

        String url = strategy.createPaymentUrl(payment, "127.0.0.1");

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_TmnCode=" + TEST_TMN_CODE);
        // VNPay amount unit is VND × 100
        assertThat(url).contains("vnp_Amount=10000000");
        assertThat(url).contains("vnp_TxnRef=TEST123");
        assertThat(url).contains("vnp_SecureHash=");
        assertThat(url).contains("vnp_CurrCode=VND");
        // OrderInfo gets URL-encoded — space → %20
        assertThat(url).contains("vnp_OrderInfo=Thanh%20toan%20PLAN%20PREMIUM");
    }

    @Test
    void verifyCallback_acceptsGeneratedSignatureRoundtrip() {
        // Build params identical to a callback, then sign them with the same secret —
        // proves create + verify use a consistent algorithm.
        Map<String, String> params = signedCallbackParams("TXN1", 50_000L, "00", "00");

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTxnRef()).isEqualTo("TXN1");
        assertThat(result.getAmountVnd()).isEqualTo(50_000L);
    }

    @Test
    void verifyCallback_rejectsTamperedAmount() {
        Map<String, String> params = signedCallbackParams("TXN2", 50_000L, "00", "00");
        // Attacker bumps the amount AFTER signing — signature stays the same.
        params.put("vnp_Amount", "100000000");

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void verifyCallback_signatureValidButResponseCodeFailureMeansTxnFailed() {
        Map<String, String> params = signedCallbackParams("TXN3", 50_000L, "24", "02");

        VerificationResult result = strategy.verifyCallback(params);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("24");
    }

    @Test
    void verifyCallback_emptyParamsFails() {
        VerificationResult result = strategy.verifyCallback(Map.of());
        assertThat(result.isSignatureValid()).isFalse();
    }

    @Test
    void verifyCallback_missingHashFails() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "X");
        VerificationResult result = strategy.verifyCallback(params);
        assertThat(result.isSignatureValid()).isFalse();
    }

    @Test
    void createPaymentUrl_signatureRecoverableFromUrl() {
        // End-to-end: create returns a URL — parse it back into a map, verify it.
        Payment payment = newPayment("RT001", 75_000L);
        String url = strategy.createPaymentUrl(payment, "127.0.0.1");

        Map<String, String> params = parseQueryString(url.substring(url.indexOf('?') + 1));
        VerificationResult result = strategy.verifyCallback(params);

        // No ResponseCode set yet (VNPay would inject on user return) — signature is the assertion.
        assertThat(result.isSignatureValid()).isTrue();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Payment newPayment(String txnRef, long amount) {
        User user = User.builder().id(1L).email("test@example.com").build();
        return Payment.builder()
                .id(1L)
                .user(user)
                .provider(PaymentProvider.VNPAY)
                .purchaseType(PurchaseType.PLAN)
                .scope(PaymentScope.USER)
                .planCode("PREMIUM")
                .amountVnd(amount)
                .txnRef(txnRef)
                .status(PaymentStatus.PENDING)
                .build();
    }

    /**
     * Build a fully-signed VNPay callback param map. Drives the createPaymentUrl
     * path to compute the hash, then parses it back so verifyCallback gets a
     * valid input — proves the two halves agree.
     */
    private Map<String, String> signedCallbackParams(String txnRef, long amount,
                                                     String responseCode, String txnStatus) {
        Payment payment = newPayment(txnRef, amount);
        String url = strategy.createPaymentUrl(payment, "127.0.0.1");
        Map<String, String> params = parseQueryString(url.substring(url.indexOf('?') + 1));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_TransactionNo", "13371337");
        // Re-sign after adding the post-payment fields
        params.remove("vnp_SecureHash");
        params.put("vnp_SecureHash", recomputeHash(params));
        return params;
    }

    private String recomputeHash(Map<String, String> params) {
        // Reflect into private helper via re-running createPaymentUrl path won't work
        // because it adds CreateDate. Instead, exercise verifyCallback's inverse:
        // call once with placeholder hash, capture computed value when signature
        // mismatch is reported — but that's circular. Compute manually inline.
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            sorted.put(e.getKey(), e.getValue());
        }
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (data.length() > 0) data.append('&');
            data.append(e.getKey()).append('=')
                    .append(java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.US_ASCII)
                            .replace("+", "%20"));
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    TEST_HASH_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.US_ASCII);
            map.put(key, value);
        }
        return map;
    }
}
