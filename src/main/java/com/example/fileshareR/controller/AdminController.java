package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.AddonAdminRequest;
import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.request.PlanAdminRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.service.AdminService;
import com.example.fileshareR.service.UserService;
import com.example.fileshareR.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform admin endpoints. URL scope is also locked down in
 * SecurityConfiguration with hasRole("ADMIN"); the per-method
 * @PreAuthorize adds a second layer.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    // ── Stats + charts ────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/stats/charts")
    public ResponseEntity<AdminChartsResponse> getCharts() {
        return ResponseEntity.ok(adminService.getCharts());
    }

    // ── User management ───────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserSummary>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String planCode,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(adminService.listUsers(
                search, planCode, isActive,
                PageRequest.of(page, size, parseSort(sort))));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserSummary> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUser(userId));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<AdminUserSummary> updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUpdateUserRequest req) {
        return ResponseEntity.ok(adminService.updateUser(userId, req, getCurrentUserId()));
    }

    // ── Plan management ───────────────────────────────────────────────────────

    @GetMapping("/plans")
    public ResponseEntity<List<Plan>> listPlans() {
        return ResponseEntity.ok(adminService.listPlans());
    }

    @PostMapping("/plans")
    public ResponseEntity<Plan> createPlan(@RequestBody PlanAdminRequest req) {
        return ResponseEntity.ok(adminService.createPlan(req));
    }

    @PatchMapping("/plans/{planId}")
    public ResponseEntity<Plan> updatePlan(@PathVariable Long planId, @RequestBody PlanAdminRequest req) {
        return ResponseEntity.ok(adminService.updatePlan(planId, req));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        adminService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }

    // ── Addon management ──────────────────────────────────────────────────────

    @GetMapping("/addons")
    public ResponseEntity<List<StorageAddon>> listAddons() {
        return ResponseEntity.ok(adminService.listAddons());
    }

    @PostMapping("/addons")
    public ResponseEntity<StorageAddon> createAddon(@RequestBody AddonAdminRequest req) {
        return ResponseEntity.ok(adminService.createAddon(req));
    }

    @PatchMapping("/addons/{addonId}")
    public ResponseEntity<StorageAddon> updateAddon(@PathVariable Long addonId, @RequestBody AddonAdminRequest req) {
        return ResponseEntity.ok(adminService.updateAddon(addonId, req));
    }

    @DeleteMapping("/addons/{addonId}")
    public ResponseEntity<Void> deleteAddon(@PathVariable Long addonId) {
        adminService.deleteAddon(addonId);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long getCurrentUserId() {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ACCESS_TOKEN));
        User user = userService.getUserByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return user.getId();
    }

    private Sort parseSort(String spec) {
        String[] parts = spec.split(",");
        String field = parts[0];
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }
}
