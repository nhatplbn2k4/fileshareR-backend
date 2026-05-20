package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private GroupRepository groupRepository;

    private StorageQuotaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StorageQuotaServiceImpl(userRepository, groupRepository);
    }

    // ── getUserTotalQuota ───────────────────────────────────────────────────

    @Test
    void getUserTotalQuota_planNull_andBonusNull_returnsZero() {
        assertThat(service.getUserTotalQuota(User.builder().build())).isZero();
    }

    @Test
    void getUserTotalQuota_planOnly_returnsPlanQuota() {
        User user = User.builder()
                .plan(Plan.builder().quotaBytes(10_000L).build())
                .build();

        assertThat(service.getUserTotalQuota(user)).isEqualTo(10_000L);
    }

    @Test
    void getUserTotalQuota_planPlusBonus_returnsSum() {
        User user = User.builder()
                .plan(Plan.builder().quotaBytes(10_000L).build())
                .bonusStorageBytes(5_000L)
                .build();

        assertThat(service.getUserTotalQuota(user)).isEqualTo(15_000L);
    }

    // ── getGroupTotalQuota ──────────────────────────────────────────────────

    @Test
    void getGroupTotalQuota_planNullBonusNull_returnsZero() {
        assertThat(service.getGroupTotalQuota(Group.builder().build())).isZero();
    }

    @Test
    void getGroupTotalQuota_planAndBonus_returnsSum() {
        Group g = Group.builder()
                .plan(Plan.builder().quotaBytes(8_000L).build())
                .bonusStorageBytes(2_000L)
                .build();

        assertThat(service.getGroupTotalQuota(g)).isEqualTo(10_000L);
    }

    // ── ensureUserCanUpload ─────────────────────────────────────────────────

    @Test
    void ensureUserCanUpload_belowLimit_doesNotThrow() {
        User user = User.builder()
                .plan(Plan.builder().quotaBytes(1000L).build())
                .storageUsed(400L)
                .build();

        assertThatCode(() -> service.ensureUserCanUpload(user, 500L))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureUserCanUpload_atLimit_doesNotThrow() {
        User user = User.builder()
                .plan(Plan.builder().quotaBytes(1000L).build())
                .storageUsed(0L)
                .build();

        assertThatCode(() -> service.ensureUserCanUpload(user, 1000L))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureUserCanUpload_overLimit_throws() {
        User user = User.builder()
                .id(7L)
                .plan(Plan.builder().quotaBytes(1000L).build())
                .storageUsed(900L)
                .build();

        assertThatThrownBy(() -> service.ensureUserCanUpload(user, 200L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_STORAGE_QUOTA_EXCEEDED);
    }

    @Test
    void ensureUserCanUpload_storageUsedNull_treatedAsZero() {
        User user = User.builder()
                .plan(Plan.builder().quotaBytes(500L).build())
                // storageUsed not set → null
                .build();

        assertThatCode(() -> service.ensureUserCanUpload(user, 500L))
                .doesNotThrowAnyException();
    }

    // ── ensureGroupCanUpload ────────────────────────────────────────────────

    @Test
    void ensureGroupCanUpload_overLimit_throws() {
        Group g = Group.builder()
                .id(1L)
                .plan(Plan.builder().quotaBytes(100L).build())
                .storageUsed(90L)
                .build();

        assertThatThrownBy(() -> service.ensureGroupCanUpload(g, 50L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_STORAGE_QUOTA_EXCEEDED);
    }

    @Test
    void ensureGroupCanUpload_underLimit_passes() {
        Group g = Group.builder()
                .plan(Plan.builder().quotaBytes(1000L).build())
                .storageUsed(100L)
                .build();

        assertThatCode(() -> service.ensureGroupCanUpload(g, 200L))
                .doesNotThrowAnyException();
    }

    // ── increment / decrement user ──────────────────────────────────────────

    @Test
    void incrementUserUsage_addsAndSaves() {
        User user = User.builder().storageUsed(100L).build();

        service.incrementUserUsage(user, 50L);

        assertThat(user.getStorageUsed()).isEqualTo(150L);
        verify(userRepository).save(user);
    }

    @Test
    void incrementUserUsage_nullStartingValue_initialisesFromZero() {
        User user = User.builder().build();

        service.incrementUserUsage(user, 25L);

        assertThat(user.getStorageUsed()).isEqualTo(25L);
    }

    @Test
    void decrementUserUsage_clampsAtZero() {
        User user = User.builder().storageUsed(30L).build();

        service.decrementUserUsage(user, 50L);

        assertThat(user.getStorageUsed()).isZero();
        verify(userRepository).save(user);
    }

    @Test
    void decrementUserUsage_normalSubtraction() {
        User user = User.builder().storageUsed(100L).build();

        service.decrementUserUsage(user, 30L);

        assertThat(user.getStorageUsed()).isEqualTo(70L);
    }

    // ── increment / decrement group ─────────────────────────────────────────

    @Test
    void incrementGroupUsage_persists() {
        Group g = Group.builder().storageUsed(0L).build();

        service.incrementGroupUsage(g, 200L);

        assertThat(g.getStorageUsed()).isEqualTo(200L);
        verify(groupRepository).save(g);
    }

    @Test
    void decrementGroupUsage_clampsAtZeroAndSaves() {
        Group g = Group.builder().storageUsed(10L).build();

        service.decrementGroupUsage(g, 100L);

        assertThat(g.getStorageUsed()).isZero();
        verify(groupRepository).save(g);
    }
}
