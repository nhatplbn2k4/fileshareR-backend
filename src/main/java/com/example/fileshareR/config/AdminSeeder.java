package com.example.fileshareR.config;

import com.example.fileshareR.entity.Plan;
import com.example.fileshareR.entity.User;
import com.example.fileshareR.enums.AuthProvider;
import com.example.fileshareR.enums.UserRole;
import com.example.fileshareR.repository.PlanRepository;
import com.example.fileshareR.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the single platform-admin account on first boot. Idempotent — if the
 * email already exists, the bean does nothing. Runs after StoragePlanSeeder
 * (@Order(1)) so the FREE plan exists.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String ADMIN_FULL_NAME = "Platform Admin";

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            log.debug("Admin {} already exists, skipping seed", ADMIN_EMAIL);
            return;
        }

        Plan freePlan = planRepository.findByCode("FREE").orElse(null);

        User admin = User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName(ADMIN_FULL_NAME)
                .role(UserRole.ADMIN)
                .isActive(true)
                .emailVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .plan(freePlan)
                .build();

        userRepository.save(admin);
        log.info("Seeded platform admin: {} (role=ADMIN)", ADMIN_EMAIL);
    }
}
