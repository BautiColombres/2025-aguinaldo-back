package com.medibook.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read model for a follow-up reminder. Carries only ids/dates/names — NO clinical
 * tag/motive (PHI) and NO {@code monthsOverdue}/overdue concept.
 *
 * <p>UX-1: {@code doctorName} / {@code specialty} identify the RECOMMENDING doctor so the
 * patient's reminder can say who asked them to come back. Both are DERIVED at read time from the
 * doctor's {@code User} / {@code DoctorProfile} — there is no new persisted column and therefore
 * no Liquibase changelog. Exposing a treating doctor's professional name and specialty to their
 * OWN patient is not PHI; the no-PHI contract above is unchanged and still binding — do NOT add
 * clinical motive/tags or any other patient's data here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpReminderDTO {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private String patientSurname;
    private UUID doctorId;
    /** Display name of the recommending doctor ("Name Surname"). Never their email/DNI. */
    private String doctorName;
    /** Recommending doctor's specialty; {@code null} when the doctor has no profile. */
    private String specialty;
    private UUID historyId;
    private UUID turnId;
    private Integer monthsUntilControl;
    private LocalDate scheduledFor;
    private boolean dismissed;
    private LocalDateTime createdAt;
}
