package com.medibook.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for creating a follow-up reminder. Carries ONLY
 * {@code monthsUntilControl}, constrained to {3, 6, 12}.
 *
 * <p>Deliberately does NOT carry {@code patientId}, {@code doctorId},
 * {@code scheduledFor} or {@code historyId}: those are derived server-side from
 * the medical-history entry (path {@code historyId}) so a reminder can never be
 * pointed at an arbitrary patient (IDOR prevention — see FollowUpReminderService).
 */
@Data
public class CreateFollowUpReminderRequestDTO {

    @NotNull(message = "monthsUntilControl is required")
    private Integer monthsUntilControl;

    @AssertTrue(message = "monthsUntilControl must be one of 3, 6 or 12")
    public boolean isMonthsUntilControlValid() {
        return monthsUntilControl != null
                && (monthsUntilControl == 3 || monthsUntilControl == 6 || monthsUntilControl == 12);
    }
}
