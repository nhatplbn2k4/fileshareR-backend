package com.example.fileshareR.service.payment;

import com.example.fileshareR.config.PaymentProperties;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VNPay payment integration — signature algorithm per
 * https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
 *
 * HMAC-SHA512 over alphabetically-sorted, URL-encoded query string.
 * VNPay quirks:
 *  - Amount unit is VND × 100 (10,000 VND → 1,000,000)
 *  - Date format: yyyyMMddHHmmss in GMT+7
 *  - Hash data uses SAME URL encoding as the final URL (encode-then-sign, not sign-then-encode)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayPaymentStrategy implements PaymentProviderStrategy {

    private static final String HMAC_ALGO = "HmacSHA512";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String SUCCESS_CODE = "00";

    private final PaymentProperties properties;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public boolean isConfigured() {
        return properties.getVnpay().isConfigured();
    }

    @Override
    public String createPaymentUrl(Payment payment, String clientIp) {
        PaymentProperties.Vnpay cfg = properties.getVnpay();
        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        TreeMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", cfg.getVersion());
        params.put("vnp_Command", cfg.getCommand());
        params.put("vnp_TmnCode", cfg.getTmnCode());
        params.put("vnp_Amount", String.valueOf(payment.getAmountVnd() * 100L));
        params.put("vnp_CurrCode", cfg.getCurrCode());
        params.put("vnp_TxnRef", payment.getTxnRef());
        params.put("vnp_OrderInfo", buildOrderInfo(payment));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", cfg.getLocale());
        params.put("vnp_ReturnUrl", cfg.getReturnUrl());
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(DATE_FMT));
        params.put("vnp_ExpireDate", now.plusMinutes(cfg.getExpireMinutes()).format(DATE_FMT));

        String hashData = buildEncodedQuery(params);
        String secureHash = hmacSha512(cfg.getHashSecret(), hashData);
        String url = cfg.getPayUrl() + "?" + hashData + "&vnp_SecureHash=" + secureHash;
        log.info("VNPay create url for txnRef={} amount={}", payment.getTxnRef(), payment.getAmountVnd());
        log.info("VNPay DEBUG hashData={}", hashData);
        log.info("VNPay DEBUG secureHash={}", secureHash);
        log.info("VNPay DEBUG fullUrl={}", url);
        return url;
    }

    @Override
    public VerificationResult verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return VerificationResult.fail("Empty callback params");
        }
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return VerificationResult.fail("Thiếu vnp_SecureHash");
        }

        TreeMap<String, String> filtered = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || v == null || v.isEmpty()) continue;
            if (k.equals("vnp_SecureHash") || k.equals("vnp_SecureHashType")) continue;
            filtered.put(k, v);
        }

        String hashData = buildEncodedQuery(filtered);
        String computed = hmacSha512(properties.getVnpay().getHashSecret(), hashData);
        boolean sigOk = computed.equalsIgnoreCase(receivedHash);
        if (!sigOk) {
            log.warn("VNPay signature mismatch txnRef={} computed={} received={}",
                    filtered.get("vnp_TxnRef"), computed, receivedHash);
            return VerificationResult.signatureMismatch();
        }

        String responseCode = filtered.get("vnp_ResponseCode");
        String transactionStatus = filtered.get("vnp_TransactionStatus");
        boolean txnOk = SUCCESS_CODE.equals(responseCode) && SUCCESS_CODE.equals(transactionStatus);

        Long amount = null;
        try {
            String amtStr = filtered.get("vnp_Amount");
            if (amtStr != null) amount = Long.parseLong(amtStr) / 100L;
        } catch (NumberFormatException ignore) {
            // Provider sent non-numeric; treat amount unknown — still continue
        }

        return VerificationResult.builder()
                .success(txnOk)
                .signatureValid(true)
                .txnRef(filtered.get("vnp_TxnRef"))
                .providerTxnId(filtered.get("vnp_TransactionNo"))
                .amountVnd(amount)
                .responseCode(responseCode)
                .message(txnOk ? "Thanh toán VNPay thành công"
                        : "VNPay trả về mã lỗi " + responseCode + "/" + transactionStatus)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildOrderInfo(Payment p) {
        String code = p.getPurchaseType() == com.example.fileshareR.enums.PurchaseType.PLAN
                ? p.getPlanCode() : p.getAddonCode();
        return "Thanh toan " + p.getPurchaseType() + " " + code;
    }

    /**
     * Build alphabetically-sorted query string with values URL-encoded.
     * Both signing and final URL use this exact encoding (VNPay quirk).
     */
    private String buildEncodedQuery(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA512", ex);
        }
    }
}
