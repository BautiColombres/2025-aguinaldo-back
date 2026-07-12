package com.medibook.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "medical_history")
// Class-level batching of LAZY MedicalHistory proxies (up to 32 per select):
// collapses the N+1 when a list of rows each lazily loads its medicalHistory
// (e.g. the follow-up "due" panel — F2). Read-only optimization: results are
// unchanged, only the query count drops. Field-level @BatchSize is not allowed
// on @ManyToOne, so the batch hint lives on the target entity.
@org.hibernate.annotations.BatchSize(size = 32)
public class MedicalHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Size(max = 5000, message = "Medical history content must be less than 5000 characters")
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turn_id", nullable = false, unique = true)
    private TurnAssigned turn;

    /**
     * Normalized consultation tags (OQ-1 = B). Persisted in the
     * {@code medical_history_tags} table via changelog 0015 — the collection
     * table / column names below MUST match that DDL exactly (H2 tests use the
     * Hibernate-generated schema and cannot catch a mismatch).
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "medical_history_tags",
            joinColumns = @JoinColumn(name = "medical_history_id"))
    @Column(name = "tag", length = 50, nullable = false)
    @org.hibernate.annotations.BatchSize(size = 32)
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ARGENTINA_ZONE);
        updatedAt = LocalDateTime.now(ARGENTINA_ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ARGENTINA_ZONE);
    }
}