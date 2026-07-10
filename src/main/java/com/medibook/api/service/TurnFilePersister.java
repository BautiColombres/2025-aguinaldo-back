package com.medibook.api.service;

import com.medibook.api.entity.TurnFile;
import com.medibook.api.repository.TurnFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a single {@link TurnFile} row inside a real {@code @Transactional}
 * boundary.
 *
 * <p>This is a deliberately separate Spring bean from {@link TurnFileServiceImpl}
 * (mirroring {@link AuditLogPersister}) so the transaction is honored through a
 * real proxy boundary. A self-invocation inside {@code TurnFileServiceImpl} would
 * bypass the proxy and run with NO transaction at all — which is exactly the
 * BBUG-H4 defect this fix removes.
 *
 * <p>The blocking JPA write is invoked from a bounded scheduler
 * ({@code Schedulers.boundedElastic()}) by the caller, never from the reactive
 * event-loop thread that carried the storage upload. {@code saveAndFlush} forces
 * the INSERT (and any constraint violation) to surface here, inside this
 * transaction, so the reactive chain can react to the failure and run the
 * compensating storage delete.
 */
@Component
@RequiredArgsConstructor
class TurnFilePersister {

    private final TurnFileRepository turnFileRepository;

    @Transactional
    public void persist(TurnFile turnFile) {
        turnFileRepository.saveAndFlush(turnFile);
    }
}
