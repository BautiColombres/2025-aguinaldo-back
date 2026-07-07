package com.medibook.api.repository;

import com.medibook.api.entity.AuditLog;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link AuditLog} JPA entity mapping and repository queries against
 * H2. Liquibase is disabled under the {@code test} profile
 * ({@code spring.liquibase.enabled=false}, {@code ddl-auto=create-drop}), so the
 * schema here is derived from the entity mapping — NOT from the
 * {@code 0012-audit-log.xml} changelog. Confirms the append-only row stores
 * IDs/enums/metadata ONLY (no PHI content) and that the finder queries work.
 */
@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void savesAndQueriesBySubjectPatient_withIdsAndEnumsOnly() {
        UUID actorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        AuditLog entry = AuditLog.builder()
                .actorUserId(actorId)
                .actorRole("DOCTOR")
                .subjectPatientId(patientId)
                .action(AuditAction.READ)
                .outcome(AuditOutcome.ALLOW)
                .resourceType("MEDICAL_HISTORY")
                .resourceId(resourceId.toString())
                .timestamp(Instant.now())
                .sourceIp("127.0.0.1")
                .requestId("req-123")
                .build();

        auditLogRepository.save(entry);

        List<AuditLog> found = auditLogRepository.findBySubjectPatientIdOrderByTimestampDesc(patientId);
        assertEquals(1, found.size());
        AuditLog saved = found.get(0);
        assertNotNull(saved.getId());
        assertEquals(actorId, saved.getActorUserId());
        assertEquals("DOCTOR", saved.getActorRole());
        assertEquals(patientId, saved.getSubjectPatientId());
        assertEquals(AuditAction.READ, saved.getAction());
        assertEquals(AuditOutcome.ALLOW, saved.getOutcome());
        assertEquals("MEDICAL_HISTORY", saved.getResourceType());
        assertEquals(resourceId.toString(), saved.getResourceId());
        assertEquals("127.0.0.1", saved.getSourceIp());
        assertEquals("req-123", saved.getRequestId());
    }

    @Test
    void denyEntry_persistsWithAnonymousActorNull() {
        UUID patientId = UUID.randomUUID();

        AuditLog entry = AuditLog.builder()
                .actorUserId(null)
                .actorRole(null)
                .subjectPatientId(patientId)
                .action(AuditAction.READ)
                .outcome(AuditOutcome.DENY)
                .resourceType("MEDICAL_HISTORY")
                .resourceId(null)
                .timestamp(Instant.now())
                .build();

        auditLogRepository.save(entry);

        List<AuditLog> found = auditLogRepository.findBySubjectPatientIdOrderByTimestampDesc(patientId);
        assertEquals(1, found.size());
        assertNull(found.get(0).getActorUserId());
        assertNull(found.get(0).getActorRole());
        assertEquals(AuditOutcome.DENY, found.get(0).getOutcome());
    }

    @Test
    void queriesBySubjectPatientAndTimeWindow() {
        UUID patientId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditLog entry = AuditLog.builder()
                .subjectPatientId(patientId)
                .action(AuditAction.CREATE)
                .outcome(AuditOutcome.ALLOW)
                .resourceType("TURN_FILE")
                .resourceId("turn-1")
                .timestamp(now)
                .build();
        auditLogRepository.save(entry);

        List<AuditLog> inWindow = auditLogRepository
                .findBySubjectPatientIdAndTimestampBetweenOrderByTimestampDesc(
                        patientId, now.minusSeconds(60), now.plusSeconds(60));
        assertEquals(1, inWindow.size());

        List<AuditLog> outOfWindow = auditLogRepository
                .findBySubjectPatientIdAndTimestampBetweenOrderByTimestampDesc(
                        patientId, now.plusSeconds(120), now.plusSeconds(240));
        assertTrue(outOfWindow.isEmpty());
    }
}
