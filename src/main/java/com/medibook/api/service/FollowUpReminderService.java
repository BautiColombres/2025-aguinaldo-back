package com.medibook.api.service;

import com.medibook.api.dto.DueForFollowUpDTO;
import com.medibook.api.dto.FollowUpReminderDTO;
import com.medibook.api.entity.FollowUpReminder;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.FollowUpReminderRepository;
import com.medibook.api.repository.MedicalHistoryRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.util.LogMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;

/**
 * Follow-up reminders ("control en X meses").
 *
 * <p>Authorization is enforced INSIDE the service so a DENY row is
 * actually audited (id-only, no PHI): controllers keep only a coarse
 * {@code hasRole(...)} gate. All reminder fields are derived server-side from the
 * originating medical-history entry. On creation exactly one
 * one-shot {@code FOLLOWUP_SCHEDULED} notification is sent to the doctor AND one
 * to the patient — both generic / no-PHI. There is NO scheduled job, NO overdue
 * concept and reminders never auto-expire.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FollowUpReminderService {

    private final FollowUpReminderRepository followUpReminderRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final TurnAssignedRepository turnAssignedRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    private static final String RESOURCE_TYPE = "FOLLOW_UP_REMINDER";

    @Transactional
    public FollowUpReminderDTO createReminder(Authentication authentication, UUID doctorId,
                                              UUID historyId, int months) {
        UUID principalId = principalId(authentication);
        if (principalId == null || !principalId.equals(doctorId)) {
            auditLogService.record(AuditAction.CREATE, AuditOutcome.DENY, null, RESOURCE_TYPE,
                    historyId != null ? historyId.toString() : null);
            throw new AccessDeniedException("Not authorized to create follow-up reminders for another doctor");
        }

        MedicalHistory history = medicalHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Medical history entry not found"));

        if (!history.getDoctor().getId().equals(doctorId)) {
            auditLogService.record(AuditAction.CREATE, AuditOutcome.DENY,
                    history.getPatient().getId(), RESOURCE_TYPE, historyId.toString());
            throw new AccessDeniedException(
                    "Doctor can only create follow-up reminders for their own medical history entries");
        }

        if (followUpReminderRepository.existsByMedicalHistory_IdAndDismissedFalse(historyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An active follow-up reminder already exists for this medical history entry");
        }

        User patient = history.getPatient();
        User doctor = history.getDoctor();
        // Anchored to the clinical visit date, NOT history.createdAt.
        LocalDate scheduledFor = history.getTurn().getScheduledAt().toLocalDate().plusMonths(months);

        FollowUpReminder reminder = FollowUpReminder.builder()
                .medicalHistory(history)
                .patient(patient)
                .doctor(doctor)
                .monthsUntilControl(months)
                .scheduledFor(scheduledFor)
                .dismissed(false)
                .build();

        FollowUpReminder saved = followUpReminderRepository.save(reminder);
        log.info("Created follow-up reminder {} for history {} (patient {} by doctor {})",
                LogMaskingUtil.maskId(saved.getId()), LogMaskingUtil.maskId(historyId),
                LogMaskingUtil.maskId(patient.getId()), LogMaskingUtil.maskId(doctorId));

        auditLogService.record(AuditAction.CREATE, AuditOutcome.ALLOW,
                patient.getId(), RESOURCE_TYPE, saved.getId() != null ? saved.getId().toString() : null);

        // Exactly one one-shot notification to each recipient, generic / no-PHI.
        notificationService.createFollowUpScheduledDoctorNotification(doctor.getId(), saved.getId());
        notificationService.createFollowUpScheduledPatientNotification(patient.getId(), saved.getId());

        return mapToDTO(saved);
    }

    @Transactional
    public void dismissReminder(Authentication authentication, UUID doctorId, UUID reminderId) {
        requireDoctorPrincipal(authentication, doctorId, AuditAction.UPDATE);

        FollowUpReminder reminder = followUpReminderRepository
                .findByIdAndDoctor_Id(reminderId, doctorId)
                .orElseThrow(() -> {
                    // Coarse principal.id == doctorId passed, but the reminder belongs to
                    // ANOTHER doctor (empty under scope). Emit an id-only DENY audit (no PHI)
                    // BEFORE the 404 so cross-doctor dismiss is audited like create/read.
                    auditLogService.record(AuditAction.UPDATE, AuditOutcome.DENY,
                            null, RESOURCE_TYPE, reminderId.toString());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Follow-up reminder not found");
                });

        reminder.setDismissed(true);
        followUpReminderRepository.save(reminder);
        log.info("Dismissed follow-up reminder {} by doctor {}",
                LogMaskingUtil.maskId(reminderId), LogMaskingUtil.maskId(doctorId));

        auditLogService.record(AuditAction.UPDATE, AuditOutcome.ALLOW,
                reminder.getPatient().getId(), RESOURCE_TYPE, reminderId.toString());
    }

    /**
     * The doctor's live "due + no future turn" set: non-dismissed reminders due
     * today or earlier (past-due still shows — no expiry), minus patients
     * who already have a future active turn with this doctor.
     */
    public List<FollowUpReminderDTO> getDueReminders(Authentication authentication, UUID doctorId) {
        requireDoctorPrincipal(authentication, doctorId, AuditAction.READ);
        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW, null, RESOURCE_TYPE, null);

        LocalDate today = LocalDate.now(ARGENTINA_ZONE);
        OffsetDateTime now = OffsetDateTime.now(ARGENTINA_ZONE);

        return followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(doctorId, today)
                .stream()
                .filter(r -> !turnAssignedRepository.existsFutureActiveTurn(
                        doctorId, r.getPatient().getId(), now))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * The "pacientes que deben volver" panel. DELEGATES to {@link #getDueReminders}
     * for the base "due + no future turn" set (that filter — and the service-side
     * authz + single READ audit — live in exactly ONE place). The panel lists
     * PATIENTS (not reminders), so a patient with more than one active due reminder
     * appears at most ONCE: rows are deduped by {@code patientId}, keeping the
     * reminder with the earliest (soonest) {@code scheduledFor} — the nearest
     * recommended return date. The final list is ordered by {@code scheduledFor}
     * ascending. Each row is enriched with {@code lastTurnDate} (the patient's
     * most-recent COMPLETED turn with this doctor). NO overdue computation;
     * {@code lastTurnDate} may be {@code null}.
     */
    public List<DueForFollowUpDTO> getPatientsDueForFollowUp(Authentication authentication, UUID doctorId) {
        return getDueReminders(authentication, doctorId).stream()
                .collect(Collectors.toMap(
                        FollowUpReminderDTO::getPatientId,
                        r -> r,
                        (a, b) -> a.getScheduledFor().isAfter(b.getScheduledFor()) ? b : a,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                .map(r -> DueForFollowUpDTO.builder()
                        .patientId(r.getPatientId())
                        .patientName(r.getPatientName())
                        .patientSurname(r.getPatientSurname())
                        .scheduledFor(r.getScheduledFor())
                        .lastTurnDate(lastCompletedTurnDate(doctorId, r.getPatientId()))
                        .build())
                .sorted(java.util.Comparator.comparing(DueForFollowUpDTO::getScheduledFor))
                .collect(Collectors.toList());
    }

    private OffsetDateTime lastCompletedTurnDate(UUID doctorId, UUID patientId) {
        return turnAssignedRepository
                .findFirstByDoctor_IdAndPatient_IdAndStatusOrderByScheduledAtDesc(doctorId, patientId, "COMPLETED")
                .map(com.medibook.api.entity.TurnAssigned::getScheduledAt)
                .orElse(null);
    }

    /**
     * Patient-owned read: the patient's own non-dismissed
     * reminders. Service-side authz: the principal must be the patient.
     */
    public List<FollowUpReminderDTO> getRemindersForPatient(Authentication authentication, UUID patientId) {
        UUID principalId = principalId(authentication);
        if (principalId == null || !principalId.equals(patientId)) {
            auditLogService.record(AuditAction.READ, AuditOutcome.DENY, patientId, RESOURCE_TYPE, null);
            throw new AccessDeniedException("Not authorized to read these follow-up reminders");
        }
        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW, patientId, RESOURCE_TYPE, null);

        return followUpReminderRepository
                .findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(patientId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private void requireDoctorPrincipal(Authentication authentication, UUID doctorId, AuditAction action) {
        UUID principalId = principalId(authentication);
        if (principalId == null || !principalId.equals(doctorId)) {
            auditLogService.record(action, AuditOutcome.DENY, null, RESOURCE_TYPE,
                    doctorId != null ? doctorId.toString() : null);
            throw new AccessDeniedException("Not authorized for this doctor's follow-up reminders");
        }
    }

    private UUID principalId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User principal) {
            return principal.getId();
        }
        return null;
    }

    private FollowUpReminderDTO mapToDTO(FollowUpReminder reminder) {
        MedicalHistory history = reminder.getMedicalHistory();
        User doctor = reminder.getDoctor();
        return FollowUpReminderDTO.builder()
                .id(reminder.getId())
                .patientId(reminder.getPatient().getId())
                .patientName(reminder.getPatient().getName())
                .patientSurname(reminder.getPatient().getSurname())
                .doctorId(doctor.getId())
                // UX-1: derived at read time from the doctor's User/DoctorProfile — no new column.
                .doctorName(displayName(doctor))
                .specialty(doctor.getDoctorProfile() != null
                        ? doctor.getDoctorProfile().getSpecialty()
                        : null)
                .historyId(history != null ? history.getId() : null)
                .turnId(history != null && history.getTurn() != null ? history.getTurn().getId() : null)
                .monthsUntilControl(reminder.getMonthsUntilControl())
                .scheduledFor(reminder.getScheduledFor())
                .dismissed(reminder.isDismissed())
                .createdAt(reminder.getCreatedAt())
                .build();
    }

    /** "Name Surname", tolerating a missing half. Never exposes email/DNI. */
    private String displayName(User user) {
        String name = user.getName() != null ? user.getName().trim() : "";
        String surname = user.getSurname() != null ? user.getSurname().trim() : "";
        String full = (name + " " + surname).trim();
        return full.isEmpty() ? null : full;
    }
}
