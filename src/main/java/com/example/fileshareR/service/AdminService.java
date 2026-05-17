package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.AddonAdminRequest;
import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.request.PlanAdminRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminDocumentSummary;
import com.example.fileshareR.dto.response.AdminGroupSummary;
import com.example.fileshareR.dto.response.AdminPaymentSummary;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.VisibilityType;
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

    // ── Documents ────────────────────────────────────────────────────────────
    Page<AdminDocumentSummary> listDocuments(String search, FileType fileType, VisibilityType visibility,
                                              Long userId, Pageable pageable);
    void deleteDocument(Long documentId);

    // ── Payments ─────────────────────────────────────────────────────────────
    Page<AdminPaymentSummary> listPayments(String search, PaymentProvider provider, PaymentStatus status,
                                            Pageable pageable);
    AdminPaymentSummary getPayment(Long paymentId);

    // ── Groups ───────────────────────────────────────────────────────────────
    Page<AdminGroupSummary> listGroups(String search, GroupVisibilityType visibility, Pageable pageable);
    void deleteGroup(Long groupId);
}
