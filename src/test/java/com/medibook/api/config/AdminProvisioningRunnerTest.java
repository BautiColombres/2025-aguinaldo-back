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
 *  - create the admin from env vars (BCrypt-encoded),
 *  - be idempotent: updating (not duplicating) an existing admin on re-run.
 *
 * <p>The former {@code mustResetPassword} flag was REMOVED (changelog
 * {@code 0017-drop-must-reset-password}): it was set here but never enforced at
 * signin, i.e. a dormant security control that misled readers into believing
 * first-login reset was covered. See {@link #userEntityHasNoDormantMustResetPasswordFlag()}.
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
    }

    /**
     * Regression guard for the Phase 6 product decision: the {@code mustResetPassword}
     * flag was removed rather than wired up.
     *
     * <p>It used to be persisted on {@code users.must_reset_password} and set to
     * {@code true} by this runner, but NOTHING ever read it — signin never forced a
     * reset. A security control that looks present but does nothing is worse than no
     * control: it makes the next reader assume first-login reset is handled.
     *
     * <p>Do not reintroduce the field on its own. If forced reset is ever built, it
     * needs the full flow (persisted state + a signin gate + a blocking "set new
     * password" screen) landing together.
     */
    @Test
    void userEntityHasNoDormantMustResetPasswordFlag() {
        boolean declaresFlag = java.util.Arrays.stream(User.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equalsIgnoreCase("mustResetPassword"));

        assertFalse(declaresFlag,
                "User must not declare a mustResetPassword field: the flag was removed because it "
                        + "was never enforced at signin. Reintroduce it only together with a real "
                        + "forced-reset flow (and a Liquibase changelog re-adding the column).");
    }
}
