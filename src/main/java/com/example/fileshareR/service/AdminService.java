package com.example.fileshareR.service;

import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {
    AdminStatsResponse getStats();

    AdminChartsResponse getCharts();

    Page<AdminUserSummary> listUsers(String search, String planCode, Boolean isActive, Pageable pageable);

    AdminUserSummary getUser(Long userId);

    AdminUserSummary updateUser(Long userId, AdminUpdateUserRequest req, Long actingAdminId);
}
