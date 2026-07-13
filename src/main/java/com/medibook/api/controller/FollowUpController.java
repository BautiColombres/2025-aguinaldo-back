package com.medibook.api.controller;

import com.medibook.api.dto.CreateFollowUpReminderRequestDTO;
import com.medibook.api.dto.DueForFollowUpDTO;
import com.medibook.api.dto.FollowUpReminderDTO;
import com.medibook.api.service.FollowUpReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Follow-up reminder endpoints. DTO-only responses.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class FollowUpController {

    private final FollowUpReminderService followUpReminderService;

    @PostMapping("/api/doctors/{doctorId}/medical-history/{historyId}/followup")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<FollowUpReminderDTO> createReminder(
            @PathVariable UUID doctorId,
            @PathVariable UUID historyId,
            @Valid @RequestBody CreateFollowUpReminderRequestDTO request,
            Authentication authentication) {

        FollowUpReminderDTO reminder = followUpReminderService.createReminder(
                authentication, doctorId, historyId, request.getMonthsUntilControl());
        return ResponseEntity.status(HttpStatus.CREATED).body(reminder);
    }

    @GetMapping("/api/doctors/{doctorId}/followups")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<FollowUpReminderDTO>> getDueReminders(
            @PathVariable UUID doctorId,
            Authentication authentication) {

        return ResponseEntity.ok(followUpReminderService.getDueReminders(authentication, doctorId));
    }

    @GetMapping("/api/doctors/{doctorId}/patients/due-for-followup")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<DueForFollowUpDTO>> getPatientsDueForFollowUp(
            @PathVariable UUID doctorId,
            Authentication authentication) {

        return ResponseEntity.ok(
                followUpReminderService.getPatientsDueForFollowUp(authentication, doctorId));
    }

    @PutMapping("/api/doctors/{doctorId}/followups/{reminderId}/dismiss")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> dismissReminder(
            @PathVariable UUID doctorId,
            @PathVariable UUID reminderId,
            Authentication authentication) {

        followUpReminderService.dismissReminder(authentication, doctorId, reminderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/patients/{patientId}/followups")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<FollowUpReminderDTO>> getPatientReminders(
            @PathVariable UUID patientId,
            Authentication authentication) {

        return ResponseEntity.ok(followUpReminderService.getRemindersForPatient(authentication, patientId));
    }
}
