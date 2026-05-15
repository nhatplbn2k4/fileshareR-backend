package com.example.fileshareR.service.payment;

import com.example.fileshareR.config.PaymentProperties;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.enums.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MoMo payment integration — API v2 /create endpoint.
 * Docs: https://developers.momo.vn/v3/docs/payment/api/wallet/onetime
 *
 * HMAC-SHA256 over fixed-order raw signature string.
 * MoMo quirks:
 *  - Amount in VND units directly (no *100 like VNPay)
 *  - Create is server-to-server REST call (not browser redirect like VNPay)
 *  - IPN payload is JSON, not form-encoded
 *  - resultCode == 0 means success (not "00" like VNPay)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MomoPaymentStrategy implements PaymentProviderStrategy {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long SUCCESS_RESULT_CODE = 0L;

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.MOMO;
    }

    @Override
    public boolean isConfigured() {
        return properties.getMomo().isConfigured();
    }

    @Override
    public String createPaymentUrl(Payment payment, String clientIp) {
        PaymentProperties.Momo cfg = properties.getMomo();
        String requestId = UUID.randomUUID().toString();
        String orderId = payment.getTxnRef();
        String orderInfo = buildOrderInfo(payment);
        String amount = String.valueOf(payment.getAmountVnd());
        String extraData = "";

        String rawSignature = "accessKey=" + cfg.getAccessKey()
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + cfg.getIpnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + cfg.getPartnerCode()
                + "&redirectUrl=" + cfg.getReturnUrl()
                + "&requestId=" + requestId
                + "&requestType=" + cfg.getRequestType();

        String signature = hmacSha256(cfg.getSecretKey(), rawSignature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", cfg.getPartnerCode());
        body.put("accessKey", cfg.getAccessKey());
        body.put("requestId", requestId);
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", cfg.getReturnUrl());
        body.put("ipnUrl", cfg.getIpnUrl());
        body.put("extraData", extraData);
        body.put("requestType", cfg.getRequestType());
        body.put("signature", signature);
        body.put("lang", cfg.getLang());

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getEndpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("MoMo create response txnRef={} http={} body={}", orderId, resp.statusCode(), resp.body());
            JsonNode root = objectMapper.readTree(resp.body());
            String payUrl = root.path("payUrl").asText(null);
            if (payUrl == null || payUrl.isBlank()) {
                throw new IllegalStateException("MoMo response missing payUrl: " + resp.body());
            }
            return payUrl;
        } catch (Exception ex) {
            log.error("MoMo create failed for txnRef={}", orderId, ex);
            throw new IllegalStateException("Failed to call MoMo create endpoint", ex);
        }
    }

    @Override
    public VerificationResult verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return VerificationResult.fail("Empty callback params");
        }
        PaymentProperties.Momo cfg = properties.getMomo();
        String receivedSignature = params.get("signature");
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return VerificationResult.fail("Thiếu signature");
        }

        String rawSignature = "accessKey=" + cfg.getAccessKey()
                + "&amount=" + nullToEmpty(params.get("amount"))
                + "&extraData=" + nullToEmpty(params.get("extraData"))
                + "&message=" + nullToEmpty(params.get("message"))
                + "&orderId=" + nullToEmpty(params.get("orderId"))
                + "&orderInfo=" + nullToEmpty(params.get("orderInfo"))
                + "&orderType=" + nullToEmpty(params.get("orderType"))
                + "&partnerCode=" + nullToEmpty(params.get("partnerCode"))
                + "&payType=" + nullToEmpty(params.get("payType"))
                + "&requestId=" + nullToEmpty(params.get("requestId"))
                + "&responseTime=" + nullToEmpty(params.get("responseTime"))
                + "&resultCode=" + nullToEmpty(params.get("resultCode"))
                + "&transId=" + nullToEmpty(params.get("transId"));

        String computed = hmacSha256(cfg.getSecretKey(), rawSignature);
        boolean sigOk = computed.equalsIgnoreCase(receivedSignature);
        if (!sigOk) {
            log.warn("MoMo signature mismatch orderId={} computed={} received={}",
                    params.get("orderId"), computed, receivedSignature);
            return VerificationResult.signatureMismatch();
        }

        long resultCode = parseLongSafe(params.get("resultCode"), -1L);
        boolean txnOk = resultCode == SUCCESS_RESULT_CODE;
        Long amount = parseLongOrNull(params.get("amount"));

        return VerificationResult.builder()
                .success(txnOk)
                .signatureValid(true)
                .txnRef(params.get("orderId"))
                .providerTxnId(params.get("transId"))
                .amountVnd(amount)
                .responseCode(String.valueOf(resultCode))
                .message(txnOk ? "Thanh toán MoMo thành công"
                        : "MoMo trả về resultCode " + resultCode + ": " + params.get("message"))
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildOrderInfo(Payment p) {
        String code = p.getPurchaseType() == com.example.fileshareR.enums.PurchaseType.PLAN
                ? p.getPlanCode() : p.getAddonCode();
        return "Thanh toan " + p.getPurchaseType() + " " + code;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private long parseLongSafe(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Long parseLongOrNull(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", ex);
        }
    }
}
