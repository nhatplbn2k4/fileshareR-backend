package com.example.fileshareR.config;

import com.example.fileshareR.entity.Document;
import com.example.fileshareR.entity.Folder;
import com.example.fileshareR.entity.Group;
import com.example.fileshareR.entity.GroupMember;
import com.example.fileshareR.entity.Payment;
import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.AuthProvider;
import com.example.fileshareR.enums.FileType;
import com.example.fileshareR.enums.FolderVisibilityType;
import com.example.fileshareR.enums.GroupMemberRole;
import com.example.fileshareR.enums.GroupVisibilityType;
import com.example.fileshareR.enums.ModerationStatus;
import com.example.fileshareR.enums.PaymentProvider;
import com.example.fileshareR.enums.PaymentScope;
import com.example.fileshareR.enums.PaymentStatus;
import com.example.fileshareR.enums.PurchaseType;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.enums.VisibilityType;
import com.example.fileshareR.repository.DocumentRepository;
import com.example.fileshareR.repository.FolderRepository;
import com.example.fileshareR.repository.GroupMemberRepository;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PaymentRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds demo data for thesis presentation — 20 users, 7 groups, 30 docs,
 * 40 payments — spread across the last 12 months so admin charts render
 * meaningful series.
 *
 * Activation: only runs when `app.demo-seed.enabled=true` (set via env
 * APP_DEMO_SEED_ENABLED=true). Idempotent — second run skips if the demo
 * fingerprint user (demo01@filesharer.local) already exists.
 *
 * Demo passwords: `demo123` for every demo user. Document fileUrl points
 * at placeholder paths (no actual files on disk) — list / chart features
 * work, but download / preview will 404 for these rows.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {

    private static final String FINGERPRINT_EMAIL = "demo01@filesharer.local";
    private static final String DEMO_PASSWORD = "demo123";
    private static final int USER_COUNT = 20;
    private static final int PREMIUM_USER_COUNT = 5;
    private static final int GROUP_COUNT = 7;
    private static final int DOC_COUNT = 30;
    private static final int PAYMENT_COUNT = 40;

    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final DocumentRepository documentRepository;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager em;

    private final Random rand = new Random(42); // deterministic ordering

    private static final String[] LAST_NAMES = {
        "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan",
        "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương"
    };
    private static final String[] MIDDLE_NAMES = {
        "Văn", "Thị", "Hữu", "Đức", "Quang", "Minh", "Anh", "Bảo",
        "Mai", "Hà", "Hồng", "Phúc", "Thành", "Tuấn", "Khánh"
    };
    private static final String[] FIRST_NAMES = {
        "An", "Bình", "Cường", "Dũng", "Hà", "Hằng", "Khánh", "Linh",
        "Minh", "Nam", "Phong", "Quân", "Sơn", "Trang", "Tú", "Vy",
        "Long", "Ngọc", "Hiếu", "Thảo"
    };

    private static final String[] GROUP_NAMES = {
        "Nhóm học Toán 12A",
        "CLB Lập trình BKHN",
        "Hội Văn học Việt Nam",
        "Luyện thi IELTS 7.0+",
        "Đồ án Tốt nghiệp K65",
        "Marketing 4.0 Forum",
        "Tiếng Anh Giao Tiếp"
    };
    private static final String[] GROUP_DESCS = {
        "Cùng nhau luyện đề Toán THPT Quốc Gia 2026.",
        "Nơi trao đổi kiến thức lập trình, dự án, thuật toán.",
        "Chia sẻ sách hay, bài phân tích, tác phẩm văn học VN + thế giới.",
        "Tài liệu, tips, mock test cho band 7.0 trở lên.",
        "Tài liệu tham khảo + slide bảo vệ đồ án CNTT khoá 65.",
        "Bài viết, case study, công cụ digital marketing mới nhất.",
        "Học giao tiếp tiếng Anh qua các chủ đề thường ngày."
    };

    private static final String[] DOC_TITLES = {
        "Bài tập chương 1 Đại số",
        "Slide Spring Boot Annotations",
        "CV mẫu Software Engineer",
        "Đề thi giữa kỳ Mạng Máy Tính",
        "Báo cáo phân tích Big Data",
        "Tổng hợp ngữ pháp IELTS",
        "Đồ án Web Bán hàng - Báo cáo",
        "Hướng dẫn dùng Docker Compose",
        "Sách 10 bí quyết Marketing 2026",
        "Slide chuyên đề React Hooks",
        "Cấu trúc dữ liệu - Cây nhị phân",
        "Đề cương ôn thi Hệ điều hành",
        "Mẫu báo cáo thực tập IT",
        "Tài liệu Microservices in Action",
        "Bài giảng Machine Learning cơ bản",
        "100 câu hỏi phỏng vấn Java",
        "Slide Kubernetes Deep Dive",
        "Tổng hợp công thức Lượng giác",
        "Trắc nghiệm Triết học Mác-Lênin",
        "Đề thi tốt nghiệp THPT 2025 - Toán",
        "Hướng dẫn viết Khoá luận tốt nghiệp",
        "Cẩm nang Tiếng Anh giao tiếp 1000 câu",
        "Slide AWS Cloud Practitioner",
        "Đề mẫu chứng chỉ Tiếng Anh A2",
        "Phân tích thuật toán Dijkstra",
        "Báo cáo Seminar Blockchain",
        "Tài liệu UX/UI Design Foundations",
        "Đề kiểm tra Cấu trúc dữ liệu",
        "Bài tập SQL nâng cao + đáp án",
        "Sách Clean Code Vietsub"
    };
    private static final FileType[] FILE_TYPES = FileType.values();

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmail(FINGERPRINT_EMAIL).isPresent()) {
            log.info("DemoSeeder: demo dataset already present (found {}), skipping",
                    FINGERPRINT_EMAIL);
            return;
        }

        log.info("DemoSeeder: starting demo data seed...");
        Plan freePlan = planRepository.findByCode("FREE")
                .orElseThrow(() -> new IllegalStateException("FREE plan missing — StoragePlanSeeder must run first"));
        Plan premiumPlan = planRepository.findByCode("PREMIUM").orElse(null);

        List<User> users = seedUsers(freePlan, premiumPlan);
        log.info("DemoSeeder: seeded {} demo users", users.size());

        List<Folder> folders = seedFolders(users);
        log.info("DemoSeeder: seeded {} folders", folders.size());

        List<Group> groups = seedGroups(users, freePlan);
        log.info("DemoSeeder: seeded {} groups", groups.size());

        seedGroupMembers(groups, users);
        log.info("DemoSeeder: seeded group memberships");

        seedDocuments(users, groups, folders);
        log.info("DemoSeeder: seeded {} documents", DOC_COUNT);

        seedPayments(users);
        log.info("DemoSeeder: seeded {} payments", PAYMENT_COUNT);

        backdateTimestamps();
        log.info("DemoSeeder: complete — admin dashboard charts should now render historical data");
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private List<User> seedUsers(Plan freePlan, Plan premiumPlan) {
        List<User> created = new ArrayList<>();
        String hashedPwd = passwordEncoder.encode(DEMO_PASSWORD);
        Set<String> usedNames = new HashSet<>();

        for (int i = 1; i <= USER_COUNT; i++) {
            String email = String.format("demo%02d@filesharer.local", i);
            String fullName;
            do {
                fullName = randomVietnameseName();
            } while (!usedNames.add(fullName));

            // First PREMIUM_USER_COUNT users get PREMIUM plan
            Plan plan = (i <= PREMIUM_USER_COUNT && premiumPlan != null) ? premiumPlan : freePlan;

            User u = User.builder()
                    .email(email)
                    .passwordHash(hashedPwd)
                    .fullName(fullName)
                    .role(UserRole.USER)
                    .isActive(true)
                    .emailVerified(true)
                    .authProvider(AuthProvider.LOCAL)
                    .plan(plan)
                    .build();
            created.add(userRepository.save(u));
        }
        return created;
    }

    private String randomVietnameseName() {
        return LAST_NAMES[rand.nextInt(LAST_NAMES.length)] + " "
                + MIDDLE_NAMES[rand.nextInt(MIDDLE_NAMES.length)] + " "
                + FIRST_NAMES[rand.nextInt(FIRST_NAMES.length)];
    }

    // ── Folders ───────────────────────────────────────────────────────────────

    private List<Folder> seedFolders(List<User> users) {
        List<Folder> created = new ArrayList<>();
        String[] folderNames = {"Tài liệu học tập", "Đề thi", "Slide bài giảng", "Khoá luận", "Tham khảo"};

        for (User user : users) {
            int folderCount = 1 + rand.nextInt(3); // 1-3 folders per user
            for (int i = 0; i < folderCount; i++) {
                Folder f = Folder.builder()
                        .user(user)
                        .name(folderNames[rand.nextInt(folderNames.length)])
                        .visibility(rand.nextBoolean() ? FolderVisibilityType.PUBLIC : FolderVisibilityType.PRIVATE)
                        .shareToken(UUID.randomUUID().toString())
                        .build();
                created.add(folderRepository.save(f));
            }
        }
        return created;
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    private List<Group> seedGroups(List<User> users, Plan freePlan) {
        List<Group> created = new ArrayList<>();
        for (int i = 0; i < GROUP_COUNT; i++) {
            User owner = users.get(i); // demo01..demo07 are owners
            // First 4 are PUBLIC, rest PRIVATE
            GroupVisibilityType vis = i < 4 ? GroupVisibilityType.PUBLIC : GroupVisibilityType.PRIVATE;
            Group g = Group.builder()
                    .name(GROUP_NAMES[i])
                    .description(GROUP_DESCS[i])
                    .visibility(vis)
                    .owner(owner)
                    .shareToken(UUID.randomUUID().toString())
                    .requireApproval(vis == GroupVisibilityType.PRIVATE)
                    .plan(freePlan)
                    .build();
            created.add(groupRepository.save(g));
        }
        return created;
    }

    // ── Group memberships ─────────────────────────────────────────────────────

    private void seedGroupMembers(List<Group> groups, List<User> users) {
        for (Group group : groups) {
            // Owner first
            GroupMember ownerMember = GroupMember.builder()
                    .group(group)
                    .user(group.getOwner())
                    .role(GroupMemberRole.OWNER)
                    .joinedAt(LocalDateTime.now())
                    .build();
            groupMemberRepository.save(ownerMember);

            // 5-15 random members (excluding owner)
            int memberCount = 5 + rand.nextInt(11);
            Set<Long> picked = new HashSet<>();
            picked.add(group.getOwner().getId());
            int attempts = 0;
            while (picked.size() < memberCount + 1 && attempts < 100) {
                attempts++;
                User candidate = users.get(rand.nextInt(users.size()));
                if (picked.add(candidate.getId())) {
                    // 20% chance ADMIN, rest MEMBER
                    GroupMemberRole role = rand.nextInt(5) == 0
                            ? GroupMemberRole.ADMIN
                            : GroupMemberRole.MEMBER;
                    GroupMember m = GroupMember.builder()
                            .group(group)
                            .user(candidate)
                            .role(role)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    groupMemberRepository.save(m);
                }
            }
        }
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    private void seedDocuments(List<User> users, List<Group> groups, List<Folder> folders) {
        for (int i = 0; i < DOC_COUNT; i++) {
            User owner = users.get(rand.nextInt(users.size()));
            String title = DOC_TITLES[i % DOC_TITLES.length];
            FileType fileType = FILE_TYPES[rand.nextInt(FILE_TYPES.length)];
            long fileSize = (100L + rand.nextInt(4900)) * 1024; // 100KB - 5MB

            // 40% docs belong to a group, rest personal
            Group group = (rand.nextInt(10) < 4 && !groups.isEmpty())
                    ? groups.get(rand.nextInt(groups.size()))
                    : null;

            // Pick a folder owned by this user (only for personal docs)
            Folder folder = null;
            if (group == null) {
                List<Folder> userFolders = folders.stream()
                        .filter(f -> f.getUser().getId().equals(owner.getId()))
                        .toList();
                if (!userFolders.isEmpty() && rand.nextBoolean()) {
                    folder = userFolders.get(rand.nextInt(userFolders.size()));
                }
            }

            VisibilityType visibility = rand.nextInt(10) < 6
                    ? VisibilityType.PUBLIC
                    : VisibilityType.PRIVATE;

            Document d = Document.builder()
                    .user(owner)
                    .group(group)
                    .folder(folder)
                    .title(title)
                    .fileName(slugify(title) + "." + fileType.name().toLowerCase())
                    .fileType(fileType)
                    .fileSize(fileSize)
                    .fileUrl("demo/placeholder-" + (i + 1) + "." + fileType.name().toLowerCase())
                    .visibility(visibility)
                    .downloadCount(rand.nextInt(50))
                    .moderationStatus(ModerationStatus.APPROVED)
                    .build();
            documentRepository.save(d);
        }
    }

    private String slugify(String s) {
        return s.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[đ]", "d")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    private void seedPayments(List<User> users) {
        String[] planCodes = {"PREMIUM"};
        String[] addonCodes = {"PACK_500MB", "PACK_1GB", "PACK_5GB"};
        long[] planPrices = {50000L};
        long[] addonPrices = {20000L, 35000L, 150000L};

        for (int i = 0; i < PAYMENT_COUNT; i++) {
            User payer = users.get(rand.nextInt(users.size()));
            boolean isPlan = rand.nextInt(10) < 6; // 60% plan, 40% addon
            boolean isMomo = rand.nextBoolean();

            String code;
            long amount;
            PurchaseType pType;
            if (isPlan) {
                int idx = rand.nextInt(planCodes.length);
                code = planCodes[idx];
                amount = planPrices[idx];
                pType = PurchaseType.PLAN;
            } else {
                int idx = rand.nextInt(addonCodes.length);
                code = addonCodes[idx];
                amount = addonPrices[idx];
                pType = PurchaseType.ADDON;
            }

            // 70% SUCCESS, 20% FAILED, 10% PENDING
            int r = rand.nextInt(10);
            PaymentStatus status = r < 7 ? PaymentStatus.SUCCESS
                    : r < 9 ? PaymentStatus.FAILED
                    : PaymentStatus.PENDING;

            Payment p = Payment.builder()
                    .user(payer)
                    .provider(isMomo ? PaymentProvider.MOMO : PaymentProvider.VNPAY)
                    .purchaseType(pType)
                    .scope(PaymentScope.USER)
                    .planCode(isPlan ? code : null)
                    .addonCode(isPlan ? null : code)
                    .amountVnd(amount)
                    .currency("VND")
                    .txnRef("DEMO" + System.nanoTime() + i)
                    .status(status)
                    .ipnReceivedAt(status == PaymentStatus.SUCCESS ? LocalDateTime.now() : null)
                    .failureReason(status == PaymentStatus.FAILED ? "Mã giao dịch bị huỷ bởi người dùng" : null)
                    .build();
            paymentRepository.save(p);
        }
    }

    // ── Backdate timestamps so admin charts render multi-month series ─────────

    /**
     * BaseEntity.@PrePersist forces createdAt = now() regardless of builder
     * value. After persistence we backdate created_at via native SQL so
     * dashboard charts have spread across the last 12 months.
     */
    private void backdateTimestamps() {
        // Users: spread across 12 months
        em.createNativeQuery(
                "UPDATE users SET created_at = NOW() - (random() * INTERVAL '365 days'), " +
                        "                  updated_at = created_at " +
                        "WHERE email LIKE 'demo%@filesharer.local'")
                .executeUpdate();

        // Groups: spread across 9 months
        em.createNativeQuery(
                "UPDATE user_groups SET created_at = NOW() - (random() * INTERVAL '270 days'), " +
                        "                       updated_at = created_at " +
                        "WHERE owner_id IN (SELECT id FROM users WHERE email LIKE 'demo%@filesharer.local')")
                .executeUpdate();

        // Group members: spread across 6 months
        em.createNativeQuery(
                "UPDATE group_members SET created_at = NOW() - (random() * INTERVAL '180 days'), " +
                        "                         updated_at = created_at, " +
                        "                         joined_at = created_at " +
                        "WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'demo%@filesharer.local')")
                .executeUpdate();

        // Documents: spread across 12 months
        em.createNativeQuery(
                "UPDATE documents SET created_at = NOW() - (random() * INTERVAL '365 days'), " +
                        "                     updated_at = created_at " +
                        "WHERE file_url LIKE 'demo/placeholder-%'")
                .executeUpdate();

        // Folders: spread across 12 months
        em.createNativeQuery(
                "UPDATE folders SET created_at = NOW() - (random() * INTERVAL '365 days'), " +
                        "                   updated_at = created_at " +
                        "WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'demo%@filesharer.local')")
                .executeUpdate();

        // Payments: spread across 12 months for monthly revenue chart
        em.createNativeQuery(
                "UPDATE payments SET created_at = NOW() - (random() * INTERVAL '365 days'), " +
                        "                    updated_at = created_at, " +
                        "                    ipn_received_at = CASE WHEN status = 'SUCCESS' THEN created_at + INTERVAL '5 seconds' ELSE NULL END " +
                        "WHERE txn_ref LIKE 'DEMO%'")
                .executeUpdate();
    }
}
