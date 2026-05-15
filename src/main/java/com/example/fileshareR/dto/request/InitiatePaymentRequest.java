package com.example.fileshareR.dto.request;

import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentScope;
import com.example.fileshareR.enums.PurchaseType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRequest {

    @NotNull
    private PaymentProvider provider;

    @NotNull
    private PurchaseType purchaseType;

    @NotNull
    private PaymentScope scope;

    /** Required when purchaseType=PLAN. */
    private String planCode;

    /** Required when purchaseType=ADDON. */
    private String addonCode;

    /** Required when scope=GROUP. */
    private Long groupId;
}
