package com.medibook.api.service;

import com.medibook.api.dto.DueForFollowUpDTO;
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

        // scheduled_for = turn visit date + months, NOT history.createdAt.
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

    // ---- getPatientsDueForFollowUp ----

    @Test
    void getPatientsDueForFollowUp_delegatesToDueReminders_excludesFutureActiveTurnPatient() {
        // getPatientsDueForFollowUp composes on getDueReminders: the no-future-turn filter lives ONLY inside
        // getDueReminders, so a patient with a future active turn is excluded via
        // delegation (NOT a second filter here).
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
        when(turnAssignedRepository
                .findFirstByDoctor_IdAndPatient_IdAndStatusOrderByScheduledAtDesc(doctorId, patientId, "COMPLETED"))
                .thenReturn(Optional.of(turn));

        List<DueForFollowUpDTO> due = service.getPatientsDueForFollowUp(doctorAuth, doctorId);

        assertEquals(1, due.size());
        assertEquals(patientId, due.get(0).getPatientId());
        // Delegation audits exactly one READ/ALLOW (no double-audit from a second authz).
        verify(auditLogService, times(1)).record(eq(AuditAction.READ), eq(AuditOutcome.ALLOW),
                isNull(), eq("FOLLOW_UP_REMINDER"), isNull());
    }

    @Test
    void getPatientsDueForFollowUp_carriesScheduledForAndLastTurnDate_pastDueStillAppears() {
        LocalDate scheduledFor = LocalDate.now().minusMonths(1); // past-due, non-dismissed -> still shows
        FollowUpReminder r = reminder(patient, scheduledFor);

        when(followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(eq(doctorId), any(LocalDate.class)))
                .thenReturn(List.of(r));
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), eq(patientId), any()))
                .thenReturn(false);
        when(turnAssignedRepository
                .findFirstByDoctor_IdAndPatient_IdAndStatusOrderByScheduledAtDesc(doctorId, patientId, "COMPLETED"))
                .thenReturn(Optional.of(turn));

        List<DueForFollowUpDTO> due = service.getPatientsDueForFollowUp(doctorAuth, doctorId);

        assertEquals(1, due.size());
        DueForFollowUpDTO dto = due.get(0);
        assertEquals(patientId, dto.getPatientId());
        assertEquals("Pedro", dto.getPatientName());
        assertEquals("Lopez", dto.getPatientSurname());
        assertEquals(scheduledFor, dto.getScheduledFor());
        assertEquals(scheduledAt, dto.getLastTurnDate());
    }

    @Test
    void getPatientsDueForFollowUp_noCompletedTurn_lastTurnDateNull() {
        FollowUpReminder r = reminder(patient, LocalDate.now());

        when(followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(eq(doctorId), any(LocalDate.class)))
                .thenReturn(List.of(r));
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), eq(patientId), any()))
                .thenReturn(false);
        when(turnAssignedRepository
                .findFirstByDoctor_IdAndPatient_IdAndStatusOrderByScheduledAtDesc(doctorId, patientId, "COMPLETED"))
                .thenReturn(Optional.empty());

        List<DueForFollowUpDTO> due = service.getPatientsDueForFollowUp(doctorAuth, doctorId);

        assertEquals(1, due.size());
        assertNull(due.get(0).getLastTurnDate());
    }

    @Test
    void getPatientsDueForFollowUp_foreignDoctorPrincipal_deniedAndAudited() {
        UUID pathDoctorId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> service.getPatientsDueForFollowUp(doctorAuth, pathDoctorId));

        // Delegation surfaces getDueReminders' single READ/DENY (id-only, no PHI); no ALLOW.
        verify(auditLogService).record(eq(AuditAction.READ), eq(AuditOutcome.DENY),
                isNull(), eq("FOLLOW_UP_REMINDER"), any());
        verify(auditLogService, never()).record(any(), eq(AuditOutcome.ALLOW), any(), any(), any());
        verify(followUpReminderRepository, never())
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(any(), any());
    }

    @Test
    void getPatientsDueForFollowUp_dedupesByPatient_keepsEarliestScheduledFor() {
        // A patient with MORE THAN ONE active due reminder must appear at most ONCE
        // in the panel, keeping the reminder with the earliest (soonest) scheduledFor.
        LocalDate earlier = LocalDate.now().minusMonths(3);
        LocalDate later = LocalDate.now().minusMonths(1);
        FollowUpReminder rLater = reminder(patient, later);
        FollowUpReminder rEarlier = reminder(patient, earlier);

        // Deliberately return the later-dated reminder first to prove ordering by scheduledFor.
        when(followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(eq(doctorId), any(LocalDate.class)))
                .thenReturn(List.of(rLater, rEarlier));
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), eq(patientId), any()))
                .thenReturn(false);
        when(turnAssignedRepository
                .findFirstByDoctor_IdAndPatient_IdAndStatusOrderByScheduledAtDesc(doctorId, patientId, "COMPLETED"))
                .thenReturn(Optional.of(turn));

        List<DueForFollowUpDTO> due = service.getPatientsDueForFollowUp(doctorAuth, doctorId);

        assertEquals(1, due.size());
        assertEquals(patientId, due.get(0).getPatientId());
        assertEquals(earlier, due.get(0).getScheduledFor());
        assertEquals(scheduledAt, due.get(0).getLastTurnDate());
    }

    @Test
    void getPatientsDueForFollowUp_twoDifferentPatients_returnsTwoRowsOrderedByScheduledFor() {
        User patientB = new User();
        patientB.setId(UUID.randomUUID());
        patientB.setName("Beto");
        patientB.setSurname("Ruiz");

        LocalDate aDate = LocalDate.now().minusMonths(1);
        LocalDate bDate = LocalDate.now().minusMonths(3);
        FollowUpReminder rA = reminder(patient, aDate);
        FollowUpReminder rB = reminder(patientB, bDate);

        when(followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(eq(doctorId), any(LocalDate.class)))
                .thenReturn(List.of(rA, rB));
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), any(UUID.class), any()))
                .thenReturn(false);
        when(turnAssignedRepository
                .findFirstByDoctor_IdAndPatient_IdAndStatusOrderByScheduledAtDesc(eq(doctorId), any(UUID.class), eq("COMPLETED")))
                .thenReturn(Optional.empty());

        List<DueForFollowUpDTO> due = service.getPatientsDueForFollowUp(doctorAuth, doctorId);

        assertEquals(2, due.size());
        // Ordered by scheduledFor ascending (soonest first): patientB (bDate) then patient (aDate).
        assertEquals(patientB.getId(), due.get(0).getPatientId());
        assertEquals(bDate, due.get(0).getScheduledFor());
        assertEquals(patientId, due.get(1).getPatientId());
        assertEquals(aDate, due.get(1).getScheduledFor());
    }

    @Test
    void dueForFollowUpDTO_hasNoOverdueField() {
        // The panel DTO must carry NO overdue/monthsOverdue/severity concept.
        var fieldNames = java.util.Arrays.stream(DueForFollowUpDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(
                "patientId", "patientName", "patientSurname", "scheduledFor", "lastTurnDate"),
                fieldNames);
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
        // dismiss must emit exactly one id-only DENY audit (no PHI) before 404.
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

    // ---- UX-1: the reminder names the recommending doctor ----

    /**
     * The patient must be able to see WHO recommended the control. Name + specialty are
     * derived from the doctor's User/DoctorProfile — no new persisted column.
     */
    @Test
    void getRemindersForPatient_populatesDoctorNameAndSpecialty() {
        doctor.setDoctorProfile(doctorProfile("Cardiologia"));
        FollowUpReminder r = reminder(patient, LocalDate.now());
        when(followUpReminderRepository.findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(patientId))
                .thenReturn(List.of(r));

        List<FollowUpReminderDTO> result = service.getRemindersForPatient(patientAuth, patientId);

        assertEquals(1, result.size());
        FollowUpReminderDTO dto = result.get(0);
        assertEquals(doctorId, dto.getDoctorId());
        assertEquals(doctor.getName() + " " + doctor.getSurname(), dto.getDoctorName());
        assertEquals("Cardiologia", dto.getSpecialty());
    }

    /** A doctor with no DoctorProfile must not blow up the mapping — specialty is simply absent. */
    @Test
    void getRemindersForPatient_doctorWithoutProfile_nameStillPresent_specialtyNull() {
        doctor.setDoctorProfile(null);
        FollowUpReminder r = reminder(patient, LocalDate.now());
        when(followUpReminderRepository.findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(patientId))
                .thenReturn(List.of(r));

        List<FollowUpReminderDTO> result = service.getRemindersForPatient(patientAuth, patientId);

        FollowUpReminderDTO dto = result.get(0);
        assertEquals(doctor.getName() + " " + doctor.getSurname(), dto.getDoctorName());
        assertNull(dto.getSpecialty());
    }

    /** The doctor-facing due list is built by the same mapper, so it carries the fields too. */
    @Test
    void getDueReminders_populatesDoctorNameAndSpecialty() {
        doctor.setDoctorProfile(doctorProfile("Clinica Medica"));
        FollowUpReminder r = reminder(patient, LocalDate.now());
        when(followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(eq(doctorId), any(LocalDate.class)))
                .thenReturn(List.of(r));
        when(turnAssignedRepository.existsFutureActiveTurn(eq(doctorId), eq(patientId), any()))
                .thenReturn(false);

        List<FollowUpReminderDTO> due = service.getDueReminders(doctorAuth, doctorId);

        assertEquals(1, due.size());
        assertEquals(doctor.getName() + " " + doctor.getSurname(), due.get(0).getDoctorName());
        assertEquals("Clinica Medica", due.get(0).getSpecialty());
    }

    private com.medibook.api.entity.DoctorProfile doctorProfile(String specialty) {
        com.medibook.api.entity.DoctorProfile profile = new com.medibook.api.entity.DoctorProfile();
        profile.setSpecialty(specialty);
        profile.setMedicalLicense("MP-1234");
        profile.setSlotDurationMin(30);
        return profile;
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
