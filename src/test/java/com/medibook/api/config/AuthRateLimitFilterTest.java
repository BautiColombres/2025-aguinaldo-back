package com.medibook.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.dto.Auth.SignInRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * BSEC-M-4 — in-app Bucket4j rate limiting on /api/auth/**.
 * A low per-IP limit is configured for this test so the bucket can be exhausted.
 *
 * <p>By default NO trusted proxy is configured, so the spoofable
 * {@code X-Forwarded-For} header MUST be ignored and the limit enforced by the
 * real peer address (remoteAddr).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "auth.ratelimit.capacity=3",
        "auth.ratelimit.refill-minutes=1",
        "auth.ratelimit.trusted-proxies="
})
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String signinBody() throws Exception {
        return objectMapper.writeValueAsString(
                new SignInRequestDTO("nobody@example.com", "wrong-password"));
    }

    private String signinBody(String email) throws Exception {
        return objectMapper.writeValueAsString(new SignInRequestDTO(email, "wrong-password"));
    }

    @Test
    void underLimitDoesNotReturn429() throws Exception {
        String body = signinBody();
        for (int i = 0; i < 3; i++) {
            MvcResult result = mockMvc.perform(post("/api/auth/signin")
                            .with(req -> { req.setRemoteAddr("10.0.0.1"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            assertTrue(result.getResponse().getStatus() != 429,
                    "request " + i + " should not be rate-limited (status="
                            + result.getResponse().getStatus() + ")");
        }
    }

    @Test
    void exceedingLimitReturns429() throws Exception {
        String body = signinBody();
        int status = 200;
        for (int i = 0; i < 5; i++) {
            status = mockMvc.perform(post("/api/auth/signin")
                            .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        }
        assertEquals(429, status, "exceeding the auth rate limit must return 429");
    }

    @Test
    void separateClientsHaveSeparateBuckets() throws Exception {
        // Distinct emails so only the per-IP dimension is under test (not the
        // per-account dimension, which is independent of IP by design).
        // Exhaust client A.
        String bodyA = signinBody("clientA@example.com");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/signin")
                    .with(req -> { req.setRemoteAddr("10.0.0.3"); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyA));
        }
        // Client B (different IP and different account) still allowed.
        int status = mockMvc.perform(post("/api/auth/signin")
                        .with(req -> { req.setRemoteAddr("10.0.0.4"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody("clientB@example.com")))
                .andReturn().getResponse().getStatus();
        assertTrue(status != 429, "a different client IP must not be rate-limited");
    }

    /**
     * Same real peer (remoteAddr) but a rotating, spoofed X-Forwarded-For per
     * request. With no trusted proxy configured the header must be ignored, so
     * the bucket is keyed on the real peer and the limit is still hit.
     */
    @Test
    void spoofedForwardedForDoesNotBypassLimit() throws Exception {
        int status = 200;
        for (int i = 0; i < 5; i++) {
            final String spoof = "203.0.113." + i;
            // Distinct email per request so only the per-peer IP dimension can
            // trip; proves the spoofed header did not mint fresh IP buckets.
            final String body = signinBody("spoof" + i + "@example.com");
            status = mockMvc.perform(post("/api/auth/signin")
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.5");
                                req.addHeader("X-Forwarded-For", spoof);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        }
        assertEquals(429, status,
                "a rotating spoofed X-Forwarded-For must NOT create fresh buckets / bypass the limit");
    }
}
