package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminChartsResponse.DailyPoint;
import com.example.fileshareR.dto.response.AdminChartsResponse.LabeledCount;
import com.example.fileshareR.dto.response.AdminChartsResponse.MonthlyPoint;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PaymentRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.UserRepository;
import com.example.fileshareR.service.AdminService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final GroupRepository groupRepository;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;

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

    // ── Chart datasets ────────────────────────────────────────────────────────

    @Override
    public AdminChartsResponse getCharts() {
        return AdminChartsResponse.builder()
                .signupsLast30Days(signupsLast30Days())
                .revenueLast30Days(revenueLast30Days())
                .revenueLast12Months(revenueLast12Months())
                .documentsByType(documentsByType())
                .paymentsByStatus(paymentsByStatus())
                .usersByPlan(usersByPlan())
                .build();
    }

    /**
     * Daily signup count for the last 30 days. Missing days are filled with 0 so
     * the FE line chart renders evenly without gaps.
     */
    @SuppressWarnings("unchecked")
    private List<DailyPoint> signupsLast30Days() {
        LocalDate from = LocalDate.now().minusDays(29);
        var rows = em.createNativeQuery(
                "SELECT DATE(created_at) AS d, COUNT(*) AS c " +
                        "FROM users WHERE created_at >= :since " +
                        "GROUP BY DATE(created_at) ORDER BY d")
                .setParameter("since", from.atStartOfDay())
                .getResultList();

        Map<String, Long> byDate = new HashMap<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            byDate.put(toLocalDate(cols[0]).toString(),
                    ((Number) cols[1]).longValue());
        }
        return fillDailyGaps(from, 30, byDate);
    }

    @SuppressWarnings("unchecked")
    private List<DailyPoint> revenueLast30Days() {
        LocalDate from = LocalDate.now().minusDays(29);
        var rows = em.createNativeQuery(
                "SELECT DATE(created_at) AS d, COALESCE(SUM(amount_vnd), 0) AS r " +
                        "FROM payments WHERE status = :status AND created_at >= :since " +
                        "GROUP BY DATE(created_at) ORDER BY d")
                .setParameter("status", PaymentStatus.SUCCESS.name())
                .setParameter("since", from.atStartOfDay())
                .getResultList();

        Map<String, Long> byDate = new HashMap<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            byDate.put(toLocalDate(cols[0]).toString(),
                    ((Number) cols[1]).longValue());
        }
        return fillDailyGaps(from, 30, byDate);
    }

    @SuppressWarnings("unchecked")
    private List<MonthlyPoint> revenueLast12Months() {
        LocalDate fromMonth = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        var rows = em.createNativeQuery(
                "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS m, " +
                        "       COALESCE(SUM(amount_vnd), 0) AS r " +
                        "FROM payments " +
                        "WHERE status = :status AND created_at >= :since " +
                        "GROUP BY DATE_TRUNC('month', created_at) " +
                        "ORDER BY DATE_TRUNC('month', created_at)")
                .setParameter("status", PaymentStatus.SUCCESS.name())
                .setParameter("since", fromMonth.atStartOfDay())
                .getResultList();

        Map<String, Long> byMonth = new HashMap<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            byMonth.put((String) cols[0], ((Number) cols[1]).longValue());
        }

        List<MonthlyPoint> result = new ArrayList<>(12);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 0; i < 12; i++) {
            String key = fromMonth.plusMonths(i).format(fmt);
            result.add(MonthlyPoint.builder()
                    .month(key)
                    .value(byMonth.getOrDefault(key, 0L))
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<LabeledCount> documentsByType() {
        var rows = em.createNativeQuery(
                "SELECT file_type::text AS t, COUNT(*) AS c " +
                        "FROM documents GROUP BY file_type ORDER BY c DESC")
                .getResultList();
        return mapLabeledCount(rows);
    }

    @SuppressWarnings("unchecked")
    private List<LabeledCount> paymentsByStatus() {
        var rows = em.createNativeQuery(
                "SELECT status AS s, COUNT(*) AS c " +
                        "FROM payments GROUP BY status ORDER BY c DESC")
                .getResultList();
        return mapLabeledCount(rows);
    }

    @SuppressWarnings("unchecked")
    private List<LabeledCount> usersByPlan() {
        var rows = em.createNativeQuery(
                "SELECT COALESCE(p.code, 'NO_PLAN') AS code, COUNT(u.id) AS c " +
                        "FROM users u LEFT JOIN plans p ON p.id = u.plan_id " +
                        "GROUP BY p.code ORDER BY c DESC")
                .getResultList();
        return mapLabeledCount(rows);
    }

    private List<LabeledCount> mapLabeledCount(List<?> rows) {
        List<LabeledCount> out = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            out.add(LabeledCount.builder()
                    .label(cols[0] == null ? "UNKNOWN" : cols[0].toString())
                    .count(((Number) cols[1]).longValue())
                    .build());
        }
        return out;
    }

    /**
     * Hibernate may return either java.sql.Date or LocalDate for a Postgres
     * DATE column depending on driver/dialect/Hibernate version. Accept both
     * so the native query rows don't ClassCastException at runtime.
     */
    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date sd) return sd.toLocalDate();
        if (value instanceof java.util.Date d) {
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    // ── User management ───────────────────────────────────────────────────────

    @Override
    public Page<AdminUserSummary> listUsers(String search, String planCode, Boolean isActive, Pageable pageable) {
        return userRepository
                .findAllForAdmin(search, planCode, isActive, pageable)
                .map(this::toSummary);
    }

    @Override
    public AdminUserSummary getUser(Long userId) {
        return userRepository.findById(userId)
                .map(this::toSummary)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public AdminUserSummary updateUser(Long userId, AdminUpdateUserRequest req, Long actingAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Admin should not deactivate themselves — would lock out the only admin
        // when the seeded account is the one performing the action.
        if (req.getIsActive() != null && !req.getIsActive() && user.getId().equals(actingAdminId)) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Không thể tự vô hiệu hoá tài khoản admin đang đăng nhập.");
        }

        if (req.getPlanCode() != null && !req.getPlanCode().isBlank()) {
            Plan plan = planRepository.findByCode(req.getPlanCode())
                    .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
            user.setPlan(plan);
        }
        if (req.getIsActive() != null) {
            user.setIsActive(req.getIsActive());
        }

        userRepository.save(user);
        return toSummary(user);
    }

    private AdminUserSummary toSummary(User user) {
        Plan plan = user.getPlan();
        long quota = plan != null ? plan.getQuotaBytes() : 0L;
        long bonus = user.getBonusStorageBytes() != null ? user.getBonusStorageBytes() : 0L;
        return AdminUserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : null)
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .planCode(plan != null ? plan.getCode() : null)
                .planName(plan != null ? plan.getName() : null)
                .storageUsed(user.getStorageUsed() != null ? user.getStorageUsed() : 0L)
                .totalQuotaBytes(quota + bonus)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private List<DailyPoint> fillDailyGaps(LocalDate from, int days, Map<String, Long> byDate) {
        List<DailyPoint> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String key = from.plusDays(i).toString();
            result.add(DailyPoint.builder()
                    .date(key)
                    .value(byDate.getOrDefault(key, 0L))
                    .build());
        }
        return result;
    }
}
