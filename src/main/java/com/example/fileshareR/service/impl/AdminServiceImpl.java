package com.example.fileshareR.service.impl;

import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PaymentRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.AdminService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final GroupRepository groupRepository;
    private final PaymentRepository paymentRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long premiumUsers = countLong(
                "SELECT COUNT(u) FROM User u WHERE u.plan IS NOT NULL AND u.plan.code <> 'FREE' AND u.role = :role",
                "role", UserRole.USER);
        long activeUsers = countLong(
                "SELECT COUNT(u) FROM User u WHERE u.isActive = true");
        long signupsToday = countLong(
                "SELECT COUNT(u) FROM User u WHERE u.createdAt >= :start",
                "start", LocalDate.now().atStartOfDay());

        long totalDocuments = documentRepository.count();
        long totalGroups = groupRepository.count();

        long totalStorageUsedBytes = sumLong(
                "SELECT COALESCE(SUM(u.storageUsed), 0) FROM User u");
        long totalStorageQuotaBytes = sumLong(
                "SELECT COALESCE(SUM(u.plan.quotaBytes + u.bonusStorageBytes), 0) FROM User u WHERE u.plan IS NOT NULL");

        long totalRevenueVnd = sumLong(
                "SELECT COALESCE(SUM(p.amountVnd), 0) FROM Payment p WHERE p.status = :s",
                "s", PaymentStatus.SUCCESS);
        long revenueLast30dVnd = sumLong(
                "SELECT COALESCE(SUM(p.amountVnd), 0) FROM Payment p WHERE p.status = :s AND p.createdAt >= :since",
                "s", PaymentStatus.SUCCESS,
                "since", LocalDateTime.now().minusDays(30));
        long successPaymentsCount = countLong(
                "SELECT COUNT(p) FROM Payment p WHERE p.status = :s",
                "s", PaymentStatus.SUCCESS);
        long pendingPaymentsCount = countLong(
                "SELECT COUNT(p) FROM Payment p WHERE p.status = :s",
                "s", PaymentStatus.PENDING);

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .premiumUsers(premiumUsers)
                .activeUsers(activeUsers)
                .signupsToday(signupsToday)
                .totalDocuments(totalDocuments)
                .totalGroups(totalGroups)
                .totalStorageUsedBytes(totalStorageUsedBytes)
                .totalStorageQuotaBytes(totalStorageQuotaBytes)
                .totalRevenueVnd(totalRevenueVnd)
                .revenueLast30dVnd(revenueLast30dVnd)
                .successPaymentsCount(successPaymentsCount)
                .pendingPaymentsCount(pendingPaymentsCount)
                .build();
    }

    private long countLong(String jpql, Object... namedParams) {
        return executeScalar(jpql, namedParams);
    }

    private long sumLong(String jpql, Object... namedParams) {
        return executeScalar(jpql, namedParams);
    }

    private long executeScalar(String jpql, Object... namedParams) {
        var query = em.createQuery(jpql, Number.class);
        for (int i = 0; i + 1 < namedParams.length; i += 2) {
            query.setParameter((String) namedParams[i], namedParams[i + 1]);
        }
        Number result = query.getSingleResult();
        return result == null ? 0L : result.longValue();
    }
}
