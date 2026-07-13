package com.medibook.api.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Builds/reads/clears the httpOnly refresh-token cookie.
 *
 * <p>The cookie is scoped to {@code Path=/api/auth} so it is only ever sent to the
 * three auth endpoints (signin sets it, refresh-token rotates it, signout clears it).
 * Every other endpoint authenticates via the {@code Authorization} header — this scoping
 * is what makes the CSRF story trivial (SameSite=Strict + header-only elsewhere).
 *
 * <p>{@code Secure} is driven by the {@code app.cookie.secure} property (off in DEV, on in
 * prod) rather than inferred from the request scheme, because the app may sit behind a
 * TLS-terminating proxy. {@code SameSite} is driven by {@code app.cookie.same-site} so a
 * future cross-domain deploy is a one-line config change (Strict -> None).
 */
@Component
public class RefreshTokenCookieUtil {

    public static final String COOKIE_NAME = "refreshToken";
    public static final String COOKIE_PATH = "/api/auth";
    /** 30 days, matches the server-side refresh-token TTL. */
    public static final long MAX_AGE_SECONDS = 2592000L;

    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieUtil(
            @Value("${app.cookie.secure:true}") boolean secure,
            @Value("${app.cookie.same-site:Strict}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie build(String rawToken) {
        return baseBuilder(rawToken)
                .maxAge(Duration.ofSeconds(MAX_AGE_SECONDS))
                .build();
    }

    public ResponseCookie clear() {
        return baseBuilder("")
                .maxAge(0)
                .build();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH);
    }
}
