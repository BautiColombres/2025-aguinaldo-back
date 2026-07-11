package com.medibook.api.service;

import com.medibook.api.entity.AuditLog;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit logging of PHI access (medical history + patient files).
 *
 * <p>The {@code record(...)} API is <b>best-effort</b>: it captures actor and
 * request metadata from the ambient security/request context and persists a
 * single immutable {@link AuditLog} row. A failure to write the audit entry is
 * logged and swallowed so it never breaks the primary PHI operation — but every
 * successful PHI access MUST attempt an audit write.
 *
 * <p><b>Transaction isolation:</b> the actual INSERT is delegated to
 * {@link AuditLogPersister#persist(AuditLog)}, which runs in its OWN transaction
 * via {@code @Transactional(REQUIRES_NEW)}. This is the mechanism that enforces
 * the best-effort contract: if the audit insert fails it rolls back only that
 * nested transaction and can never mark the caller's primary PHI transaction
 * rollback-only. (The try/catch here alone is insufficient — a failed insert
 * participating in the shared transaction would poison it and make the primary
 * commit throw {@code UnexpectedRollbackException}.) The persister is a separate
 * bean on purpose: REQUIRES_NEW is proxy-based, so a self-invocation inside this
 * class would bypass it. {@code record(...)} itself is intentionally NOT
 * transactional and swallows any exception (including one surfaced when the nested
 * audit transaction commits/rolls back).
 *
 * <p>Only identifiers, enums and request metadata are stored; NO PHI content.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogPersister auditLogPersister;

    /**
     * Records an audit entry, resolving the actor from the current
     * {@link SecurityContextHolder}. Best-effort: never throws. The insert runs in
     * its own transaction ({@code REQUIRES_NEW}) so an audit failure never rolls
     * back the caller's primary operation.
     */
    public void record(AuditAction action, AuditOutcome outcome,
                       UUID subjectPatientId, String resourceType, String resourceId) {
        record(currentActor(), action, outcome, subjectPatientId, resourceType, resourceId);
    }

    /**
     * Records an audit entry with an explicitly-provided actor (used where the
     * principal is passed rather than resolvable from the security context, e.g.
     * reactive/off-request-thread paths). Best-effort: never throws. The insert
     * runs in its own transaction ({@code REQUIRES_NEW}) so an audit failure never
     * rolls back the caller's primary operation.
     */
    public void record(User actor, AuditAction action, AuditOutcome outcome,
                       UUID subjectPatientId, String resourceType, String resourceId) {
        try {
            HttpServletRequest request = currentRequest();
            AuditLog entry = AuditLog.builder()
                    .actorUserId(actor != null ? actor.getId() : null)
                    .actorRole(actor != null ? actor.getRole() : null)
                    .subjectPatientId(subjectPatientId)
                    .action(action)
                    .outcome(outcome)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .timestamp(Instant.now())
                    .sourceIp(request != null ? request.getRemoteAddr() : null)
                    .requestId(requestId(request))
                    .build();
            auditLogPersister.persist(entry);
        } catch (Exception e) {
            // Best-effort: an audit-write failure must not break the primary operation.
            log.error("Failed to write audit log entry (action={}, outcome={}, resourceType={}): {}",
                    action, outcome, resourceType, e.getMessage());
        }
    }

    private User currentActor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                return user;
            }
        } catch (Exception e) {
            log.debug("Could not resolve audit actor from security context: {}", e.getMessage());
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                return servletAttrs.getRequest();
            }
        } catch (Exception e) {
            log.debug("Could not resolve current request for audit metadata: {}", e.getMessage());
        }
        return null;
    }

    private String requestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader("X-Correlation-Id");
        }
        return (requestId != null && !requestId.isBlank()) ? requestId : null;
    }
}
