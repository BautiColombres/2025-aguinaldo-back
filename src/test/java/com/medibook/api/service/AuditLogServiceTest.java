package com.medibook.api.service;

import com.medibook.api.entity.AuditLog;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogPersister auditLogPersister;

    @InjectMocks
    private AuditLogService auditLogService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User principal(String role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setRole(role);
        u.setStatus("ACTIVE");
        return u;
    }

    @Test
    void record_persistsAllowEntry_withActorSubjectActionOutcome_andNoPhiContent() {
        User doctor = principal("DOCTOR");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctor, null, java.util.List.of()));

        UUID patientId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW,
                patientId, "MEDICAL_HISTORY", resourceId.toString());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister).persist(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals(doctor.getId(), saved.getActorUserId());
        assertEquals("DOCTOR", saved.getActorRole());
        assertEquals(patientId, saved.getSubjectPatientId());
        assertEquals(AuditAction.READ, saved.getAction());
        assertEquals(AuditOutcome.ALLOW, saved.getOutcome());
        assertEquals("MEDICAL_HISTORY", saved.getResourceType());
        assertEquals(resourceId.toString(), saved.getResourceId());
        assertNotNull(saved.getTimestamp());
    }

    @Test
    void record_persistsDenyEntry() {
        User doctor = principal("DOCTOR");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctor, null, java.util.List.of()));

        UUID patientId = UUID.randomUUID();

        auditLogService.record(AuditAction.READ, AuditOutcome.DENY,
                patientId, "MEDICAL_HISTORY", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister).persist(captor.capture());
        assertEquals(AuditOutcome.DENY, captor.getValue().getOutcome());
        assertEquals(doctor.getId(), captor.getValue().getActorUserId());
    }

    @Test
    void record_anonymous_actorNull() {
        UUID patientId = UUID.randomUUID();

        auditLogService.record(AuditAction.READ, AuditOutcome.DENY,
                patientId, "MEDICAL_HISTORY", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister).persist(captor.capture());
        assertNull(captor.getValue().getActorUserId());
        assertNull(captor.getValue().getActorRole());
    }

    @Test
    void record_writeActions_captureCreateUpdateDelete() {
        UUID patientId = UUID.randomUUID();

        auditLogService.record(AuditAction.CREATE, AuditOutcome.ALLOW, patientId, "MEDICAL_HISTORY", "r1");
        auditLogService.record(AuditAction.UPDATE, AuditOutcome.ALLOW, patientId, "MEDICAL_HISTORY", "r1");
        auditLogService.record(AuditAction.DELETE, AuditOutcome.ALLOW, patientId, "MEDICAL_HISTORY", "r1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister, times(3)).persist(captor.capture());
        assertEquals(AuditAction.CREATE, captor.getAllValues().get(0).getAction());
        assertEquals(AuditAction.UPDATE, captor.getAllValues().get(1).getAction());
        assertEquals(AuditAction.DELETE, captor.getAllValues().get(2).getAction());
    }

    /**
     * {@code record(...)} is best-effort: any failure from the (REQUIRES_NEW)
     * persister — whether raised when the insert is issued or later when the nested
     * audit transaction commits/rolls back — must be swallowed so the caller's
     * primary operation is never affected.
     */
    @Test
    void record_persisterFailure_isSwallowed_bestEffort() {
        User doctor = principal("DOCTOR");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(doctor, null, java.util.List.of()));
        doThrow(new RuntimeException("db down"))
                .when(auditLogPersister).persist(any(AuditLog.class));

        UUID patientId = UUID.randomUUID();

        assertDoesNotThrow(() -> auditLogService.record(
                AuditAction.READ, AuditOutcome.ALLOW, patientId, "MEDICAL_HISTORY", "r1"));
    }

    /**
     * The real isolation guarantee ("an audit-write failure must never roll back
     * the caller's primary PHI transaction") is provided by running the audit
     * insert in its OWN transaction via {@code @Transactional(REQUIRES_NEW)} on
     * {@link AuditLogPersister#persist}. That propagation is enforced by the Spring
     * proxy at runtime and cannot be exercised with plain Mockito mocks; here we
     * assert the annotation contract on the method that actually opens the new
     * transaction. The end-to-end isolation is exercised in
     * {@link AuditLogTransactionPropagationTest}.
     */
    @Test
    void persister_persist_declaresRequiresNewPropagation() throws NoSuchMethodException {
        Method persist = AuditLogPersister.class.getDeclaredMethod("persist", AuditLog.class);

        Transactional tx = persist.getAnnotation(Transactional.class);
        assertNotNull(tx, "AuditLogPersister.persist must be @Transactional to isolate the audit write");
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
                "AuditLogPersister.persist must use REQUIRES_NEW so an audit failure never rolls back the primary op");
    }

    @Test
    void record_withExplicitActor_usesProvidedActor() {
        User doctor = principal("DOCTOR");
        UUID patientId = UUID.randomUUID();

        auditLogService.record(doctor, AuditAction.CREATE, AuditOutcome.ALLOW,
                patientId, "TURN_FILE", "file1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister).persist(captor.capture());
        assertEquals(doctor.getId(), captor.getValue().getActorUserId());
        assertEquals("DOCTOR", captor.getValue().getActorRole());
        assertEquals("TURN_FILE", captor.getValue().getResourceType());
    }
}
