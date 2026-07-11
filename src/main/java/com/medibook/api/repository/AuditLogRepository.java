package com.medibook.api.repository;

import com.medibook.api.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only repository for {@link AuditLog}.
 *
 * <p>Intentionally exposes only insert ({@code save}) and read/query operations.
 * No update or delete methods are provided: audit rows are immutable once written.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findBySubjectPatientIdOrderByTimestampDesc(UUID subjectPatientId);

    List<AuditLog> findBySubjectPatientIdAndTimestampBetweenOrderByTimestampDesc(
            UUID subjectPatientId, Instant from, Instant to);
}
