package com.example.fileshareR.controller;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
