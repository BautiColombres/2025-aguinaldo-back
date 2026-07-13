package com.medibook.api.repository;

import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the {@code medical_history_tags} @ElementCollection mapping and the
 * per-patient / per-doctor tag-frequency query.
 *
 * <p>NOTE: H2 uses the Hibernate-generated schema (Liquibase disabled in tests),
 * so this validates the JPA mapping/round-trip, NOT the 0015 Liquibase DDL — the
 * Postgres e2e remains the real gate for the changelog.
 */
@DataJpaTest
@ActiveProfiles("test")
class MedicalHistoryTagsRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User doctorA;
    private User doctorB;
    private User patient;

    @BeforeEach
    void setUp() {
        doctorA = entityManager.persistAndFlush(createUser("doctor-a@test.com", 30000001L, "DOCTOR"));
        doctorB = entityManager.persistAndFlush(createUser("doctor-b@test.com", 30000002L, "DOCTOR"));
        patient = entityManager.persistAndFlush(createUser("patient@test.com", 30000003L, "PATIENT"));
    }

    @Test
    void tagsRoundTrip_saveReloadPreservesSet() {
        MedicalHistory history = persistHistory(doctorA, patient, Set.of("diabetes", "control-anual"));
        UUID id = history.getId();
        entityManager.clear();

        MedicalHistory reloaded = medicalHistoryRepository.findById(id).orElseThrow();
        assertEquals(2, reloaded.getTags().size());
        assertTrue(reloaded.getTags().contains("diabetes"));
        assertTrue(reloaded.getTags().contains("control-anual"));
    }

    @Test
    void tags_emptySet_roundTrips() {
        MedicalHistory history = persistHistory(doctorA, patient, Set.of());
        UUID id = history.getId();
        entityManager.clear();

        MedicalHistory reloaded = medicalHistoryRepository.findById(id).orElseThrow();
        assertNotNull(reloaded.getTags());
        assertTrue(reloaded.getTags().isEmpty());
    }

    @Test
    void tagFrequency_orderedByCountDescending() {
        persistHistory(doctorA, patient, Set.of("diabetes", "gripe"));
        persistHistory(doctorA, patient, Set.of("diabetes"));
        entityManager.clear();

        List<Object[]> rows = medicalHistoryRepository
                .findTagFrequencyByPatientAndDoctor(patient.getId(), doctorA.getId());

        assertEquals(2, rows.size());
        assertEquals("diabetes", rows.get(0)[0]);
        assertEquals(2L, ((Number) rows.get(0)[1]).longValue());
        assertEquals("gripe", rows.get(1)[0]);
        assertEquals(1L, ((Number) rows.get(1)[1]).longValue());
    }

    @Test
    void tagFrequency_scopedPerDoctor_noCrossDoctorLeak() {
        persistHistory(doctorA, patient, Set.of("diabetes"));
        persistHistory(doctorA, patient, Set.of("diabetes"));
        persistHistory(doctorB, patient, Set.of("diabetes"));
        entityManager.clear();

        List<Object[]> doctorARows = medicalHistoryRepository
                .findTagFrequencyByPatientAndDoctor(patient.getId(), doctorA.getId());
        List<Object[]> doctorBRows = medicalHistoryRepository
                .findTagFrequencyByPatientAndDoctor(patient.getId(), doctorB.getId());

        // Doctor A sees only their own 2 entries, never doctor B's.
        assertEquals(1, doctorARows.size());
        assertEquals(2L, ((Number) doctorARows.get(0)[1]).longValue());
        // Doctor B sees only their own single entry.
        assertEquals(1, doctorBRows.size());
        assertEquals(1L, ((Number) doctorBRows.get(0)[1]).longValue());
    }

    @Test
    void tagFrequency_noTags_returnsEmpty() {
        persistHistory(doctorA, patient, Set.of());
        entityManager.clear();

        List<Object[]> rows = medicalHistoryRepository
                .findTagFrequencyByPatientAndDoctor(patient.getId(), doctorA.getId());
        assertTrue(rows.isEmpty());
    }

    private MedicalHistory persistHistory(User doctor, User patientUser, Set<String> tags) {
        TurnAssigned turn = entityManager.persistAndFlush(TurnAssigned.builder()
                .doctor(doctor)
                .patient(patientUser)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build());

        MedicalHistory history = MedicalHistory.builder()
                .doctor(doctor)
                .patient(patientUser)
                .turn(turn)
                .content("note")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tags(new java.util.HashSet<>(tags))
                .build();
        return entityManager.persistAndFlush(history);
    }

    private User createUser(String email, Long dni, String role) {
        User user = new User();
        user.setEmail(email);
        user.setDni(dni);
        user.setPasswordHash("hash");
        user.setName("Test");
        user.setSurname("User");
        user.setPhone("1234567890");
        user.setBirthdate(LocalDate.of(1990, 1, 1));
        user.setGender("MALE");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return user;
    }
}
