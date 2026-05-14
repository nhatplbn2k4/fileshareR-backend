package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.PurchaseAddonRequest;
import com.example.fileshareR.dto.request.PurchasePlanRequest;
import com.example.fileshareR.dto.response.PlanResponse;
import com.example.fileshareR.dto.response.StorageAddonResponse;
import com.example.fileshareR.dto.response.StorageInfoResponse;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.BillingService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final UserService userService;

    // ── Public lookup ───────────────────────────────────────────────────────

    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> listPlans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @GetMapping("/storage-addons")
    public ResponseEntity<List<StorageAddonResponse>> listAddons() {
        return ResponseEntity.ok(billingService.listAddons());
    }

    // ── User storage ─────────────────────────────────────────────────────────

    @GetMapping("/users/me/storage")
    public ResponseEntity<StorageInfoResponse> getMyStorage() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.getUserStorageInfo(userId));
    }

    @PostMapping("/users/me/upgrade-plan")
    public ResponseEntity<StorageInfoResponse> upgradeUserPlan(@Valid @RequestBody PurchasePlanRequest req) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.purchaseUserPlan(userId, req.getPlanCode()));
    }

    @PostMapping("/users/me/purchase-addon")
    public ResponseEntity<StorageInfoResponse> purchaseUserAddon(@Valid @RequestBody PurchaseAddonRequest req) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.purchaseUserAddon(userId, req.getAddonCode()));
    }

    // ── Group storage ────────────────────────────────────────────────────────

    @GetMapping("/groups/{groupId}/storage")
    public ResponseEntity<StorageInfoResponse> getGroupStorage(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.getGroupStorageInfo(groupId, userId));
    }

    @PostMapping("/groups/{groupId}/upgrade-plan")
    public ResponseEntity<StorageInfoResponse> upgradeGroupPlan(
            @PathVariable Long groupId,
            @Valid @RequestBody PurchasePlanRequest req) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.purchaseGroupPlan(groupId, userId, req.getPlanCode()));
    }

    @PostMapping("/groups/{groupId}/purchase-addon")
    public ResponseEntity<StorageInfoResponse> purchaseGroupAddon(
            @PathVariable Long groupId,
            @Valid @RequestBody PurchaseAddonRequest req) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(billingService.purchaseGroupAddon(groupId, userId, req.getAddonCode()));
    }

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }
}
