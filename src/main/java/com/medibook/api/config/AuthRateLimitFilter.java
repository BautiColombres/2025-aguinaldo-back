package com.medibook.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medibook.api.service.RateLimitService;
import com.medibook.api.util.TrustedProxyResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * BSEC-M-4 — per-client Bucket4j rate-limiting filter for {@code /api/auth/**}.
 * Returns HTTP 429 with a JSON body when the limit is exceeded. Does NOT assume a
 * WAF/gateway: limiting happens in-app.
 *
 * <p><b>Spoofing hardening:</b> the client IP is resolved via
 * {@link TrustedProxyResolver}, which ignores {@code X-Forwarded-For} unless the
 * immediate peer is in the configured trusted-proxy allowlist (default empty).
 * This stops a client from minting fresh buckets / churning the bounded LRU by
 * spoofing the header.
 *
 * <p><b>Per-account dimension:</b> for {@code POST /api/auth/signin} a second
 * token is consumed against a {@code user:<email>} key so per-account brute force
 * is throttled even from rotating IPs. The request body is cached so the
 * controller can still read it downstream.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final String SIGNIN_PATH = "/api/auth/signin";

    private final RateLimitService rateLimitService;
    private final TrustedProxyResolver trustedProxyResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthRateLimitFilter(RateLimitService rateLimitService,
                               TrustedProxyResolver trustedProxyResolver) {
        this.rateLimitService = rateLimitService;
        this.trustedProxyResolver = trustedProxyResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = trustedProxyResolver.resolveClientIp(
                request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));

        HttpServletRequest effectiveRequest = request;
        String usernameKey = null;

        if (isSignin(request)) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            effectiveRequest = cached;
            String email = extractEmail(cached.getCachedBody());
            if (email != null && !email.isBlank()) {
                usernameKey = "user:" + email.trim().toLowerCase(Locale.ROOT);
            }
        }

        // Per-IP dimension (always) + per-account dimension (signin only).
        boolean allowed = rateLimitService.tryConsume("ip:" + clientIp);
        if (allowed && usernameKey != null) {
            allowed = rateLimitService.tryConsume(usernameKey);
        }

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many requests. Please try again later.\"}");
            return;
        }
        filterChain.doFilter(effectiveRequest, response);
    }

    private boolean isSignin(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && SIGNIN_PATH.equals(request.getRequestURI());
    }

    private String extractEmail(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode email = node.get("email");
            return email != null && email.isTextual() ? email.asText() : null;
        } catch (IOException e) {
            return null; // malformed body — fall back to IP-only limiting
        }
    }

    /**
     * Wraps a request so its body can be read by this filter and again by the
     * controller. The body is buffered once into memory.
     */
    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
        }
    }
}
