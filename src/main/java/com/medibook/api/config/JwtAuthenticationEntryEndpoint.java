package com.medibook.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * BSEC-M-3: returns a fixed, generic 401 body. The raw {@link AuthenticationException}
 * message is NEVER reflected into the response (information-disclosure hardening); the
 * body is produced via Jackson serialization, not string concatenation.
 */
@Component
public class JwtAuthenticationEntryEndpoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryEndpoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, String> body = Map.of(
                "error", "UNAUTHORIZED",
                "message", "Authentication required");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
