package com.example.fileshareR.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PurchasePlanRequest {
    @NotBlank
    private String planCode;
}
