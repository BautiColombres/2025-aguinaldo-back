package com.medibook.api.repository;

import com.medibook.api.entity.FollowUpReminder;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slice tests for {@link FollowUpReminderRepository}.
 *
 * <p>NOTE: H2 uses the Hibernate-generated schema (Liquibase disabled in tests),
 * so this validates the JPA mapping + derived queries, NOT the 0016 Liquibase DDL
 * — the Postgres e2e remains the real gate for the changelog.
 */
@DataJpaTest
@ActiveProfiles("test")
class FollowUpReminderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FollowUpReminderRepository followUpReminderRepository;

    private User doctorA;
    private User doctorB;
    private User patient;
    private User otherPatient;

    @BeforeEach
    void setUp() {
        doctorA = entityManager.persistAndFlush(createUser("fur-doctor-a@test.com", 40000001L, "DOCTOR"));
        doctorB = entityManager.persistAndFlush(createUser("fur-doctor-b@test.com", 40000002L, "DOCTOR"));
        patient = entityManager.persistAndFlush(createUser("fur-patient@test.com", 40000003L, "PATIENT"));
        otherPatient = entityManager.persistAndFlush(createUser("fur-other-patient@test.com", 40000004L, "PATIENT"));
    }

    @Test
    void findDue_includesReminderDueExactlyToday() {
        LocalDate today = LocalDate.now();
        persistReminder(doctorA, patient, today, false);

        List<FollowUpReminder> due = followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(doctorA.getId(), today);

        assertEquals(1, due.size());
    }

    @Test
    void findDue_includesPastDueNonDismissed_noExpiry() {
        LocalDate today = LocalDate.now();
        persistReminder(doctorA, patient, today.minusMonths(4), false);

        List<FollowUpReminder> due = followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(doctorA.getId(), today);

        assertEquals(1, due.size());
    }

    @Test
    void findDue_excludesDismissed() {
        LocalDate today = LocalDate.now();
        persistReminder(doctorA, patient, today.minusDays(1), true);

        List<FollowUpReminder> due = followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(doctorA.getId(), today);

        assertTrue(due.isEmpty());
    }

    @Test
    void findDue_excludesNotYetDue() {
        LocalDate today = LocalDate.now();
        persistReminder(doctorA, patient, today.plusMonths(2), false);

        List<FollowUpReminder> due = followUpReminderRepository
                .findByDoctor_IdAndDismissedFalseAndScheduledForLessThanEqual(doctorA.getId(), today);

        assertTrue(due.isEmpty());
    }

    @Test
    void existsActiveForHistory_trueWhenNonDismissed_falseWhenDismissed() {
        FollowUpReminder active = persistReminder(doctorA, patient, LocalDate.now(), false);
        FollowUpReminder dismissed = persistReminder(doctorA, otherPatient, LocalDate.now(), true);

        assertTrue(followUpReminderRepository
                .existsByMedicalHistory_IdAndDismissedFalse(active.getMedicalHistory().getId()));
        assertFalse(followUpReminderRepository
                .existsByMedicalHistory_IdAndDismissedFalse(dismissed.getMedicalHistory().getId()));
        assertFalse(followUpReminderRepository
                .existsByMedicalHistory_IdAndDismissedFalse(UUID.randomUUID()));
    }

    @Test
    void findByIdAndDoctor_scopedToOwner() {
        FollowUpReminder reminder = persistReminder(doctorA, patient, LocalDate.now(), false);

        Optional<FollowUpReminder> asOwner = followUpReminderRepository
                .findByIdAndDoctor_Id(reminder.getId(), doctorA.getId());
        Optional<FollowUpReminder> asForeign = followUpReminderRepository
                .findByIdAndDoctor_Id(reminder.getId(), doctorB.getId());

        assertTrue(asOwner.isPresent());
        assertTrue(asForeign.isEmpty());
    }

    @Test
    void findForPatient_onlyOwnNonDismissed_orderedByScheduledForAsc() {
        persistReminder(doctorA, patient, LocalDate.now().plusMonths(2), false);
        persistReminder(doctorA, patient, LocalDate.now().plusMonths(1), false);
        persistReminder(doctorA, patient, LocalDate.now(), true);            // dismissed -> excluded
        persistReminder(doctorA, otherPatient, LocalDate.now(), false);      // other patient -> excluded

        List<FollowUpReminder> result = followUpReminderRepository
                .findByPatient_IdAndDismissedFalseOrderByScheduledForAsc(patient.getId());

        assertEquals(2, result.size());
        assertTrue(result.get(0).getScheduledFor().isBefore(result.get(1).getScheduledFor()));
        result.forEach(r -> {
            assertEquals(patient.getId(), r.getPatient().getId());
            assertFalse(r.isDismissed());
        });
    }

    @Test
    void findByDoctorAndPatient_onlyNonDismissed_scopedToDoctorAndPatient_orderedByScheduledForAsc() {
        persistReminder(doctorA, patient, LocalDate.now().plusMonths(2), false);
        persistReminder(doctorA, patient, LocalDate.now().plusMonths(1), false);
        persistReminder(doctorA, patient, LocalDate.now(), true);          // dismissed -> excluded
        persistReminder(doctorB, patient, LocalDate.now(), false);         // other doctor -> excluded
        persistReminder(doctorA, otherPatient, LocalDate.now(), false);    // other patient -> excluded

        List<FollowUpReminder> result = followUpReminderRepository
                .findByDoctor_IdAndPatient_IdAndDismissedFalseOrderByScheduledForAsc(
                        doctorA.getId(), patient.getId());

        assertEquals(2, result.size());
        assertTrue(result.get(0).getScheduledFor().isBefore(result.get(1).getScheduledFor()));
        result.forEach(r -> {
            assertEquals(doctorA.getId(), r.getDoctor().getId());
            assertEquals(patient.getId(), r.getPatient().getId());
            assertFalse(r.isDismissed());
        });
    }

    @Test
    void findByDoctorAndPatient_noReminders_returnsEmpty() {
        List<FollowUpReminder> result = followUpReminderRepository
                .findByDoctor_IdAndPatient_IdAndDismissedFalseOrderByScheduledForAsc(
                        doctorA.getId(), patient.getId());

        assertTrue(result.isEmpty());
    }

    private FollowUpReminder persistReminder(User doctor, User patientUser, LocalDate scheduledFor, boolean dismissed) {
        TurnAssigned turn = entityManager.persistAndFlush(TurnAssigned.builder()
                .doctor(doctor)
                .patient(patientUser)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build());

        MedicalHistory history = entityManager.persistAndFlush(MedicalHistory.builder()
                .doctor(doctor)
                .patient(patientUser)
                .turn(turn)
                .content("note")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        return entityManager.persistAndFlush(FollowUpReminder.builder()
                .medicalHistory(history)
                .doctor(doctor)
                .patient(patientUser)
                .monthsUntilControl(3)
                .scheduledFor(scheduledFor)
                .dismissed(dismissed)
                .createdAt(LocalDateTime.now())
                .build());
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
