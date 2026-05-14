package com.example.fileshareR.config;

import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.StorageAddon;
import com.example.fileshareR.repository.GroupRepository;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.StorageAddonRepository;
import com.example.fileshareR.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class StoragePlanSeeder implements CommandLineRunner {

    private final PlanRepository planRepository;
    private final StorageAddonRepository addonRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(String... args) {
        seedPlans();
        seedAddons();
        assignDefaultPlanToExisting();
        backfillStorageUsed();
    }

    private void seedPlans() {
        upsertPlan("FREE",    "Miễn phí", 100L * 1024 * 1024,        0L,     "Gói miễn phí 100 MB");
        upsertPlan("PREMIUM", "Cao cấp",  1L * 1024 * 1024 * 1024,   50000L, "Gói cao cấp 1 GB");
    }

    private void seedAddons() {
        upsertAddon("PACK_500MB", "Gói thêm 500 MB", 500L * 1024 * 1024,        20000L,  "Mua thêm 500 MB lưu trữ");
        upsertAddon("PACK_1GB",   "Gói thêm 1 GB",   1L * 1024 * 1024 * 1024,   35000L,  "Mua thêm 1 GB lưu trữ");
        upsertAddon("PACK_5GB",   "Gói thêm 5 GB",   5L * 1024 * 1024 * 1024,   150000L, "Mua thêm 5 GB lưu trữ");
    }

    private void upsertPlan(String code, String name, long quotaBytes, long priceVnd, String desc) {
        planRepository.findByCode(code).orElseGet(() -> {
            Plan p = Plan.builder()
                    .code(code)
                    .name(name)
                    .quotaBytes(quotaBytes)
                    .priceVnd(priceVnd)
                    .description(desc)
                    .build();
            log.info("Seeding plan: {}", code);
            return planRepository.save(p);
        });
    }

    private void upsertAddon(String code, String name, long extraBytes, long priceVnd, String desc) {
        addonRepository.findByCode(code).orElseGet(() -> {
            StorageAddon a = StorageAddon.builder()
                    .code(code)
                    .name(name)
                    .extraBytes(extraBytes)
                    .priceVnd(priceVnd)
                    .description(desc)
                    .build();
            log.info("Seeding addon: {}", code);
            return addonRepository.save(a);
        });
    }

    private void assignDefaultPlanToExisting() {
        Plan free = planRepository.findByCode("FREE").orElseThrow();
        int u = em.createQuery("UPDATE User u SET u.plan = :p WHERE u.plan IS NULL")
                .setParameter("p", free)
                .executeUpdate();
        int g = em.createQuery("UPDATE Group g SET g.plan = :p WHERE g.plan IS NULL")
                .setParameter("p", free)
                .executeUpdate();
        if (u > 0 || g > 0) {
            log.info("Assigned FREE plan to {} users and {} groups", u, g);
        }
    }

    private void backfillStorageUsed() {
        // User: storage_used = sum of personal docs (group_id IS NULL)
        int u = em.createNativeQuery(
                "UPDATE users u SET storage_used = COALESCE(" +
                        "(SELECT SUM(d.file_size) FROM documents d " +
                        " WHERE d.user_id = u.id AND d.group_id IS NULL), 0) " +
                        "WHERE storage_used = 0")
                .executeUpdate();
        // Group: storage_used = sum of docs in that group
        int g = em.createNativeQuery(
                "UPDATE user_groups g SET storage_used = COALESCE(" +
                        "(SELECT SUM(d.file_size) FROM documents d WHERE d.group_id = g.id), 0) " +
                        "WHERE storage_used = 0")
                .executeUpdate();
        if (u > 0 || g > 0) {
            log.info("Backfilled storage_used for {} users and {} groups", u, g);
        }
    }
}
