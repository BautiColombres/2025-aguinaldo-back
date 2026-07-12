package com.medibook.api.service;

import com.medibook.api.dto.FollowUpReminderDTO;
import com.medibook.api.entity.FollowUpReminder;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.FollowUpReminderRepository;
import com.medibook.api.repository.MedicalHistoryRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowUpReminderServiceTest {

    @Mock
    private FollowUpReminderRepository followUpReminderRepository;
    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;
    @Mock
    private TurnAssignedRepository turnAssignedRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private FollowUpReminderService service;

    private User doctor;
    private User patient;
    private TurnAssigned turn;
    private MedicalHistory history;
    private UUID doctorId;
    private UUID patientId;
    private UUID historyId;
    private OffsetDateTime scheduledAt;
    private Authentication doctorAuth;
    private Authentication patientAuth;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        historyId = UUID.randomUUID();
        scheduledAt = OffsetDateTime.parse("2025-01-15T10:00:00-03:00");

        doctor = new User();
        doctor.setId(doctorId);
        doctor.setName("Dr. Ana");
        doctor.setSurname("Gomez");
        doctor.setRole("DOCTOR");
        doctor.setStatus("ACTIVE");

        patient = new User();
        patient.setId(patientId);
        patient.setName("Pedro");
        patient.setSurname("Lopez");
        patient.setRole("PATIENT");
        patient.setStatus("ACTIVE");

        turn = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledAt)
                .status("COMPLETED")
                .build();

        history = MedicalHistory.builder()
                .id(historyId)
                .doctor(doctor)
                .patient(patient)
                .turn(turn)
                .content("note")
                .createdAt(LocalDateTime.now())
                .build();

        doctorAuth = new UsernamePasswordAuthenticationToken(doctor, null, List.of());
        patientAuth = new UsernamePasswordAuthenticationToken(patient, null, List.of());
    }

    // ---- createReminder ----

    @Test
    void createReminder_derivesScheduledForFromTurn_andAuditsCreateAllow() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));
        when(followUpReminderRepository.existsByMedicalHistory_IdAndDismissedFalse(historyId)).thenReturn(false);
        when(followUpReminderRepository.save(any(FollowUpReminder.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowUpReminderDTO dto = service.createReminder(doctorAuth, doctorId, historyId, 3);

        ArgumentCaptor<FollowUpReminder> captor = ArgumentCaptor.forClass(FollowUpReminder.class);
        verify(followUpReminderRepository).save(captor.capture());
        FollowUpReminder saved = captor.getValue();

        // scheduled_for = turn visit date + months (OQ-7), NOT history.createdAt.
        assertEquals(LocalDate.of(2025, 4, 15), saved.getScheduledFor());
        assertEquals(patientId, saved.getPatient().getId());
        assertEquals(doctorId, saved.getDoctor().getId());
        assertEquals(3, saved.getMonthsUntilControl());
        assertFalse(saved.isDismissed());

        assertEquals(LocalDate.of(2025, 4, 15), dto.getScheduledFor());
        verify(auditLogService).record(eq(AuditAction.CREATE), eq(AuditOutcome.ALLOW),
                eq(patientId), eq("FOLLOW_UP_REMINDER"), any());
    }

    @Test
    void createReminder_emitsExactlyOneNotificationToDoctorAndPatient() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));
        when(followUpReminderRepository.existsByMedicalHistory_IdAndDismissedFalse(historyId)).thenReturn(false);
        when(followUpReminderRepository.save(any(FollowUpReminder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReminder(doctorAuth, doctorId, historyId, 6);

        verify(notificationService, times(1))
                .createFollowUpScheduledDoctorNotification(eq(doctorId), any());
        verify(notificationService, times(1))
                .createFollowUpScheduledPatientNotification(eq(patientId), any());
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void createReminder_foreignDoctorPrincipal_deniedAndAudited() {
        UUID pathDoctorId = UUID.randomUUID(); // path id differs from authenticated principal

        assertThrows(AccessDeniedException.class,
                () -> service.createReminder(doctorAuth, pathDoctorId, historyId, 3));

        verify(auditLogService).record(eq(AuditAction.CREATE), eq(AuditOutcome.DENY),
                isNull(), eq("FOLLOW_UP_REMINDER"), any());
        verify(followUpReminderRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void createReminder_historyOwnedByAnotherDoctor_deniedAndAudited() {
        User otherDoctor = new User();
        otherDoctor.setId(UUID.randomUUID());
        otherDoctor.setRole("DOCTOR");
        history.setDoctor(otherDoctor);
        // principal == pathDoctorId (doctorId) but history belongs to otherDoctor
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));

        assertThrows(AccessDeniedException.class,
                () -> service.createReminder(doctorAuth, doctorId, historyId, 3));

        verify(auditLogService).record(eq(AuditAction.CREATE), eq(AuditOutcome.DENY),
                eq(patientId), eq("FOLLOW_UP_REMINDER"), eq(historyId.toString()));
        verify(followUpReminderRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void createReminder_activeDuplicate_suppressedWithConflict() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));
        when(followUpReminderRepository.existsByMedicalHistory_IdAndDismissedFalse(historyId)).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> service.createReminder(doctorAuth, doctorId, historyId, 3));

        verify(followUpReminderRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void createReminder_historyNotFound_notFound() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.createReminder(doctorAuth, doctorId, historyId, 3));
    }

    // ---- getDueReminders ----

    @Test
    void getDueReminders_excludesPatientsWithFutureActiveTurn_includesPastDue() {
        User patientB = new User();
        patientB.setId(UUID.randomUUID());
        patientB.setName("B");
        patientB.setSurname("B");

        FollowUpReminder rA = reminder(patient, LocalDate.now().minusMonths(2)); // past-due, no future turn
        FollowUpReminder rB = reminder(patientB, LocalDate.now());               // due, but future turn exists

        when(followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(eq(doctorId), any(LocalDate.class)))
                .thenReturn(List.of(rA, rB));
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), eq(patientId), any()))
                .thenReturn(false);
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), eq(patientB.getId()), any()))
                .thenReturn(true);

        List<FollowUpReminderDTO> due = service.getDueReminders(doctorAuth, doctorId);

        assertEquals(1, due.size());
        assertEquals(patientId, due.get(0).getPatientId());
    }

    @Test
    void getDueReminders_foreignDoctor_deniedAndAudited() {
        UUID pathDoctorId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> service.getDueReminders(doctorAuth, pathDoctorId));

        verify(auditLogService).record(eq(AuditAction.READ), eq(AuditOutcome.DENY),
                isNull(), eq("FOLLOW_UP_REMINDER"), any());
        verify(followUpReminderRepository, never())
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(any(), any());
    }

    // ---- dismissReminder ----

    @Test
    void dismissReminder_ownershipScoped_setsDismissedAndAudits() {
        UUID reminderId = UUID.randomUUID();
        FollowUpReminder r = reminder(patient, LocalDate.now());
        r.setId(reminderId);
        when(followUpReminderRepository.findByIdAndDoctor_Id(reminderId, doctorId))
                .thenReturn(Optional.of(r));
        when(followUpReminderRepository.save(any(FollowUpReminder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.dismissReminder(doctorAuth, doctorId, reminderId);

        assertTrue(r.isDismissed());
        verify(auditLogService).record(eq(AuditAction.UPDATE), eq(AuditOutcome.ALLOW),
                eq(patientId), eq("FOLLOW_UP_REMINDER"), eq(reminderId.toString()));
    }

    @Test
    void dismissReminder_foreignReminder_notFoundUnderScope() {
        UUID reminderId = UUID.randomUUID();
        when(followUpReminderRepository.findByIdAndDoctor_Id(reminderId, doctorId))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.dismissReminder(doctorAuth, doctorId, reminderId));

        verify(followUpReminderRepository, never()).save(any());
    }

    @Test
    void dismissReminder_crossDoctorReminder_notFoundEmitsIdOnlyDenyAudit() {
        // Coarse principal.id == doctorId passes, but the reminder belongs to
        // ANOTHER doctor so the ownership-scoped lookup is empty. A cross-doctor
        // dismiss must emit exactly one id-only DENY audit (no PHI) before 404 (OQ-8a).
        UUID reminderId = UUID.randomUUID();
        when(followUpReminderRepository.findByIdAndDoctor_Id(reminderId, doctorId))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.dismissReminder(doctorAuth, doctorId, reminderId));

        // Exactly one UPDATE/DENY row, subjectPatientId null (no PHI), resourceId = reminderId.
        verify(auditLogService, times(1)).record(eq(AuditAction.UPDATE), eq(AuditOutcome.DENY),
                isNull(), eq("FOLLOW_UP_REMINDER"), eq(reminderId.toString()));
        verify(auditLogService, never()).record(any(), eq(AuditOutcome.ALLOW), any(), any(), any());
        verify(followUpReminderRepository, never()).save(any());
    }

    @Test
    void dismissReminder_foreignDoctorPrincipal_deniedAndAudited() {
        UUID pathDoctorId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> service.dismissReminder(doctorAuth, pathDoctorId, UUID.randomUUID()));

        verify(auditLogService).record(eq(AuditAction.UPDATE), eq(AuditOutcome.DENY),
                isNull(), eq("FOLLOW_UP_REMINDER"), any());
        verify(followUpReminderRepository, never()).findByIdAndDoctor_Id(any(), any());
    }

    // ---- getRemindersForPatient ----

    @Test
    void getRemindersForPatient_ownReminders_returnedAndAuditAllow() {
        FollowUpReminder r = reminder(patient, LocalDate.now());
        when(followUpReminderRepository.findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(patientId))
                .thenReturn(List.of(r));

        List<FollowUpReminderDTO> result = service.getRemindersForPatient(patientAuth, patientId);

        assertEquals(1, result.size());
        assertEquals(patientId, result.get(0).getPatientId());
        verify(auditLogService).record(eq(AuditAction.READ), eq(AuditOutcome.ALLOW),
                eq(patientId), eq("FOLLOW_UP_REMINDER"), isNull());
    }

    @Test
    void getRemindersForPatient_principalMismatch_deniedAndAudited() {
        UUID otherPatientId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> service.getRemindersForPatient(patientAuth, otherPatientId));

        verify(auditLogService).record(eq(AuditAction.READ), eq(AuditOutcome.DENY),
                eq(otherPatientId), eq("FOLLOW_UP_REMINDER"), isNull());
        verify(followUpReminderRepository, never())
                .findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(any());
    }

    private FollowUpReminder reminder(User forPatient, LocalDate scheduledFor) {
        return FollowUpReminder.builder()
                .id(UUID.randomUUID())
                .medicalHistory(history)
                .doctor(doctor)
                .patient(forPatient)
                .monthsUntilControl(3)
                .scheduledFor(scheduledFor)
                .dismissed(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
