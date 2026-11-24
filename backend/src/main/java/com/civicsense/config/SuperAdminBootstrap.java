package com.civicsense.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.Role;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;

/**
 * Creates the first SUPER_ADMIN from local .env values at startup.
 * Disabled in the test profile; tests create their own admin fixture.
 * Never logs the password and never overwrites an existing password.
 */
@Component
@Profile("!test")
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public SuperAdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Value("${civicsense.super-admin.email:}") String email,
            @org.springframework.beans.factory.annotation.Value("${civicsense.super-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Super-admin bootstrap skipped: CIVICSENSE_SUPERADMIN_EMAIL / PASSWORD not set. " +
                    "Set both in the local .env to create the first administrator.");
            return;
        }

        String normalized = email.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(normalized);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.SUPER_ADMIN || user.getAccountStatus() != AccountStatus.ACTIVE) {
                user.setRole(Role.SUPER_ADMIN);
                user.setAccountStatus(AccountStatus.ACTIVE);
                userRepository.save(user);
                log.info("Promoted existing account to SUPER_ADMIN");
            }
            return;
        }

        User admin = new User();
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.SUPER_ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setFullName("Super Administrator");
        admin.setLocale("en");
        userRepository.save(admin);
        log.info("Created bootstrap SUPER_ADMIN account");
    }
}
