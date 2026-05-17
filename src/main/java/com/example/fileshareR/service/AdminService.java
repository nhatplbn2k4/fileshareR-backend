package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.AddonAdminRequest;
import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.request.PlanAdminRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminService {
    AdminStatsResponse getStats();

    AdminChartsResponse getCharts();

    Page<AdminUserSummary> listUsers(String search, String planCode, Boolean isActive, Pageable pageable);

    AdminUserSummary getUser(Long userId);

    AdminUserSummary updateUser(Long userId, AdminUpdateUserRequest req, Long actingAdminId);

    // ── Plans ─────────────────────────────────────────────────────────────────
    List<Plan> listPlans();
    Plan createPlan(PlanAdminRequest req);
    Plan updatePlan(Long planId, PlanAdminRequest req);
    void deletePlan(Long planId);

    // ── Addons ────────────────────────────────────────────────────────────────
    List<StorageAddon> listAddons();
    StorageAddon createAddon(AddonAdminRequest req);
    StorageAddon updateAddon(Long addonId, AddonAdminRequest req);
    void deleteAddon(Long addonId);
}
