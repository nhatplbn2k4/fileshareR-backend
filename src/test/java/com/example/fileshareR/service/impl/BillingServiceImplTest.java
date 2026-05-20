package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.response.PlanResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock private PlanRepository planRepository;
    @Mock private StorageAddonRepository addonRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserPurchaseRepository userPurchaseRepository;
    @Mock private GroupPurchaseRepository groupPurchaseRepository;

    private BillingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BillingServiceImpl(planRepository, addonRepository, userRepository,
                groupRepository, userPurchaseRepository, groupPurchaseRepository);
    }

    // ── listPlans / listAddons ──────────────────────────────────────────────

    @Test
    void listPlans_mapsToResponseAndPreservesOrder() {
        when(planRepository.findAll()).thenReturn(List.of(
                plan(1L, "FREE", 1000L, 0L),
                plan(2L, "PREMIUM", 10000L, 50000L)));

        List<PlanResponse> out = service.listPlans();

        assertThat(out).extracting(PlanResponse::getCode).containsExactly("FREE", "PREMIUM");
        assertThat(out).extracting(PlanResponse::getQuotaBytes).containsExactly(1000L, 10000L);
    }

    @Test
    void listAddons_mapsToResponse() {
        StorageAddon a = StorageAddon.builder().id(1L).code("X10").name("Extra 10GB")
                .extraBytes(10L).priceVnd(99L).description("d").build();
        when(addonRepository.findAll()).thenReturn(List.of(a));

        assertThat(service.listAddons()).hasSize(1)
                .extracting("code").containsExactly("X10");
    }

    // ── getUserStorageInfo / getGroupStorageInfo ────────────────────────────

    @Test
    void getUserStorageInfo_happy_returnsAggregatedQuota() {
        User u = User.builder().id(1L)
                .plan(plan(1L, "PRO", 1000L, 0L))
                .bonusStorageBytes(500L)
                .storageUsed(700L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        StorageInfoResponse info = service.getUserStorageInfo(1L);

        assertThat(info.getQuotaBytes()).isEqualTo(1000L);
        assertThat(info.getBonusStorageBytes()).isEqualTo(500L);
        assertThat(info.getTotalQuotaBytes()).isEqualTo(1500L);
        assertThat(info.getStorageUsed()).isEqualTo(700L);
        assertThat(info.getPlan().getCode()).isEqualTo("PRO");
    }

    @Test
    void getUserStorageInfo_userMissing_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserStorageInfo(99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getUserStorageInfo_nullPlanAndBonus_returnsZeros() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        StorageInfoResponse info = service.getUserStorageInfo(1L);

        assertThat(info.getQuotaBytes()).isZero();
        assertThat(info.getBonusStorageBytes()).isZero();
        assertThat(info.getTotalQuotaBytes()).isZero();
        assertThat(info.getPlan()).isNull();
    }

    @Test
    void getGroupStorageInfo_happy() {
        Group g = Group.builder().id(20L)
                .plan(plan(2L, "PRO", 2000L, 0L))
                .bonusStorageBytes(1000L)
                .storageUsed(500L).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));

        StorageInfoResponse info = service.getGroupStorageInfo(20L, 1L);

        assertThat(info.getTotalQuotaBytes()).isEqualTo(3000L);
        assertThat(info.getStorageUsed()).isEqualTo(500L);
    }

    @Test
    void getGroupStorageInfo_groupMissing_throws() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupStorageInfo(99L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    // ── purchaseUserPlan ────────────────────────────────────────────────────

    @Test
    void purchaseUserPlan_happy_persistsPurchaseAndUpdatesUser() {
        User u = User.builder().id(1L).build();
        Plan p = plan(2L, "PREMIUM", 10000L, 100_000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(planRepository.findByCode("PREMIUM")).thenReturn(Optional.of(p));

        service.purchaseUserPlan(1L, "PREMIUM");

        assertThat(u.getPlan()).isEqualTo(p);
        verify(userRepository).save(u);
        ArgumentCaptor<UserPurchase> cap = ArgumentCaptor.forClass(UserPurchase.class);
        verify(userPurchaseRepository).save(cap.capture());
        assertThat(cap.getValue().getPurchaseType()).isEqualTo(PurchaseType.PLAN);
        assertThat(cap.getValue().getAmountVnd()).isEqualTo(100_000L);
    }

    @Test
    void purchaseUserPlan_planWithNullPrice_recordsZeroAmount() {
        User u = User.builder().id(1L).build();
        Plan free = plan(1L, "FREE", 1000L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(free));

        service.purchaseUserPlan(1L, "FREE");

        ArgumentCaptor<UserPurchase> cap = ArgumentCaptor.forClass(UserPurchase.class);
        verify(userPurchaseRepository).save(cap.capture());
        assertThat(cap.getValue().getAmountVnd()).isZero();
    }

    @Test
    void purchaseUserPlan_userMissing_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseUserPlan(1L, "PRO"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void purchaseUserPlan_planMissing_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(planRepository.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseUserPlan(1L, "X"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLAN_NOT_FOUND);
    }

    // ── purchaseUserAddon ───────────────────────────────────────────────────

    @Test
    void purchaseUserAddon_addsToBonus_andPersists() {
        User u = User.builder().id(1L).bonusStorageBytes(500L).build();
        StorageAddon a = StorageAddon.builder().code("X10").extraBytes(1000L).priceVnd(20_000L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(addonRepository.findByCode("X10")).thenReturn(Optional.of(a));

        service.purchaseUserAddon(1L, "X10");

        assertThat(u.getBonusStorageBytes()).isEqualTo(1500L);
        verify(userPurchaseRepository).save(org.mockito.ArgumentMatchers.any(UserPurchase.class));
    }

    @Test
    void purchaseUserAddon_nullBonusStart_treatsAsZero() {
        User u = User.builder().id(1L).build();
        StorageAddon a = StorageAddon.builder().code("X").extraBytes(100L).priceVnd(0L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(addonRepository.findByCode("X")).thenReturn(Optional.of(a));

        service.purchaseUserAddon(1L, "X");

        assertThat(u.getBonusStorageBytes()).isEqualTo(100L);
    }

    @Test
    void purchaseUserAddon_addonMissing_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(addonRepository.findByCode("MISS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseUserAddon(1L, "MISS"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDON_NOT_FOUND);
    }

    // ── purchaseGroupPlan ───────────────────────────────────────────────────

    @Test
    void purchaseGroupPlan_happy_ownerSucceeds() {
        User owner = User.builder().id(1L).build();
        Group g = Group.builder().id(20L).owner(owner).build();
        Plan p = plan(2L, "PRO", 5000L, 50_000L);
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(p));

        service.purchaseGroupPlan(20L, 1L, "PRO");

        assertThat(g.getPlan()).isEqualTo(p);
        verify(groupRepository).save(g);
        verify(groupPurchaseRepository).save(org.mockito.ArgumentMatchers.any(GroupPurchase.class));
    }

    @Test
    void purchaseGroupPlan_notOwner_throws() {
        Group g = Group.builder().id(20L).owner(User.builder().id(99L).build()).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.purchaseGroupPlan(20L, 1L, "PRO"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void purchaseGroupPlan_nullOwner_throws() {
        Group g = Group.builder().id(20L).owner(null).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.purchaseGroupPlan(20L, 1L, "PRO"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    @Test
    void purchaseGroupPlan_groupMissing_throws() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseGroupPlan(99L, 1L, "P"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void purchaseGroupPlan_userMissing_throws() {
        User owner = User.builder().id(1L).build();
        Group g = Group.builder().id(20L).owner(owner).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseGroupPlan(20L, 1L, "P"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── purchaseGroupAddon ──────────────────────────────────────────────────

    @Test
    void purchaseGroupAddon_owner_addsBonusBytes() {
        User owner = User.builder().id(1L).build();
        Group g = Group.builder().id(20L).owner(owner).bonusStorageBytes(100L).build();
        StorageAddon a = StorageAddon.builder().code("X").extraBytes(900L).priceVnd(10_000L).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(addonRepository.findByCode("X")).thenReturn(Optional.of(a));

        service.purchaseGroupAddon(20L, 1L, "X");

        assertThat(g.getBonusStorageBytes()).isEqualTo(1000L);
        verify(groupPurchaseRepository).save(org.mockito.ArgumentMatchers.any(GroupPurchase.class));
    }

    @Test
    void purchaseGroupAddon_nullBonusStart_treatsAsZero() {
        User owner = User.builder().id(1L).build();
        Group g = Group.builder().id(20L).owner(owner).build();
        StorageAddon a = StorageAddon.builder().code("X").extraBytes(500L).priceVnd(0L).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(addonRepository.findByCode("X")).thenReturn(Optional.of(a));

        service.purchaseGroupAddon(20L, 1L, "X");

        assertThat(g.getBonusStorageBytes()).isEqualTo(500L);
    }

    @Test
    void purchaseGroupAddon_addonMissing_throws() {
        User owner = User.builder().id(1L).build();
        Group g = Group.builder().id(20L).owner(owner).build();
        when(groupRepository.findById(20L)).thenReturn(Optional.of(g));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(addonRepository.findByCode("MISS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseGroupAddon(20L, 1L, "MISS"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDON_NOT_FOUND);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Plan plan(Long id, String code, Long quota, Long price) {
        return Plan.builder().id(id).code(code).name(code)
                .quotaBytes(quota).priceVnd(price).description("d").build();
    }
}
