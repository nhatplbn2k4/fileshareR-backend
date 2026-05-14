package com.example.fileshareR.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PurchaseAddonRequest {
    @NotBlank
    private String addonCode;
}
