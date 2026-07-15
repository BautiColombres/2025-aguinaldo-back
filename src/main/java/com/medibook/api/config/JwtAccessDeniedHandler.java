package com.medibook.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Returns a fixed, generic 403 body for authenticated-but-not-authorized requests
 * (wrong role, cross-tenant access, or a non-ACTIVE account — see
 * {@link ActiveUserAuthorizationManager}).
 *
 * <p>Mirrors {@link JwtAuthenticationEntryEndpoint}: the raw {@link AccessDeniedException}
 * message is NEVER reflected into the response (information-disclosure hardening) and the body is
 * produced via Jackson serialization, not string concatenation. In particular the caller is never
 * told WHY they were denied — a probing client cannot distinguish "your account is PENDING" from
 * "wrong role" from "not your resource".
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        Map<String, String> body = Map.of(
                "error", "FORBIDDEN",
                "message", "Access denied");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
