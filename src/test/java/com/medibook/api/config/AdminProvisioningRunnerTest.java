package com.medibook.api.config;

import com.medibook.api.entity.User;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Env-driven admin provisioning. The runner must:
 *  - fail fast when ADMIN_EMAIL / ADMIN_PASSWORD are absent,
 *  - create the admin from env vars (BCrypt-encoded, mustResetPassword=true),
 *  - be idempotent: updating (not duplicating) an existing admin on re-run.
 */
class AdminProvisioningRunnerTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
    }

    private AdminProvisioningRunner runner(String email, String password) {
        return new AdminProvisioningRunner(userRepository, passwordEncoder, email, password, 99999999L);
    }

    @Test
    void failsFastWhenEmailMissing() {
        AdminProvisioningRunner runner = runner("", "Secret123!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, runner::provisionAdmin);
        assertTrue(ex.getMessage().toLowerCase().contains("admin"));
    }

    @Test
    void failsFastWhenPasswordMissing() {
        AdminProvisioningRunner runner = runner("admin@medibook.com", "  ");
        assertThrows(IllegalStateException.class, runner::provisionAdmin);
    }

    @Test
    void createsAdminFromEnvWhenAbsent() {
        when(userRepository.findByEmail("admin@medibook.com")).thenReturn(Optional.empty());
        when(userRepository.existsByDni(any())).thenReturn(false);

        runner("admin@medibook.com", "Secret123!").provisionAdmin();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("admin@medibook.com", saved.getEmail());
        assertEquals("ADMIN", saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
        assertTrue(saved.isMustResetPassword(), "provisioned admin must be flagged mustResetPassword");
        assertTrue(saved.isEmailVerified(), "provisioned admin must be email-verified to be able to log in");
        assertFalse(saved.getPasswordHash().equals("Secret123!"), "password must be encoded");
        assertTrue(passwordEncoder.matches("Secret123!", saved.getPasswordHash()),
                "stored hash must verify against the env password");
    }

    @Test
    void updatesExistingAdminPasswordIdempotently() {
        User existing = new User();
        existing.setEmail("admin@medibook.com");
        existing.setRole("ADMIN");
        existing.setStatus("ACTIVE");
        existing.setPasswordHash("$2a$10$oldhasholdhasholdhasholdhasholdhasholdhash");
        when(userRepository.findByEmail("admin@medibook.com")).thenReturn(Optional.of(existing));

        runner("admin@medibook.com", "NewSecret123!").provisionAdmin();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertTrue(passwordEncoder.matches("NewSecret123!", saved.getPasswordHash()));
        assertTrue(saved.isMustResetPassword());
        // No new DNI assignment / no duplicate creation: same instance updated.
        verify(userRepository, never()).existsByDni(any());
    }

    @Test
    void updatePathMarksExistingAdminEmailVerifiedSoItCanLogIn() {
        // Regression: an existing admin row seeded with emailVerified=false must be
        // flipped to verified on the update path, otherwise signIn rejects it with 401.
        User existing = new User();
        existing.setEmail("admin@medibook.com");
        existing.setRole("ADMIN");
        existing.setStatus("ACTIVE");
        existing.setEmailVerified(false);
        existing.setPasswordHash("$2a$10$oldhasholdhasholdhasholdhasholdhasholdhash");
        when(userRepository.findByEmail("admin@medibook.com")).thenReturn(Optional.of(existing));

        runner("admin@medibook.com", "NewSecret123!").provisionAdmin();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertTrue(saved.isEmailVerified(),
                "update path must set emailVerified=true so the provisioned admin can authenticate");
        assertEquals("ACTIVE", saved.getStatus());
        assertTrue(saved.isMustResetPassword());
    }
}
