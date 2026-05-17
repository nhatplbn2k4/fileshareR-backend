package com.example.fileshareR.service;

import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminStatsResponse;

public interface AdminService {
    AdminStatsResponse getStats();

    AdminChartsResponse getCharts();
}
