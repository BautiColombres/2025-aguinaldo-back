package com.medibook.api.repository;

import com.medibook.api.entity.FollowUpReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpReminderRepository extends JpaRepository<FollowUpReminder, UUID> {

    /**
     * "Due" reminders for a doctor: non-dismissed and whose recommended control
     * date has arrived ({@code scheduled_for <= today}). Includes past-due
     * reminders — they never expire on the date passing.
     */
    List<FollowUpReminder> findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(
            UUID doctorId, LocalDate today);

    /**
     * Active-duplicate guard: an active (non-dismissed) reminder already exists
     * for this medical-history entry.
     */
    boolean existsByMedicalHistory_IdAndDismissedFalse(UUID medicalHistoryId);

    /**
     * Ownership-scoped lookup used by dismiss — a reminder belonging to another
     * doctor is simply not found under scope.
     */
    Optional<FollowUpReminder> findByIdAndDoctor_Id(UUID id, UUID doctorId);

    /**
     * Patient-owned read: the patient's own non-dismissed
     * reminders, soonest control date first.
     */
    List<FollowUpReminder> findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(UUID patientId);
}
