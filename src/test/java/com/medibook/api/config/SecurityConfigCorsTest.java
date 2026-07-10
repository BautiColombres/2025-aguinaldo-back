package com.medibook.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BSEC-M-2 / FSEC-H1: CORS must pin origins, allow credentials (for the refresh
 * cookie), and use explicit method/header allow-lists instead of wildcards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String PINNED_ORIGIN = "http://localhost:5173";

    @Test
    void preflight_pinnedOrigin_allowsCredentialsAndEchoesOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/signin")
                .header("Origin", PINNED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PINNED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflight_allowedMethods_areExplicit_notWildcard() throws Exception {
        var result = mockMvc.perform(options("/api/auth/signin")
                .header("Origin", PINNED_ORIGIN)
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andReturn();

        String allowMethods = result.getResponse().getHeader("Access-Control-Allow-Methods");
        org.junit.jupiter.api.Assertions.assertNotNull(allowMethods);
        org.junit.jupiter.api.Assertions.assertFalse(allowMethods.contains("*"),
                "CORS methods must not be a wildcard: " + allowMethods);
        for (String m : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"}) {
            org.junit.jupiter.api.Assertions.assertTrue(allowMethods.contains(m),
                    "CORS methods must include " + m + " but was: " + allowMethods);
        }
    }

    @Test
    void preflight_allowedHeaders_areExplicit_notWildcard() throws Exception {
        var result = mockMvc.perform(options("/api/auth/signin")
                .header("Origin", PINNED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andReturn();

        String allowHeaders = result.getResponse().getHeader("Access-Control-Allow-Headers");
        org.junit.jupiter.api.Assertions.assertNotNull(allowHeaders);
        org.junit.jupiter.api.Assertions.assertFalse(allowHeaders.contains("*"),
                "CORS headers must not be a wildcard: " + allowHeaders);
        org.junit.jupiter.api.Assertions.assertTrue(allowHeaders.contains("Authorization"));
        org.junit.jupiter.api.Assertions.assertTrue(allowHeaders.contains("Content-Type"));
        // FSEC-H1 Stage 3: the Refresh-Token header is removed from the CORS allow-list.
        org.junit.jupiter.api.Assertions.assertFalse(allowHeaders.contains("Refresh-Token"),
                "Refresh-Token must no longer be an allowed CORS header: " + allowHeaders);
    }

    @Test
    void preflight_refreshTokenHeader_isNotAllowed() throws Exception {
        // FSEC-H1 Stage 3: a preflight requesting the removed Refresh-Token header must
        // NOT get it echoed back as allowed.
        var result = mockMvc.perform(options("/api/auth/refresh-token")
                .header("Origin", PINNED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Refresh-Token"))
                .andReturn();

        String allowHeaders = result.getResponse().getHeader("Access-Control-Allow-Headers");
        if (allowHeaders != null) {
            org.junit.jupiter.api.Assertions.assertFalse(allowHeaders.contains("Refresh-Token"),
                    "Refresh-Token must not be an allowed CORS header: " + allowHeaders);
        }
    }

    @Test
    void preflight_unknownOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/auth/signin")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
