package com.medibook.api.repository;

import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TurnAssignedRepository extends JpaRepository<TurnAssigned, UUID> {
    List<TurnAssigned> findByDoctor_IdAndScheduledAtBetween(UUID doctorId, OffsetDateTime start, OffsetDateTime end);
    boolean existsByDoctor_IdAndScheduledAt(UUID doctorId, OffsetDateTime scheduledAt);
    
    @Query("SELECT COUNT(t) > 0 FROM TurnAssigned t WHERE t.doctor.id = :doctorId AND t.scheduledAt = :scheduledAt AND t.status NOT IN ('CANCELED', 'NO_SHOW')")
    boolean existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(@Param("doctorId") UUID doctorId, @Param("scheduledAt") OffsetDateTime scheduledAt);

    @Query("SELECT COUNT(t) > 0 FROM TurnAssigned t WHERE t.doctor.id = :doctorId AND t.scheduledAt = :scheduledAt AND t.id <> :excludeTurnId AND t.status NOT IN ('CANCELED', 'NO_SHOW')")
    boolean existsConflictingTurnExcludingId(@Param("doctorId") UUID doctorId, @Param("scheduledAt") OffsetDateTime scheduledAt, @Param("excludeTurnId") UUID excludeTurnId);
    
    List<TurnAssigned> findByDoctor_IdOrderByScheduledAtDesc(UUID doctorId);
    
    List<TurnAssigned> findByPatient_IdOrderByScheduledAtDesc(UUID patientId);
    
    List<TurnAssigned> findByDoctor_IdAndStatusOrderByScheduledAtDesc(UUID doctorId, String status);
    
    List<TurnAssigned> findByPatient_IdAndStatusOrderByScheduledAtDesc(UUID patientId, String status);
    
    @Query("SELECT DISTINCT t.patient FROM TurnAssigned t WHERE t.doctor.id = :doctorId AND t.patient IS NOT NULL ORDER BY t.patient.name, t.patient.surname")
    List<User> findDistinctPatientsByDoctorId(@Param("doctorId") UUID doctorId);
    
    boolean existsByDoctor_IdAndPatient_Id(UUID doctorId, UUID patientId);

    @Query("SELECT COUNT(t) > 0 FROM TurnAssigned t WHERE t.doctor.id = :doctorId AND t.patient.id = :patientId AND t.status NOT IN ('CANCELED', 'NO_SHOW')")
    boolean existsActiveRelationship(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId);

    /**
     * True when the patient has a FUTURE (after {@code now}) active — non-cancelled /
     * non-no-show — turn with this doctor. Used by the follow-up "due" panel to
     * live-filter out patients who already have an upcoming turn (F2 / OQ-4).
     * Note the deliberate type split: turn {@code scheduledAt} is an
     * {@code OffsetDateTime}; the reminder's {@code scheduled_for} is a
     * {@code LocalDate}.
     */
    @Query("SELECT COUNT(t) > 0 FROM TurnAssigned t WHERE t.doctor.id = :doctorId AND t.patient.id = :patientId AND t.scheduledAt > :now AND t.status NOT IN ('CANCELED', 'NO_SHOW')")
    boolean existsFutureActiveTurn(@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId, @Param("now") OffsetDateTime now);
    
    List<TurnAssigned> findByPatient_IdAndStatusAndScheduledAtAfter(UUID patientId, String status, OffsetDateTime scheduledAt);
    
    List<TurnAssigned> findByPatient_IdAndStatus(UUID patientId, String status);
    
    @Query("SELECT COUNT(t) > 0 FROM TurnAssigned t WHERE t.patient.email = :email AND t.status = 'COMPLETED' AND t.motive = 'HEALTH CERTIFICATE' AND t.scheduledAt > :oneYearAgo")
    boolean existsHealthCertificateWithinLastYear(@Param("email") String email, @Param("oneYearAgo") OffsetDateTime oneYearAgo);
    
    @Query("SELECT COALESCE(MAX(sub.count), 0) FROM (SELECT COUNT(t) AS count FROM TurnAssigned t WHERE t.patient.id = :patientId AND t.status = 'COMPLETED' GROUP BY t.doctor.id) sub")
    Long findMaxCompletedTurnsWithSameDoctor(@Param("patientId") UUID patientId);
}
