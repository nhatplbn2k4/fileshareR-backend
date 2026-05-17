package com.example.fileshareR.service.impl;

import com.example.fileshareR.common.exception.CustomException;
import com.example.fileshareR.common.exception.ErrorCode;
import com.example.fileshareR.dto.request.AddonAdminRequest;
import com.example.fileshareR.dto.request.AdminUpdateUserRequest;
import com.example.fileshareR.dto.request.PlanAdminRequest;
import com.example.fileshareR.dto.response.AdminChartsResponse;
import com.example.fileshareR.dto.response.AdminChartsResponse.DailyPoint;
import com.example.fileshareR.dto.response.AdminChartsResponse.LabeledCount;
import com.example.fileshareR.dto.response.AdminChartsResponse.MonthlyPoint;
import com.example.fileshareR.dto.response.AdminDocumentSummary;
import com.example.fileshareR.dto.response.AdminPaymentSummary;
import com.example.fileshareR.dto.response.AdminStatsResponse;
import com.example.fileshareR.dto.response.AdminUserSummary;
import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.enums.VisibilityType;
import com.example.fileshareR.service.DocumentService;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PaymentRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.StorageAddonRepository;
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
    private final StorageAddonRepository storageAddonRepository;
    private final DocumentService documentService;

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

    // ── Payment management ────────────────────────────────────────────────────

    @Override
    public Page<AdminPaymentSummary> listPayments(String search, PaymentProvider provider, PaymentStatus status,
                                                   Pageable pageable) {
        return paymentRepository
                .findAllForAdmin(search, provider, status, pageable)
                .map(this::toPaymentSummary);
    }

    @Override
    public AdminPaymentSummary getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(this::toPaymentSummary)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST, "Giao dịch không tồn tại"));
    }

    private AdminPaymentSummary toPaymentSummary(Payment p) {
        var u = p.getUser();
        return AdminPaymentSummary.builder()
                .id(p.getId())
                .txnRef(p.getTxnRef())
                .providerTxnId(p.getProviderTxnId())
                .provider(p.getProvider() != null ? p.getProvider().name() : null)
                .purchaseType(p.getPurchaseType() != null ? p.getPurchaseType().name() : null)
                .scope(p.getScope() != null ? p.getScope().name() : null)
                .planCode(p.getPlanCode())
                .addonCode(p.getAddonCode())
                .amountVnd(p.getAmountVnd())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .ipnReceivedAt(p.getIpnReceivedAt())
                .userId(u != null ? u.getId() : null)
                .userEmail(u != null ? u.getEmail() : null)
                .userFullName(u != null ? u.getFullName() : null)
                .groupId(p.getGroupId())
                .build();
    }

    // ── Document management ───────────────────────────────────────────────────

    @Override
    public Page<AdminDocumentSummary> listDocuments(String search, FileType fileType, VisibilityType visibility,
                                                     Long userId, Pageable pageable) {
        return documentRepository
                .findAllForAdmin(search, fileType, visibility, userId, pageable)
                .map(this::toDocSummary);
    }

    @Override
    public void deleteDocument(Long documentId) {
        documentService.adminDeleteDocument(documentId);
    }

    private AdminDocumentSummary toDocSummary(Document d) {
        var owner = d.getUser();
        var grp = d.getGroup();
        return AdminDocumentSummary.builder()
                .id(d.getId())
                .title(d.getTitle())
                .fileName(d.getFileName())
                .fileType(d.getFileType() != null ? d.getFileType().name() : null)
                .fileSize(d.getFileSize())
                .visibility(d.getVisibility() != null ? d.getVisibility().name() : null)
                .moderationStatus(d.getModerationStatus() != null ? d.getModerationStatus().name() : null)
                .downloadCount(d.getDownloadCount())
                .ownerId(owner != null ? owner.getId() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                .ownerFullName(owner != null ? owner.getFullName() : null)
                .groupId(grp != null ? grp.getId() : null)
                .groupName(grp != null ? grp.getName() : null)
                .createdAt(d.getCreatedAt())
                .build();
    }

    // ── Plan management ───────────────────────────────────────────────────────

    @Override
    public List<Plan> listPlans() {
        return planRepository.findAllByOrderByPriceVndAsc();
    }

    @Override
    @Transactional
    public Plan createPlan(PlanAdminRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Code không được để trống.");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Tên gói không được để trống.");
        }
        if (req.getQuotaBytes() == null || req.getQuotaBytes() < 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Dung lượng (quotaBytes) không hợp lệ.");
        }
        if (req.getPriceVnd() == null || req.getPriceVnd() < 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Giá (priceVnd) không hợp lệ.");
        }
        String code = req.getCode().trim().toUpperCase();
        if (planRepository.existsByCode(code)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Code đã tồn tại: " + code);
        }
        Plan p = Plan.builder()
                .code(code)
                .name(req.getName().trim())
                .quotaBytes(req.getQuotaBytes())
                .priceVnd(req.getPriceVnd())
                .description(req.getDescription())
                .build();
        return planRepository.save(p);
    }

    @Override
    @Transactional
    public Plan updatePlan(Long planId, PlanAdminRequest req) {
        Plan p = planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));

        if (req.getName() != null && !req.getName().isBlank()) p.setName(req.getName().trim());
        if (req.getQuotaBytes() != null) {
            if (req.getQuotaBytes() < 0) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "Dung lượng không hợp lệ.");
            }
            p.setQuotaBytes(req.getQuotaBytes());
        }
        if (req.getPriceVnd() != null) {
            if (req.getPriceVnd() < 0) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "Giá không hợp lệ.");
            }
            p.setPriceVnd(req.getPriceVnd());
        }
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        // Code is immutable post-create — silently ignored if provided.
        return planRepository.save(p);
    }

    @Override
    @Transactional
    public void deletePlan(Long planId) {
        Plan p = planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));

        long usersOnPlan = ((Number) em.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.plan.id = :pid")
                .setParameter("pid", planId)
                .getSingleResult()).longValue();
        if (usersOnPlan > 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Không thể xoá: " + usersOnPlan + " user đang dùng gói này. Đổi user sang gói khác trước.");
        }
        long groupsOnPlan = ((Number) em.createQuery(
                "SELECT COUNT(g) FROM Group g WHERE g.plan.id = :pid")
                .setParameter("pid", planId)
                .getSingleResult()).longValue();
        if (groupsOnPlan > 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "Không thể xoá: " + groupsOnPlan + " nhóm đang dùng gói này.");
        }
        planRepository.delete(p);
    }

    // ── Addon management ──────────────────────────────────────────────────────

    @Override
    public List<StorageAddon> listAddons() {
        return storageAddonRepository.findAllByOrderByPriceVndAsc();
    }

    @Override
    @Transactional
    public StorageAddon createAddon(AddonAdminRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Code không được để trống.");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Tên addon không được để trống.");
        }
        if (req.getExtraBytes() == null || req.getExtraBytes() <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Dung lượng (extraBytes) phải > 0.");
        }
        if (req.getPriceVnd() == null || req.getPriceVnd() < 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Giá không hợp lệ.");
        }
        String code = req.getCode().trim().toUpperCase();
        if (storageAddonRepository.existsByCode(code)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "Code đã tồn tại: " + code);
        }
        StorageAddon a = StorageAddon.builder()
                .code(code)
                .name(req.getName().trim())
                .extraBytes(req.getExtraBytes())
                .priceVnd(req.getPriceVnd())
                .description(req.getDescription())
                .build();
        return storageAddonRepository.save(a);
    }

    @Override
    @Transactional
    public StorageAddon updateAddon(Long addonId, AddonAdminRequest req) {
        StorageAddon a = storageAddonRepository.findById(addonId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST, "Addon không tồn tại"));

        if (req.getName() != null && !req.getName().isBlank()) a.setName(req.getName().trim());
        if (req.getExtraBytes() != null) {
            if (req.getExtraBytes() <= 0) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "Dung lượng phải > 0.");
            }
            a.setExtraBytes(req.getExtraBytes());
        }
        if (req.getPriceVnd() != null) {
            if (req.getPriceVnd() < 0) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "Giá không hợp lệ.");
            }
            a.setPriceVnd(req.getPriceVnd());
        }
        if (req.getDescription() != null) a.setDescription(req.getDescription());
        return storageAddonRepository.save(a);
    }

    @Override
    @Transactional
    public void deleteAddon(Long addonId) {
        StorageAddon a = storageAddonRepository.findById(addonId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST, "Addon không tồn tại"));
        // Addon references aren't enforced by FK (Payment.addon_code is a free
        // string), so a hard delete is safe — historical payments retain the
        // string code without breaking integrity.
        storageAddonRepository.delete(a);
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
