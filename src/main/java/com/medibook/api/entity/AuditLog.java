package com.medibook.api.entity;

import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record of a PHI access (medical history / patient files).
 *
 * <p><b>Immutability / append-only:</b> rows in {@code audit_log} are only ever
 * inserted, never updated or deleted by application code. There are intentionally
 * no update/delete methods on {@link com.medibook.api.repository.AuditLogRepository}.
 *
 * <p><b>No PHI content:</b> this entity stores identifiers, enums and request
 * metadata ONLY. It MUST NOT hold chart text, file contents, or any other
 * protected health information.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Authenticated user performing the access; null for anonymous. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /** Role of the actor (e.g. DOCTOR, PATIENT, ADMIN); null for anonymous. */
    @Column(name = "actor_role")
    private String actorRole;

    /** Patient whose PHI is the subject of the access. */
    @Column(name = "subject_patient_id")
    private UUID subjectPatientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    /** Resource category, e.g. MEDICAL_HISTORY or TURN_FILE. */
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    /** Identifier of the specific resource accessed; nullable (e.g. list reads). */
    @Column(name = "resource_id")
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private AuditOutcome outcome;

    /** UTC instant the access occurred. */
    @Column(name = "occurred_at", nullable = false)
    private Instant timestamp;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "request_id")
    private String requestId;
}
