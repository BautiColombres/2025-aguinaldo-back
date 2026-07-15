package com.medibook.api.config;

import com.medibook.api.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ACTIVE gate: only an authenticated user whose account status is ACTIVE is granted.
 * Everyone else is DENIED — the difference between 401 and 403 is decided downstream by
 * Spring's {@code ExceptionTranslationFilter} (anonymous -> entry point 401,
 * authenticated -> access-denied handler 403).
 */
class ActiveUserAuthorizationManagerTest {

    private final ActiveUserAuthorizationManager manager = new ActiveUserAuthorizationManager();

    private final RequestAuthorizationContext context =
            new RequestAuthorizationContext(new org.springframework.mock.web.MockHttpServletRequest());

    @Test
    void activeUser_isGranted() {
        AuthorizationDecision decision = manager.check(() -> authFor("ACTIVE", "DOCTOR"), context);

        assertNotNull(decision);
        assertTrue(decision.isGranted());
    }

    @Test
    void pendingUser_isDenied() {
        AuthorizationDecision decision = manager.check(() -> authFor("PENDING", "DOCTOR"), context);

        assertNotNull(decision);
        assertFalse(decision.isGranted(), "a PENDING account must never be authorized");
    }

    @Test
    void disabledUser_isDenied() {
        AuthorizationDecision decision = manager.check(() -> authFor("DISABLED", "PATIENT"), context);

        assertNotNull(decision);
        assertFalse(decision.isGranted(), "a DISABLED account must never be authorized");
    }

    @Test
    void nullStatus_isDenied() {
        AuthorizationDecision decision = manager.check(() -> authFor(null, "PATIENT"), context);

        assertNotNull(decision);
        assertFalse(decision.isGranted(), "fail closed when the status is unknown");
    }

    @Test
    void anonymous_isDenied() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        AuthorizationDecision decision = manager.check(() -> anonymous, context);

        assertNotNull(decision);
        assertFalse(decision.isGranted());
    }

    @Test
    void nullAuthentication_isDenied() {
        AuthorizationDecision decision = manager.check(() -> null, context);

        assertNotNull(decision);
        assertFalse(decision.isGranted());
    }

    /** An unexpected principal type must fail closed rather than be waved through. */
    @Test
    void nonUserPrincipal_isDenied() {
        Authentication auth = new UsernamePasswordAuthenticationToken("someString", null, List.of());

        AuthorizationDecision decision = manager.check(() -> auth, context);

        assertNotNull(decision);
        assertFalse(decision.isGranted());
    }

    private Authentication authFor(String status, String role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@example.com");
        user.setRole(role);
        user.setStatus(status);
        return new UsernamePasswordAuthenticationToken(
                user, null, AuthorityUtils.createAuthorityList("ROLE_" + role));
    }
}
