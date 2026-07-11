package com.medibook.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.entity.User;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("admin@example.com", 11111111L, "ADMIN");
        createUser("existing-patient@example.com", 22222222L, "PATIENT");

        adminToken = getAuthToken("admin@example.com", "password123");
        patientToken = getAuthToken("existing-patient@example.com", "password123");
    }

    private String adminRegisterBody(String email, long dni) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"dni\":" + dni + ","
                + "\"password\":\"Password123\","
                + "\"name\":\"New\","
                + "\"surname\":\"Admin\","
                + "\"phone\":\"+1234567890\","
                + "\"birthdate\":\"1985-01-15\","
                + "\"gender\":\"MALE\""
                + "}";
    }

    private String patientRegisterBody(String email, long dni) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"dni\":" + dni + ","
                + "\"password\":\"Password123\","
                + "\"name\":\"New\","
                + "\"surname\":\"Patient\","
                + "\"phone\":\"+1234567890\","
                + "\"birthdate\":\"1990-01-15\","
                + "\"gender\":\"MALE\""
                + "}";
    }

    private String doctorRegisterBody(String email, long dni) {
        return "{"
                + "\"email\":\"" + email + "\","
                + "\"dni\":" + dni + ","
                + "\"password\":\"Password123\","
                + "\"name\":\"New\","
                + "\"surname\":\"Doctor\","
                + "\"phone\":\"+1234567890\","
                + "\"birthdate\":\"1980-01-15\","
                + "\"gender\":\"MALE\","
                + "\"medicalLicense\":\"12345\","
                + "\"specialty\":\"CARDIOLOGÍA\","
                + "\"slotDurationMin\":30"
                + "}";
    }

    // ---- BSEC-C-1 ----

    @Test
    void registerAdmin_Anonymous_IsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRegisterBody("anon-admin@example.com", 33333333L)))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                        status == 401 || status == 403,
                        "Anonymous admin registration must be 401/403 but was " + status);
                });
    }

    @Test
    void registerAdmin_AsNonAdmin_IsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                .header("Authorization", "Bearer " + patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRegisterBody("patient-made-admin@example.com", 44444444L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerAdmin_AsAdmin_IsAllowed() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRegisterBody("admin-made-admin@example.com", 55555555L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---- Defense in depth: other public registrations still work ----

    @Test
    void registerPatient_Anonymous_StillWorks() throws Exception {
        mockMvc.perform(post("/api/auth/register/patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patientRegisterBody("brand-new-patient@example.com", 66666666L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void registerDoctor_Anonymous_StaysPending() throws Exception {
        mockMvc.perform(post("/api/auth/register/doctor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(doctorRegisterBody("brand-new-doctor@example.com", 77777777L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- FSEC-H1 Stage 3: refresh token is cookie-only (no body field, no header) ----

    @Test
    void signIn_setsRefreshTokenCookie_andBodyHasNoRefreshToken() throws Exception {
        var result = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"existing-patient@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                // FSEC-H1 Stage 3: the refresh token is NO LONGER present in the JSON body.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie, "signin must set a refreshToken cookie");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.startsWith("refreshToken="),
                "cookie name must be refreshToken but header was: " + setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"),
                "cookie must be HttpOnly: " + setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Strict"),
                "cookie must be SameSite=Strict: " + setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/api/auth"),
                "cookie must be scoped to Path=/api/auth: " + setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Max-Age=2592000"),
                "cookie must have 30d Max-Age: " + setCookie);
        // The cookie value must be a non-empty token.
        String cookieToken = extractCookieToken(setCookie);
        org.junit.jupiter.api.Assertions.assertNotNull(cookieToken);
        org.junit.jupiter.api.Assertions.assertFalse(cookieToken.isEmpty(),
                "cookie must carry a non-empty refresh token");
    }

    @Test
    void refreshToken_worksFromCookie_setsNewCookie() throws Exception {
        String refresh = signInAndGetRefreshToken("existing-patient@example.com", "password123");

        var result = mockMvc.perform(post("/api/auth/refresh-token")
                .cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // FSEC-H1 Stage 3: rotation result must not leak the refresh token in the body.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie, "refresh must rotate the cookie");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.startsWith("refreshToken="));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/api/auth"));
    }

    @Test
    void refreshToken_headerOnly_isUnauthorized() throws Exception {
        // FSEC-H1 Stage 3: the Refresh-Token header fallback is removed. A request that
        // carries ONLY the old header (no cookie) must now be rejected with 401.
        String refresh = signInAndGetRefreshToken("existing-patient@example.com", "password123");

        mockMvc.perform(post("/api/auth/refresh-token")
                .header("Refresh-Token", refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_missingCookie_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_ignoresHeader_usesCookie() throws Exception {
        String cookieRefresh = signInAndGetRefreshToken("existing-patient@example.com", "password123");

        // A garbage header must be ignored — only the cookie is read.
        mockMvc.perform(post("/api/auth/refresh-token")
                .cookie(new Cookie("refreshToken", cookieRefresh))
                .header("Refresh-Token", "garbage-header-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void signOut_clearsCookie_fromCookie() throws Exception {
        String refresh = signInAndGetRefreshToken("existing-patient@example.com", "password123");

        var result = mockMvc.perform(post("/api/auth/signout")
                .cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie, "signout must clear the cookie");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.startsWith("refreshToken="));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Max-Age=0"),
                "signout cookie must be expired (Max-Age=0): " + setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/api/auth"));
    }

    @Test
    void signOut_ignoresHeader_doesNotRevokeToken() throws Exception {
        // FSEC-H1 Stage 3: signout ignores the Refresh-Token header. A signout carrying
        // only the header stays idempotent (200 + cookie cleared) but does NOT revoke the
        // token — proven by the token still refreshing successfully via the cookie.
        String refresh = signInAndGetRefreshToken("existing-patient@example.com", "password123");

        var result = mockMvc.perform(post("/api/auth/signout")
                .header("Refresh-Token", refresh))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Max-Age=0"));

        // The token was NOT revoked (header ignored) -> still valid via the cookie.
        mockMvc.perform(post("/api/auth/refresh-token")
                .cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isOk());
    }

    @Test
    void signOut_idempotent_whenNoCookie() throws Exception {
        var result = mockMvc.perform(post("/api/auth/signout"))
                .andExpect(status().isOk())
                .andReturn();

        // Still clears any lingering cookie on the client.
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Max-Age=0"));
    }

    // ---- helpers ----

    /**
     * FSEC-H1 Stage 3: the refresh token is no longer in the JSON body — read it from the
     * {@code Set-Cookie} header instead.
     */
    private String signInAndGetRefreshToken(String email, String password) throws Exception {
        String setCookie = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        String token = extractCookieToken(setCookie);
        org.junit.jupiter.api.Assertions.assertNotNull(token, "signin must set a refreshToken cookie");
        return token;
    }

    private String extractCookieToken(String setCookieHeader) {
        if (setCookieHeader == null || !setCookieHeader.startsWith("refreshToken=")) {
            return null;
        }
        String rest = setCookieHeader.substring("refreshToken=".length());
        int semi = rest.indexOf(';');
        return semi >= 0 ? rest.substring(0, semi) : rest;
    }

    private User createUser(String email, long dni, String role) {
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
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private String getAuthToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
