package com.example.fileshareR.service.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VerificationResult {

    /**
     * True when provider-signed response is authentic AND transaction succeeded.
     * False covers both: invalid signature OR provider-reported failure.
     */
    private final boolean success;

    /**
     * True only when signature itself is valid (regardless of txn outcome).
     * Use to distinguish "real failure" vs "tampered request".
     */
    private final boolean signatureValid;

    /**
     * Our internal transaction reference echoed back by the provider.
     */
    private final String txnRef;

    /**
     * Provider's own transaction id (vnp_TransactionNo / momo transId).
     */
    private final String providerTxnId;

    /**
     * Amount provider reports paid (VND, already adjusted from VNPay's *100 unit).
     */
    private final Long amountVnd;

    /**
     * Provider response code (vnp_ResponseCode / momo resultCode), as-is for logging.
     */
    private final String responseCode;

    /**
     * Provider message — surface to user / log when failure.
     */
    private final String message;

    public static VerificationResult fail(String message) {
        return VerificationResult.builder()
                .success(false)
                .signatureValid(false)
                .message(message)
                .build();
    }

    public static VerificationResult signatureMismatch() {
        return VerificationResult.builder()
                .success(false)
                .signatureValid(false)
                .message("Chữ ký không hợp lệ")
                .build();
    }
}
