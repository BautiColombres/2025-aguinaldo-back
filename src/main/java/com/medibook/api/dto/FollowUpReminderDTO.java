package com.medibook.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read model for a follow-up reminder. Carries only ids/dates — NO clinical
 * tag/motive (PHI) and NO {@code monthsOverdue}/overdue concept (OQ-4).
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
    private UUID historyId;
    private UUID turnId;
    private Integer monthsUntilControl;
    private LocalDate scheduledFor;
    private boolean dismissed;
    private LocalDateTime createdAt;
}
