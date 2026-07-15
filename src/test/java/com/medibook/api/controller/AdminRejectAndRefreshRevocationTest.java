package com.medibook.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.User;
import com.medibook.api.repository.RefreshTokenRepository;
import com.medibook.api.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh-token revocation must actually COMMIT.
 *
 * <p>DELIBERATELY NOT {@code @Transactional}. A class-level test transaction would make the
 * production {@code @Transactional} boundaries merely PARTICIPATE in it, so every assertion would
 * read the DB pre-commit and could not distinguish "committed" from "written but rolled back at
 * commit". That distinction is the whole point of
 * {@code noRollbackFor = AccountNotEligibleException.class} (the refusal path writes and THEN
 * throws) and of {@code @Transactional} on {@code AdminController#rejectDoctor} (whose
 * {@code @Modifying} bulk update has no tx attribute of its own). Everything created here is
 * therefore cleaned up explicitly in {@link #cleanUp()}.
 *
 * <p>Sanity-checked: removing {@code noRollbackFor} turns
 * {@link #refreshToken_rejectedDoctor_revocationSurvivesTheThrow_andIsCommitted()} red.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRejectAndRefreshRevocationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final List<UUID> createdUserIds = new ArrayList<>();

    private User admin;
    private User doctor;
    private String adminToken;

    private record Session(String accessToken, String refreshToken) {}

    @BeforeEach
    void setUp() throws Exception {
        admin = createUser("commit-admin@example.com", 71000001L, "ADMIN", "ACTIVE");
        doctor = createUser("commit-pending-doctor@example.com", 71000002L, "DOCTOR", "PENDING");
        adminToken = signIn(admin.getEmail()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        // No ambient transaction => nothing rolls back for us.
        for (UUID id : createdUserIds) {
            refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserId(id));
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
    }

    /**
     * BLOCKER 2. The refusal path REVOKES the family and then THROWS. Read back from a fresh
     * transaction (no ambient tx), this proves the revocation was actually COMMITTED and not undone
     * by the exception's rollback.
     */
    @Test
    void refreshToken_rejectedDoctor_revocationSurvivesTheThrow_andIsCommitted() throws Exception {
        Session first = signIn(doctor.getEmail());
        signIn(doctor.getEmail()); // second device -> two live refresh tokens
        assertEquals(2, validRefreshTokens(doctor));

        // Rejected directly in the DB: isolate the refreshToken() path from the admin endpoint.
        doctor.setStatus("REJECTED");
        userRepository.save(doctor);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", first.refreshToken())))
                .andExpect(status().isUnauthorized());

        assertEquals(0, validRefreshTokens(doctor),
                "the whole refresh-token family must be revoked AND the revocation must survive "
                        + "the throw (noRollbackFor). A rolled-back revocation would leave 2 here.");
    }

    /** The PENDING path must keep working across a real commit boundary too. */
    @Test
    void refreshToken_pendingDoctor_stillRotates_andCommits() throws Exception {
        Session session = signIn(doctor.getEmail());
        assertEquals(1, validRefreshTokens(doctor));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // old token revoked + new one issued -> still exactly one live token
        assertEquals(1, validRefreshTokens(doctor));
    }

    /**
     * BLOCKER 1. {@code POST /api/admin/reject-doctor/{id}} had ZERO coverage. Its correctness now
     * depends on the transaction proxy: {@code revokeAllTokensByUserId} is a {@code @Modifying}
     * bulk update with no tx attribute of its own, so without {@code @Transactional} on the handler
     * it throws {@code TransactionRequiredException}, which the broad {@code catch (Exception e)}
     * would swallow into a 500 REJECTION_FAILED.
     */
    @Test
    void rejectDoctor_persistsStatus_revokesTokenFamily_andKillsTheSession() throws Exception {
        Session session = signIn(doctor.getEmail());
        signIn(doctor.getEmail()); // two live sessions
        assertEquals(2, validRefreshTokens(doctor));

        mockMvc.perform(post("/api/admin/reject-doctor/" + doctor.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newStatus").value("REJECTED"));

        // Committed status flip.
        User reloaded = userRepository.findById(doctor.getId()).orElseThrow();
        assertEquals("REJECTED", reloaded.getStatus());

        // Committed family-wide revocation (would be 2 if the @Modifying update never ran).
        assertEquals(0, validRefreshTokens(doctor),
                "rejecting must revoke the doctor's entire refresh-token family");

        // And the session is really dead: the cookie no longer buys a new access token.
        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private long validRefreshTokens(User user) {
        return refreshTokenRepository.countValidTokensForUser(user, ZonedDateTime.now(ARGENTINA_ZONE));
    }

    private User createUser(String email, long dni, String role, String status) {
        User user = new User();
        user.setEmail(email);
        user.setDni(dni);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setName("Commit");
        user.setSurname("Test");
        user.setPhone("1234567890");
        user.setBirthdate(LocalDate.of(1990, 1, 1));
        user.setGender("MALE");
        user.setRole(role);
        user.setStatus(status);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private Session signIn(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "signin must set the refresh-token cookie");
        String refreshToken = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));

        return new Session(accessToken, refreshToken);
    }
}
