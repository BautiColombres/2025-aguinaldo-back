package com.medibook.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model for the doctor's "pacientes que deben volver" panel.
 *
 * <p>Carries only ids/names/dates — NO clinical PHI and, by design, NO
 * {@code monthsOverdue} / overdue / severity field. {@code scheduledFor} is the
 * reminder's recommended control date; {@code lastTurnDate} is the patient's
 * most-recent COMPLETED turn with this doctor (may be {@code null}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DueForFollowUpDTO {
    private UUID patientId;
    private String patientName;
    private String patientSurname;
    private LocalDate scheduledFor;
    private OffsetDateTime lastTurnDate;
}
