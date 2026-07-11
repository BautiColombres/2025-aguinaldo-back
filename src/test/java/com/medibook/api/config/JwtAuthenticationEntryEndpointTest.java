package com.medibook.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * BSEC-M-3: the 401 entry point must NEVER reflect the raw exception message
 * (information disclosure). It must emit a fixed generic body via a serialized object.
 */
class JwtAuthenticationEntryEndpointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtAuthenticationEntryEndpoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new JwtAuthenticationEntryEndpoint(objectMapper);
    }

    @Test
    void commence_returnsGenericUnauthorizedBody() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException =
                new BadCredentialsException("Table USERS row 42 leaked internal detail");

        entryPoint.commence(request, response, authException);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentType().contains("application/json"));

        String body = response.getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertEquals("UNAUTHORIZED", json.get("error").asText());
        assertEquals("Authentication required", json.get("message").asText());
    }

    @Test
    void commence_doesNotLeakExceptionMessage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String secret = "SELECT * FROM internal_secret_table";
        AuthenticationException authException = new BadCredentialsException(secret);

        entryPoint.commence(request, response, authException);

        String body = response.getContentAsString();
        assertFalse(body.contains(secret), "response body must not reflect the exception message");
        assertFalse(body.contains("internal_secret_table"));
    }

    @Test
    void commence_nullExceptionMessage_stillGeneric() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException(null);

        entryPoint.commence(request, response, authException);

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertEquals("Authentication required", json.get("message").asText());
        assertFalse(response.getContentAsString().contains("null"));
    }
}
