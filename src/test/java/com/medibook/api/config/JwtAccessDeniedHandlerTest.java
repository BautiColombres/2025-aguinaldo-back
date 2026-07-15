package com.medibook.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The 403 handler must NEVER reflect the raw exception message
 * (information disclosure). It mirrors {@link JwtAuthenticationEntryEndpoint}: a fixed
 * generic body emitted via a serialized object, not string concatenation.
 */
class JwtAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        accessDeniedHandler = new JwtAccessDeniedHandler(objectMapper);
    }

    @Test
    void handle_returnsGenericForbiddenBody() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response,
                new AccessDeniedException("User 42 has status PENDING and lacks ROLE_DOCTOR"));

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentType().contains("application/json"));

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertEquals("FORBIDDEN", json.get("error").asText());
        assertEquals("Access denied", json.get("message").asText());
    }

    @Test
    void handle_doesNotLeakExceptionMessageNorAccountState() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String secret = "account status PENDING for doctor@example.com";

        accessDeniedHandler.handle(request, response, new AccessDeniedException(secret));

        String body = response.getContentAsString();
        assertFalse(body.contains(secret), "response body must not reflect the exception message");
        assertFalse(body.contains("PENDING"), "response body must not disclose account state");
        assertFalse(body.contains("doctor@example.com"));
    }

    @Test
    void handle_nullExceptionMessage_stillGeneric() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException(null));

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertEquals("Access denied", json.get("message").asText());
        assertFalse(response.getContentAsString().contains("null"));
    }
}
