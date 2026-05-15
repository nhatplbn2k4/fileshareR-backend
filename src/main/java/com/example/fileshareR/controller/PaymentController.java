package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.config.PaymentProperties;
import com.example.fileshareR.dto.request.InitiatePaymentRequest;
import com.example.fileshareR.dto.response.InitiatePaymentResponse;
import com.example.fileshareR.dto.response.PaymentStatusResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.service.PaymentService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;
    private final PaymentProperties paymentProperties;

    // ── User-initiated ───────────────────────────────────────────────────────

    @PostMapping("/initiate")
    public ResponseEntity<InitiatePaymentResponse> initiate(
            @Valid @RequestBody InitiatePaymentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        String clientIp = extractClientIp(httpRequest);
        InitiatePaymentResponse resp = paymentService.initiate(userId, request, clientIp);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentStatusResponse> getStatus(@PathVariable Long paymentId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(paymentService.getStatus(paymentId, userId));
    }

    // ── VNPay callbacks ──────────────────────────────────────────────────────

    /**
     * Browser redirect after VNPay payment. Always redirect user to frontend
     * with status query string so the SPA can render a result page.
     */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        PaymentStatusResponse status;
        try {
            status = paymentService.handleReturn(PaymentProvider.VNPAY, params);
        } catch (CustomException ex) {
            return redirectToFrontend("error", null, ex.getErrorCode().getMessage());
        }
        return redirectToFrontend(status.getStatus().name(), status.getTxnRef(), null);
    }

    /**
     * VNPay IPN endpoint — server-to-server. VNPay expects a specific JSON response
     * shape to acknowledge IPN.
     */
    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            PaymentStatusResponse status = paymentService.handleIpn(PaymentProvider.VNPAY, params);
            Map<String, String> body = new HashMap<>();
            body.put("RspCode", "00");
            body.put("Message", "Confirm Success: " + status.getStatus());
            return ResponseEntity.ok(body);
        } catch (CustomException ex) {
            Map<String, String> body = new HashMap<>();
            body.put("RspCode", mapVnpayErrorCode(ex.getErrorCode()));
            body.put("Message", ex.getErrorCode().getMessage());
            return ResponseEntity.ok(body);
        }
    }

    // ── MoMo callbacks ───────────────────────────────────────────────────────

    @GetMapping("/momo/return")
    public ResponseEntity<Void> momoReturn(@RequestParam Map<String, String> params) {
        PaymentStatusResponse status;
        try {
            status = paymentService.handleReturn(PaymentProvider.MOMO, params);
        } catch (CustomException ex) {
            return redirectToFrontend("error", null, ex.getErrorCode().getMessage());
        }
        return redirectToFrontend(status.getStatus().name(), status.getTxnRef(), null);
    }

    /**
     * MoMo IPN — POST JSON body, server-to-server. MoMo expects HTTP 204 No Content
     * to acknowledge; non-2xx triggers MoMo retry within a short window.
     */
    @PostMapping("/momo/ipn")
    public ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> body) {
        Map<String, String> params = body.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() == null ? "" : String.valueOf(e.getValue())));
        try {
            paymentService.handleIpn(PaymentProvider.MOMO, params);
            return ResponseEntity.noContent().build();
        } catch (CustomException ex) {
            log.warn("MoMo IPN handling failed: {}", ex.getErrorCode());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }

    /**
     * Best-effort client IP for VNPay vnp_IpAddr. Trusts X-Forwarded-For first hop
     * if present (project may sit behind a reverse proxy), else falls back to
     * request.getRemoteAddr(). Sandbox-safe — VNPay accepts 127.0.0.1.
     */
    private String extractClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private ResponseEntity<Void> redirectToFrontend(String status, String txnRef, String error) {
        String base = paymentProperties.getFrontendReturnUrl();
        StringBuilder sb = new StringBuilder(base);
        sb.append(base.contains("?") ? "&" : "?");
        sb.append("status=").append(status);
        if (txnRef != null) sb.append("&txnRef=").append(txnRef);
        if (error != null) sb.append("&error=").append(error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(sb.toString()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private String mapVnpayErrorCode(ErrorCode code) {
        return switch (code) {
            case PAYMENT_SIGNATURE_INVALID -> "97";
            case PAYMENT_NOT_FOUND -> "01";
            case PAYMENT_ALREADY_PROCESSED -> "02";
            case PAYMENT_AMOUNT_INVALID -> "04";
            default -> "99";
        };
    }
}
