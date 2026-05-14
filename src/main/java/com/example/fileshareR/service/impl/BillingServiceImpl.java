package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.PlanResponse;
import com.example.fileshareR.dto.response.StorageAddonResponse;
import com.example.fileshareR.dto.response.StorageInfoResponse;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupPurchase;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.entity.UserPurchase;
import com.example.fileshareR.enums.PurchaseType;
import com.example.fileshareR.repository.GroupPurchaseRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.StorageAddonRepository;
import com.example.fileshareR.repository.UserPurchaseRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BillingServiceImpl implements BillingService {

    private final PlanRepository planRepository;
    private final StorageAddonRepository addonRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserPurchaseRepository userPurchaseRepository;
    private final GroupPurchaseRepository groupPurchaseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findAll().stream()
                .map(this::toPlanResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StorageAddonResponse> listAddons() {
        return addonRepository.findAll().stream()
                .map(this::toAddonResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StorageInfoResponse getUserStorageInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return buildUserInfo(user);
    }

    @Override
    @Transactional(readOnly = true)
    public StorageInfoResponse getGroupStorageInfo(Long groupId, Long requesterId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        return buildGroupInfo(group);
    }

    @Override
    public StorageInfoResponse purchaseUserPlan(Long userId, String planCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));

        user.setPlan(plan);
        userRepository.save(user);

        userPurchaseRepository.save(UserPurchase.builder()
                .user(user)
                .purchaseType(PurchaseType.PLAN)
                .plan(plan)
                .amountVnd(plan.getPriceVnd() != null ? plan.getPriceVnd() : 0L)
                .build());

        log.info("User {} upgraded to plan {}", userId, planCode);
        return buildUserInfo(user);
    }

    @Override
    public StorageInfoResponse purchaseUserAddon(Long userId, String addonCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        StorageAddon addon = addonRepository.findByCode(addonCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ADDON_NOT_FOUND));

        long bonus = user.getBonusStorageBytes() == null ? 0L : user.getBonusStorageBytes();
        user.setBonusStorageBytes(bonus + addon.getExtraBytes());
        userRepository.save(user);

        userPurchaseRepository.save(UserPurchase.builder()
                .user(user)
                .purchaseType(PurchaseType.ADDON)
                .addon(addon)
                .amountVnd(addon.getPriceVnd())
                .build());

        log.info("User {} purchased addon {}", userId, addonCode);
        return buildUserInfo(user);
    }

    @Override
    public StorageInfoResponse purchaseGroupPlan(Long groupId, Long userId, String planCode) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        requireOwner(group, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));

        group.setPlan(plan);
        groupRepository.save(group);

        groupPurchaseRepository.save(GroupPurchase.builder()
                .group(group)
                .purchasedBy(user)
                .purchaseType(PurchaseType.PLAN)
                .plan(plan)
                .amountVnd(plan.getPriceVnd() != null ? plan.getPriceVnd() : 0L)
                .build());

        log.info("Group {} upgraded to plan {} by user {}", groupId, planCode, userId);
        return buildGroupInfo(group);
    }

    @Override
    public StorageInfoResponse purchaseGroupAddon(Long groupId, Long userId, String addonCode) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));
        requireOwner(group, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        StorageAddon addon = addonRepository.findByCode(addonCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ADDON_NOT_FOUND));

        long bonus = group.getBonusStorageBytes() == null ? 0L : group.getBonusStorageBytes();
        group.setBonusStorageBytes(bonus + addon.getExtraBytes());
        groupRepository.save(group);

        groupPurchaseRepository.save(GroupPurchase.builder()
                .group(group)
                .purchasedBy(user)
                .purchaseType(PurchaseType.ADDON)
                .addon(addon)
                .amountVnd(addon.getPriceVnd())
                .build());

        log.info("Group {} purchased addon {} by user {}", groupId, addonCode, userId);
        return buildGroupInfo(group);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void requireOwner(Group group, Long userId) {
        if (group.getOwner() == null || !group.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private StorageInfoResponse buildUserInfo(User user) {
        Plan plan = user.getPlan();
        long base = plan != null ? plan.getQuotaBytes() : 0L;
        long bonus = user.getBonusStorageBytes() != null ? user.getBonusStorageBytes() : 0L;
        long used = user.getStorageUsed() != null ? user.getStorageUsed() : 0L;
        return StorageInfoResponse.builder()
                .plan(plan != null ? toPlanResponse(plan) : null)
                .storageUsed(used)
                .bonusStorageBytes(bonus)
                .quotaBytes(base)
                .totalQuotaBytes(base + bonus)
                .build();
    }

    private StorageInfoResponse buildGroupInfo(Group group) {
        Plan plan = group.getPlan();
        long base = plan != null ? plan.getQuotaBytes() : 0L;
        long bonus = group.getBonusStorageBytes() != null ? group.getBonusStorageBytes() : 0L;
        long used = group.getStorageUsed() != null ? group.getStorageUsed() : 0L;
        return StorageInfoResponse.builder()
                .plan(plan != null ? toPlanResponse(plan) : null)
                .storageUsed(used)
                .bonusStorageBytes(bonus)
                .quotaBytes(base)
                .totalQuotaBytes(base + bonus)
                .build();
    }

    private PlanResponse toPlanResponse(Plan p) {
        return PlanResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .quotaBytes(p.getQuotaBytes())
                .priceVnd(p.getPriceVnd())
                .description(p.getDescription())
                .build();
    }

    private StorageAddonResponse toAddonResponse(StorageAddon a) {
        return StorageAddonResponse.builder()
                .id(a.getId())
                .code(a.getCode())
                .name(a.getName())
                .extraBytes(a.getExtraBytes())
                .priceVnd(a.getPriceVnd())
                .description(a.getDescription())
                .build();
    }
}
