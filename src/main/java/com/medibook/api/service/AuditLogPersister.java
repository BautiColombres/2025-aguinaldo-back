package com.medibook.api.service;

import com.medibook.api.entity.AuditLog;
import com.medibook.api.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a single {@link AuditLog} row in its OWN transaction.
 *
 * <p>This is a deliberately separate Spring bean from {@link AuditLogService} so
 * that {@code @Transactional(REQUIRES_NEW)} is honored through a real proxy
 * boundary (a self-invocation inside {@code AuditLogService} would bypass the
 * proxy and run in the caller's transaction). Running the insert in a new
 * transaction is what guarantees the best-effort audit contract: a failed audit
 * insert rolls back ONLY this nested transaction and can never mark the caller's
 * primary PHI transaction rollback-only.
 *
 * <p>{@code saveAndFlush} forces the INSERT (and any constraint violation) to
 * surface here, within this REQUIRES_NEW boundary, so the failure is contained to
 * this transaction rather than escaping at the caller's commit.
 */
@Component
@RequiredArgsConstructor
class AuditLogPersister {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLog entry) {
        auditLogRepository.saveAndFlush(entry);
    }
}
