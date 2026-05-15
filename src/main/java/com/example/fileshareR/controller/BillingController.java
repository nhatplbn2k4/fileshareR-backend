package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.PlanResponse;
import com.example.fileshareR.dto.response.StorageAddonResponse;
import com.example.fileshareR.dto.response.StorageInfoResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.BillingService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Storage + plan catalog endpoints.
 *
 * <p>Purchase endpoints were removed when payment gateway integration shipped —
 * upgrades MUST go through {@code POST /api/payments/initiate} which charges via
 * VNPay/MoMo before applying the storage change. The actual quota-update logic
 * remains in {@link BillingService#purchaseUserPlan} / {@code purchaseUserAddon} /
 * group variants, called internally by {@code PaymentServiceImpl} on verified IPN.</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final UserService userService;

    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> listPlans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @GetMapping("/storage-addons")
    public ResponseEntity<List<StorageAddonResponse>> listAddons() {
        return ResponseEntity.ok(billingService.listAddons());
    }

    @GetMapping("/users/me/storage")
    public ResponseEntity<StorageInfoResponse> getMyStorage() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.getUserStorageInfo(userId));
    }

    @GetMapping("/groups/{groupId}/storage")
    public ResponseEntity<StorageInfoResponse> getGroupStorage(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.getGroupStorageInfo(groupId, userId));
    }

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }
}
