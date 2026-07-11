package com.medibook.api.config;

import com.medibook.api.entity.User;
import com.medibook.api.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * BSEC-H-4 — env-driven admin provisioning.
 *
 * <p>Replaces the committed seed admin (whose static BCrypt hash lived in
 * {@code 0005-insert-admin.xml} and is neutralized by {@code 0011-*.xml}). On
 * startup this creates-or-updates the admin from {@code ADMIN_EMAIL} /
 * {@code ADMIN_PASSWORD} (BCrypt-encoded), idempotently, and flags the account
 * {@code mustResetPassword=true}.
 *
 * <p><b>Fail-fast:</b> the application refuses to boot if the required admin
 * bootstrap credentials are absent.
 *
 * <p>Follow-up (out of scope for this batch): wire {@code mustResetPassword}
 * into a forced password-reset flow on first login.
 */
@Component
@Slf4j
public class AdminProvisioningRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final Long adminDni;

    public AdminProvisioningRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.bootstrap.email:}") String adminEmail,
            @Value("${admin.bootstrap.password:}") String adminPassword,
            @Value("${admin.bootstrap.dni:99999999}") Long adminDni) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminDni = adminDni;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisionAdmin();
    }

    @Transactional
    public void provisionAdmin() {
        validate();

        User admin = userRepository.findByEmail(adminEmail.trim()).orElse(null);
        boolean creating = admin == null;
        if (creating) {
            admin = new User();
            admin.setEmail(adminEmail.trim());
            admin.setName("Admin");
            admin.setSurname("MediBook");
            admin.setRole("ADMIN");
            admin.setStatus("ACTIVE");
            admin.setDni(resolveDni());
        }

        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");
        // Must be set on BOTH paths: an existing admin row seeded with
        // emailVerified=false would otherwise be locked out by signIn (401).
        admin.setEmailVerified(true);
        admin.setMustResetPassword(true);

        userRepository.save(admin);
        log.info("Admin account provisioned from environment ({} existing admin).",
                creating ? "created new" : "updated");
    }

    private Long resolveDni() {
        if (adminDni != null && !userRepository.existsByDni(adminDni)) {
            return adminDni;
        }
        // Fallback: derive a unique-ish DNI to satisfy the NOT NULL/UNIQUE constraint
        // without colliding with an existing user.
        long candidate = adminDni != null ? adminDni : 99999999L;
        while (userRepository.existsByDni(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private void validate() {
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException(
                    "Admin bootstrap is not configured: set the ADMIN_EMAIL environment variable. "
                            + "The application refuses to start without it (no committed admin credential).");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Admin bootstrap is not configured: set the ADMIN_PASSWORD environment variable. "
                            + "The application refuses to start without it (no committed admin credential).");
        }
    }
}
