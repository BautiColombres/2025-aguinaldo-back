package com.medibook.api.config;

import com.medibook.api.entity.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Request-level authorization gate: only an authenticated user whose account status is
 * {@code ACTIVE} may reach a protected endpoint.
 *
 * <p>BUG-001. Previously a non-ACTIVE account (PENDING doctor, DISABLED user) was refused an
 * {@code Authentication} altogether, so the request looked ANONYMOUS and Spring's
 * {@code ExceptionTranslationFilter} answered <b>401 Unauthorized</b> — semantically wrong, since
 * the caller had presented a perfectly valid token. The status check now lives here, at the
 * AUTHORIZATION layer, so such a request is authenticated-but-denied and correctly answered with
 * <b>403 Forbidden</b> by {@link JwtAccessDeniedHandler}. {@link JwtAuthenticationEntryEndpoint}
 * (401) stays reserved for the genuinely unauthenticated cases: no token, malformed token,
 * expired token.
 *
 * <p>The denial itself is UNCHANGED and remains total — a non-ACTIVE user still reads and writes
 * nothing. This runs BEFORE method security, so it also covers endpoints that carry no
 * {@code @PreAuthorize} and rely solely on {@code anyRequest().authenticated()}.
 *
 * <p>Fails CLOSED: a null/anonymous authentication, an unexpected principal type, or an unknown
 * status are all denied.
 */
@Component
public class ActiveUserAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String ACTIVE = "ACTIVE";

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            // Anonymous -> ExceptionTranslationFilter routes to the 401 entry point.
            return new AuthorizationDecision(false);
        }

        // Fail closed on any principal we did not put there ourselves.
        if (!(auth.getPrincipal() instanceof User user)) {
            return new AuthorizationDecision(false);
        }

        return new AuthorizationDecision(ACTIVE.equals(user.getStatus()));
    }
}
