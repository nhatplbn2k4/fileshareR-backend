package com.example.fileshareR.service;

import com.example.fileshareR.dto.response.PlanResponse;
import com.example.fileshareR.dto.response.StorageAddonResponse;
import com.example.fileshareR.dto.response.StorageInfoResponse;

import java.util.List;

public interface BillingService {

    List<PlanResponse> listPlans();

    List<StorageAddonResponse> listAddons();

    StorageInfoResponse getUserStorageInfo(Long userId);

    StorageInfoResponse getGroupStorageInfo(Long groupId, Long requesterId);

    StorageInfoResponse purchaseUserPlan(Long userId, String planCode);

    StorageInfoResponse purchaseUserAddon(Long userId, String addonCode);

    StorageInfoResponse purchaseGroupPlan(Long groupId, Long userId, String planCode);

    StorageInfoResponse purchaseGroupAddon(Long groupId, Long userId, String addonCode);
}
