package com.example.fileshareR.dto.response;

import com.example.fileshareR.enums.PaymentProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiatePaymentResponse {

    private Long paymentId;
    private String txnRef;
    private PaymentProvider provider;
    private Long amountVnd;
    /** URL frontend should redirect the user to. */
    private String redirectUrl;
}
