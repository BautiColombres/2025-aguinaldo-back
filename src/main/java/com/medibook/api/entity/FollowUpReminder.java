package com.medibook.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;

/**
 * A doctor-created "control en X meses" follow-up reminder.
 *
 * <p>All reminder fields are derived server-side from the originating
 * {@link MedicalHistory} entry — never from the request body — so the reminder
 * can never be pointed at an arbitrary patient. The reminder
 * is live-computed into the doctor's "due" panel: there is NO scheduled job and
 * NO overdue concept; a reminder never auto-expires and simply persists until
 * the patient books a future turn (filtered out live) or the doctor dismisses it.
 *
 * <p>INVARIANT: {@code @Table} / {@code @Column} names below MUST match the
 * changelog {@code 0016-follow-up-reminders.xml} exactly — H2 tests use the
 * Hibernate-generated schema and cannot catch a mismatch.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "follow_up_reminders")
public class FollowUpReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Field-level @BatchSize is NOT allowed on @ManyToOne in this Hibernate version;
    // batching of these to-one proxies is done at the target-entity class level
    // (@BatchSize on User / MedicalHistory), which collapses the per-row proxy inits
    // into IN-clause loads.
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_history_id", nullable = false)
    private MedicalHistory medicalHistory;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @NotNull
    @Column(name = "months_until_control", nullable = false)
    private Integer monthsUntilControl;

    @NotNull
    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Column(name = "dismissed", nullable = false)
    private boolean dismissed;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ARGENTINA_ZONE);
    }
}
