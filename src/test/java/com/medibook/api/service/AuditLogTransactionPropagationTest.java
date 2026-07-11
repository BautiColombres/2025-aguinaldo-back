package com.medibook.api.service;

import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end proof that an audit-write failure is isolated from the caller's
 * primary PHI transaction.
 *
 * <p>The audit insert runs in its OWN transaction via
 * {@code @Transactional(REQUIRES_NEW)}, so when the insert fails (here: a real
 * DB-level NOT NULL violation on {@code action}) only the nested audit
 * transaction rolls back — it must NOT mark the caller's transaction
 * rollback-only. Without REQUIRES_NEW the failing insert poisons the shared
 * transaction and the primary commit throws
 * {@code UnexpectedRollbackException}, defeating the best-effort try/catch.
 *
 * <p>This is a real Spring-context integration test (not a mock): the audit
 * repository actually hits H2 so the transaction propagation is exercised for
 * real, which a plain Mockito unit test cannot do. A {@link PrimaryOpFixture}
 * bean performs a real committed write (a {@link User}) around the failing audit
 * call, invoking {@link AuditLogService} through its Spring proxy so REQUIRES_NEW
 * applies.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditLogTransactionPropagationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrimaryOpFixture primaryOpFixture;

    @Test
    void auditWriteFailure_doesNotRollBackPrimaryTransaction() {
        UUID patientId = UUID.randomUUID();

        // The audit write fails at the DB (null action). With REQUIRES_NEW that
        // rolls back only the nested audit tx; the primary op must still commit.
        UUID savedUserId = assertDoesNotThrow(
                () -> primaryOpFixture.doPrimaryOpThenFailingAudit(patientId),
                "primary op (incl. its commit) must succeed even though the audit insert fails");

        Optional<User> committed = userRepository.findById(savedUserId);
        assertTrue(committed.isPresent(),
                "primary write must be committed; a REQUIRES_NEW audit failure must not roll it back");
    }

    /**
     * Stands in for a real PHI service method: a {@code @Transactional} primary
     * operation that writes domain data and then triggers a best-effort audit
     * write on the injected {@link AuditLogService} bean (through the Spring
     * proxy, so REQUIRES_NEW applies). The audit call is made to fail at the DB
     * level by passing a null {@code action} (NOT NULL column).
     */
    public static class PrimaryOpFixture {

        private final UserRepository userRepository;
        private final AuditLogService auditLogService;

        PrimaryOpFixture(UserRepository userRepository, AuditLogService auditLogService) {
            this.userRepository = userRepository;
            this.auditLogService = auditLogService;
        }

        @Transactional
        public UUID doPrimaryOpThenFailingAudit(UUID patientId) {
            User user = new User();
            user.setEmail("audit-iso-" + UUID.randomUUID() + "@test.medibook");
            user.setDni(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L));
            user.setPasswordHash("hash");
            user.setName("Audit");
            user.setSurname("Isolation");
            user.setRole("PATIENT");
            user.setStatus("ACTIVE");
            User saved = userRepository.save(user);

            // null action -> NOT NULL violation when the audit row is flushed.
            auditLogService.record((AuditAction) null, AuditOutcome.ALLOW,
                    patientId, "MEDICAL_HISTORY", saved.getId().toString());

            return saved.getId();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        PrimaryOpFixture primaryOpFixture(UserRepository userRepository, AuditLogService auditLogService) {
            return new PrimaryOpFixture(userRepository, auditLogService);
        }
    }
}
