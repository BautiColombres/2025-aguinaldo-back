package com.medibook.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.User;
import com.medibook.api.repository.RefreshTokenRepository;
import com.medibook.api.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
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
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG-001 — an authenticated-but-not-ACTIVE user (PENDING doctor, DISABLED user) must be
 * denied with <b>403 Forbidden</b>, not 401.
 *
 * <p>401 stays reserved for the truly unauthenticated cases (no token / malformed token /
 * expired token). The denial itself must remain TOTAL: only the status code changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NonActiveUserAuthorizationTest {

    /** Mirrors {@code jwt.secret} in application-test.properties. */
    private static final String TEST_JWT_SECRET = "test-jwt-secret-key-for-testing-purposes-only";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User pendingDoctor;
    private User activeDoctor;
    private User otherActiveDoctor;
    private User disabledPatient;

    private Session pendingDoctorSession;
    private Session activeDoctorSession;
    private Session disabledPatientSession;

    private String pendingDoctorToken;
    private String activeDoctorToken;
    private String disabledPatientToken;

    /** An access token plus the raw refresh-token value carried by the httpOnly cookie. */
    private record Session(String accessToken, String refreshToken) {}

    @BeforeEach
    void setUp() throws Exception {
        // A PENDING doctor is explicitly allowed to sign in (AuthServiceImpl#isUserAuthorizedToSignIn),
        // so they legitimately hold a VALID access token while awaiting admin approval.
        pendingDoctor = createUser("pending-doctor@example.com", 60000001L, "DOCTOR", "PENDING");
        activeDoctor = createUser("active-doctor@example.com", 60000002L, "DOCTOR", "ACTIVE");
        otherActiveDoctor = createUser("other-active-doctor@example.com", 60000003L, "DOCTOR", "ACTIVE");

        pendingDoctorSession = signIn(pendingDoctor.getEmail());
        activeDoctorSession = signIn(activeDoctor.getEmail());
        pendingDoctorToken = pendingDoctorSession.accessToken();
        activeDoctorToken = activeDoctorSession.accessToken();

        // A DISABLED user cannot sign in, but may still hold a token issued while ACTIVE
        // (e.g. an admin disables the account mid-session). Simulate exactly that.
        disabledPatient = createUser("disabled-patient@example.com", 60000004L, "PATIENT", "ACTIVE");
        disabledPatientSession = signIn(disabledPatient.getEmail());
        disabledPatientToken = disabledPatientSession.accessToken();
        disabledPatient.setStatus("DISABLED");
        userRepository.saveAndFlush(disabledPatient);
    }

    // ---- PENDING doctor: authenticated but not authorized -> 403 ----

    @Test
    void pendingDoctor_validToken_doctorPatientsEndpoint_returns403NotUnauthorized() throws Exception {
        mockMvc.perform(get("/api/doctors/" + pendingDoctor.getId() + "/patients")
                        .header("Authorization", "Bearer " + pendingDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingDoctor_validToken_followupsEndpoint_returns403AndNoData() throws Exception {
        mockMvc.perform(get("/api/doctors/" + pendingDoctor.getId() + "/followups")
                        .header("Authorization", "Bearer " + pendingDoctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void pendingDoctor_validToken_dueForFollowupPanel_returns403AndNoData() throws Exception {
        mockMvc.perform(get("/api/doctors/" + pendingDoctor.getId() + "/patients/due-for-followup")
                        .header("Authorization", "Bearer " + pendingDoctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    /**
     * {@code GET /api/doctors} has NO {@code @PreAuthorize} — it is protected only by the
     * request-level {@code anyRequest().authenticated()} rule. A PENDING user must still be
     * denied here, which proves the ACTIVE gate lives at the AUTHORIZATION layer and is not
     * merely a side effect of method security.
     */
    @Test
    void pendingDoctor_validToken_endpointWithoutMethodSecurity_returns403AndNoData() throws Exception {
        mockMvc.perform(get("/api/doctors")
                        .header("Authorization", "Bearer " + pendingDoctorToken))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("active-doctor@example.com"))));
    }

    @Test
    void pendingDoctor_403Body_isGenericAndDisclosesNothing() throws Exception {
        mockMvc.perform(get("/api/doctors/" + pendingDoctor.getId() + "/patients")
                        .header("Authorization", "Bearer " + pendingDoctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                // No account-state / user detail disclosure.
                .andExpect(content().string(not(containsString("PENDING"))))
                .andExpect(content().string(not(containsString("pending-doctor@example.com"))))
                .andExpect(content().string(not(containsString("status"))));
    }

    // ---- DISABLED user: authenticated but not authorized -> 403 ----

    @Test
    void disabledUser_validToken_returns403AndNoData() throws Exception {
        mockMvc.perform(get("/api/turns/my-turns")
                        .header("Authorization", "Bearer " + disabledPatientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void disabledUser_validToken_patientFollowups_returns403AndNoData() throws Exception {
        mockMvc.perform(get("/api/patients/" + disabledPatient.getId() + "/followups")
                        .header("Authorization", "Bearer " + disabledPatientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    // ---- Regression: truly unauthenticated stays 401 ----

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/doctors/" + activeDoctor.getId() + "/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/doctors/" + activeDoctor.getId() + "/patients")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_returns401() throws Exception {
        mockMvc.perform(get("/api/doctors/" + activeDoctor.getId() + "/patients")
                        .header("Authorization", "Bearer " + expiredTokenFor(activeDoctor)))
                .andExpect(status().isUnauthorized());
    }

    // ---- Regression: existing ACTIVE-doctor authorization preserved ----

    @Test
    void activeDoctor_ownPatients_returns200() throws Exception {
        mockMvc.perform(get("/api/doctors/" + activeDoctor.getId() + "/patients")
                        .header("Authorization", "Bearer " + activeDoctorToken))
                .andExpect(status().isOk());
    }

    /**
     * CONTRACT CHANGE, FE-visible: registering an {@code AccessDeniedHandler} means PRE-EXISTING
     * 403s (cross-tenant, wrong-role) now carry this JSON body too. They previously used Spring's
     * default {@code AccessDeniedHandlerImpl}, which returns an EMPTY body. Any frontend code
     * branching on an empty 403 body must be updated.
     */
    @Test
    void activeDoctor_crossDoctorPatients_returns403_withGenericJsonBody() throws Exception {
        mockMvc.perform(get("/api/doctors/" + otherActiveDoctor.getId() + "/patients")
                        .header("Authorization", "Bearer " + activeDoctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // ---- permitAll routes: the ACTIVE gate does NOT run there, so a non-ACTIVE token
    // ---- now reaches these handlers with a REAL principal. Lock down what that may do.

    /**
     * The tightened ownership check: the filter now authenticates a PENDING user, so
     * {@code AuthController#signOut} finally sees a real {@code callerId} — and must therefore
     * REFUSE to revoke a refresh token belonging to somebody else.
     */
    @Test
    void signOut_pendingDoctorToken_withAnotherUsersRefreshCookie_doesNotRevokeTheirToken() throws Exception {
        assertEquals(1, validRefreshTokens(activeDoctor));

        mockMvc.perform(post("/api/auth/signout")
                        .header("Authorization", "Bearer " + pendingDoctorToken)
                        .cookie(new Cookie("refreshToken", activeDoctorSession.refreshToken())))
                .andExpect(status().isOk());

        assertEquals(1, validRefreshTokens(activeDoctor),
                "a PENDING caller must NOT be able to revoke another user's refresh token");
    }

    /** Regression: a PENDING user signing out of their OWN session still works. */
    @Test
    void signOut_pendingDoctor_withOwnRefreshCookie_stillRevokes() throws Exception {
        assertEquals(1, validRefreshTokens(pendingDoctor));

        mockMvc.perform(post("/api/auth/signout")
                        .header("Authorization", "Bearer " + pendingDoctorToken)
                        .cookie(new Cookie("refreshToken", pendingDoctorSession.refreshToken())))
                .andExpect(status().isOk());

        assertEquals(0, validRefreshTokens(pendingDoctor),
                "a PENDING user must still be able to sign out of their own session");
    }

    /**
     * Defense in depth. A REJECTED doctor holds a 30-day refresh token issued while PENDING.
     * Rotation must be REFUSED and the whole token family revoked — otherwise they could mint a
     * fresh access token forever and the request-level ACTIVE gate would be the only thing
     * standing between them and the API.
     */
    @Test
    void refreshToken_rejectedDoctor_isRefusedAndWholeFamilyRevoked() throws Exception {
        User rejected = createUser("rejected-doctor@example.com", 60000005L, "DOCTOR", "PENDING");
        Session first = signIn(rejected.getEmail());
        signIn(rejected.getEmail()); // a second device -> two live refresh tokens
        assertEquals(2, validRefreshTokens(rejected));

        rejected.setStatus("REJECTED");
        userRepository.saveAndFlush(rejected);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", first.refreshToken())))
                .andExpect(status().isUnauthorized());

        assertEquals(0, validRefreshTokens(rejected),
                "refusing rotation must revoke the ENTIRE refresh-token family, not just the one presented");
    }

    @Test
    void refreshToken_disabledUser_isRefusedAndWholeFamilyRevoked() throws Exception {
        assertEquals(1, validRefreshTokens(disabledPatient));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", disabledPatientSession.refreshToken())))
                .andExpect(status().isUnauthorized());

        assertEquals(0, validRefreshTokens(disabledPatient));
    }

    /**
     * Do NOT break the PENDING path: the frontend must still be able to keep a PENDING doctor's
     * session alive to render the "awaiting approval" screen.
     */
    @Test
    void refreshToken_pendingDoctor_stillSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(new Cookie("refreshToken", pendingDoctorSession.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // rotation: the old token is revoked and a new one issued -> still exactly one live token
        assertEquals(1, validRefreshTokens(pendingDoctor));
    }

    /**
     * The gymcloud route is permitAll and authenticates by API KEY, reusing the
     * {@code Authorization: Bearer} header. A valid MediBook JWT is NOT an API key: now that the
     * filter authenticates non-ACTIVE users too, make sure no principal confusion lets a JWT pass
     * as a key.
     */
    @Test
    void gymcloud_withValidMediBookJwtInsteadOfApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/gymcloud/health-certificate")
                        .header("Authorization", "Bearer " + activeDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + activeDoctor.getEmail() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid API Key"));
    }

    // ---- helpers ----

    private User createUser(String email, long dni, String role, String status) {
        User user = new User();
        user.setEmail(email);
        user.setDni(dni);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setName("Test");
        user.setSurname("User");
        user.setPhone("1234567890");
        user.setBirthdate(LocalDate.of(1990, 1, 1));
        user.setGender("MALE");
        user.setRole(role);
        user.setStatus(status);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private Session signIn(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        // "refreshToken=<raw>; Path=/api/auth; HttpOnly; ..." — the raw value is a
        // url-safe Base64 string WITHOUT padding, so it never contains '='.
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "signin must set the refresh-token cookie");
        String refreshToken = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));

        return new Session(accessToken, refreshToken);
    }

    private long validRefreshTokens(User user) {
        return refreshTokenRepository.countValidTokensForUser(user, ZonedDateTime.now(ARGENTINA_ZONE));
    }

    /** A correctly-signed token that expired an hour ago. */
    private String expiredTokenFor(User user) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        long anHourAgo = System.currentTimeMillis() - 3_600_000L;
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(new Date(anHourAgo - 1000L))
                .expiration(new Date(anHourAgo))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
