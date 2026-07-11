package com.medibook.api.repository;

import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class TurnAssignedRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TurnAssignedRepository turnAssignedRepository;

    @Autowired
    private UserRepository userRepository;

    private User doctorUser;
    private User patientUser;
    private User otherPatientUser;
    private TurnAssigned turnAssigned1;
    private TurnAssigned turnAssigned2;
    private TurnAssigned turnAssigned3;

    @BeforeEach
    void setUp() {
        turnAssignedRepository.deleteAll();
        userRepository.deleteAll();

        doctorUser = createUser("doctor@test.com", 12345678L, "DOCTOR", "ACTIVE");
        patientUser = createUser("patient@test.com", 87654321L, "PATIENT", "ACTIVE");
        otherPatientUser = createUser("other.patient@test.com", 11111111L, "PATIENT", "ACTIVE");
        
        doctorUser = entityManager.persistAndFlush(doctorUser);
        patientUser = entityManager.persistAndFlush(patientUser);
        otherPatientUser = entityManager.persistAndFlush(otherPatientUser);

        turnAssigned1 = createTurnAssigned(doctorUser, patientUser, 
            OffsetDateTime.now().plusDays(1), "SCHEDULED");
        turnAssigned2 = createTurnAssigned(doctorUser, otherPatientUser, 
            OffsetDateTime.now().plusDays(2), "RESERVED");
        turnAssigned3 = createTurnAssigned(doctorUser, patientUser, 
            OffsetDateTime.now().plusDays(3), "CANCELED");
        
        turnAssigned1 = entityManager.persistAndFlush(turnAssigned1);
        turnAssigned2 = entityManager.persistAndFlush(turnAssigned2);
        turnAssigned3 = entityManager.persistAndFlush(turnAssigned3);

        entityManager.clear();
    }

    private User createUser(String email, Long dni, String role, String status) {
        User user = new User();
        // No asignar ID - dejar que JPA lo genere automáticamente
        user.setEmail(email);
        user.setDni(dni);
        user.setPasswordHash("hashedPassword");
        user.setName("Test");
        user.setSurname("User");
        user.setPhone("1234567890");
        user.setBirthdate(LocalDate.of(1990, 1, 1));
        user.setGender("MALE");
        user.setEmailVerified(true);
        user.setStatus(status);
        user.setRole(role);
        return user;
    }

    private TurnAssigned createTurnAssigned(User doctor, User patient, 
                                           OffsetDateTime scheduledAt, String status) {
        TurnAssigned turn = new TurnAssigned();
        // No asignar ID - dejar que JPA lo genere automáticamente
        turn.setDoctor(doctor);
        turn.setPatient(patient);
        turn.setScheduledAt(scheduledAt);
        turn.setStatus(status);
        return turn;
    }

    @Test
    void findByDoctor_IdOrderByScheduledAtDesc_Success() {        
        List<TurnAssigned> result = turnAssignedRepository
            .findByDoctor_IdOrderByScheduledAtDesc(doctorUser.getId());
        assertNotNull(result);
        assertEquals(3, result.size());
        
        assertTrue(result.get(0).getScheduledAt().isAfter(result.get(1).getScheduledAt()));
        assertTrue(result.get(1).getScheduledAt().isAfter(result.get(2).getScheduledAt()));
        
        result.forEach(turn -> assertEquals(doctorUser.getId(), turn.getDoctor().getId()));
    }

    @Test
    void findByDoctor_IdOrderByScheduledAtDesc_EmptyResult() {
        UUID nonExistentDoctorId = UUID.randomUUID();
        List<TurnAssigned> result = turnAssignedRepository
            .findByDoctor_IdOrderByScheduledAtDesc(nonExistentDoctorId);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByPatient_IdOrderByScheduledAtDesc_Success() {
        List<TurnAssigned> result = turnAssignedRepository
            .findByPatient_IdOrderByScheduledAtDesc(patientUser.getId());
        assertNotNull(result);
        assertEquals(2, result.size());
        
        assertTrue(result.get(0).getScheduledAt().isAfter(result.get(1).getScheduledAt()));
        
        result.forEach(turn -> assertEquals(patientUser.getId(), turn.getPatient().getId()));
    }

    @Test
    void findByPatient_IdOrderByScheduledAtDesc_EmptyResult() {
        UUID nonExistentPatientId = UUID.randomUUID();
        List<TurnAssigned> result = turnAssignedRepository
            .findByPatient_IdOrderByScheduledAtDesc(nonExistentPatientId);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByDoctor_IdAndStatusOrderByScheduledAtDesc_Success() {
        List<TurnAssigned> scheduledTurns = turnAssignedRepository
            .findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorUser.getId(), "SCHEDULED");
        
        List<TurnAssigned> reservedTurns = turnAssignedRepository
            .findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorUser.getId(), "RESERVED");
        
        List<TurnAssigned> cancelledTurns = turnAssignedRepository
            .findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorUser.getId(), "CANCELED");
        assertEquals(1, scheduledTurns.size());
        assertEquals("SCHEDULED", scheduledTurns.get(0).getStatus());
        
        assertEquals(1, reservedTurns.size());
        assertEquals("RESERVED", reservedTurns.get(0).getStatus());
        
        assertEquals(1, cancelledTurns.size());
        assertEquals("CANCELED", cancelledTurns.get(0).getStatus());
    }

    @Test
    void findByDoctor_IdAndStatusOrderByScheduledAtDesc_NoMatches() {
        List<TurnAssigned> result = turnAssignedRepository
            .findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorUser.getId(), "COMPLETED");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByPatient_IdAndStatusOrderByScheduledAtDesc_Success() {
        List<TurnAssigned> scheduledTurns = turnAssignedRepository
            .findByPatient_IdAndStatusOrderByScheduledAtDesc(patientUser.getId(), "SCHEDULED");
        
        List<TurnAssigned> cancelledTurns = turnAssignedRepository
            .findByPatient_IdAndStatusOrderByScheduledAtDesc(patientUser.getId(), "CANCELED");
        assertEquals(1, scheduledTurns.size());
        assertEquals("SCHEDULED", scheduledTurns.get(0).getStatus());
        assertEquals(patientUser.getId(), scheduledTurns.get(0).getPatient().getId());
        
        assertEquals(1, cancelledTurns.size());
        assertEquals("CANCELED", cancelledTurns.get(0).getStatus());
        assertEquals(patientUser.getId(), cancelledTurns.get(0).getPatient().getId());
    }

    @Test
    void findByPatient_IdAndStatusOrderByScheduledAtDesc_NoMatches() {
        List<TurnAssigned> result = turnAssignedRepository
            .findByPatient_IdAndStatusOrderByScheduledAtDesc(patientUser.getId(), "RESERVED");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void existsByDoctor_IdAndScheduledAt_ExistingTurn_ReturnsTrue() {
        // Usar truncado a segundos para evitar problemas de microsegundos
        OffsetDateTime testTime = OffsetDateTime.now().plusDays(5).withNano(0);
        
        // Crear un turno nuevo con tiempo controlado
        TurnAssigned testTurn = new TurnAssigned();
        testTurn.setDoctor(doctorUser);
        testTurn.setPatient(patientUser);
        testTurn.setScheduledAt(testTime);
        testTurn.setStatus("SCHEDULED");
        
        turnAssignedRepository.saveAndFlush(testTurn);
        
        // Verificar que existe con el tiempo exacto
        boolean exists = turnAssignedRepository
            .existsByDoctor_IdAndScheduledAt(doctorUser.getId(), testTime);
        assertTrue(exists);
    }

    @Test
    void existsByDoctor_IdAndScheduledAt_NonExistingTurn_ReturnsFalse() {
        OffsetDateTime nonExistentTime = OffsetDateTime.now().plusDays(10);
        boolean exists = turnAssignedRepository
            .existsByDoctor_IdAndScheduledAt(doctorUser.getId(), nonExistentTime);
        assertFalse(exists);
    }

    @Test
    void existsByDoctor_IdAndScheduledAt_NonExistingDoctor_ReturnsFalse() {
        UUID nonExistentDoctorId = UUID.randomUUID();
        boolean exists = turnAssignedRepository
            .existsByDoctor_IdAndScheduledAt(nonExistentDoctorId, turnAssigned1.getScheduledAt());
        assertFalse(exists);
    }

    @Test
    void findTurnsInDateRange_Success() {
        OffsetDateTime startDate = OffsetDateTime.now();
        OffsetDateTime endDate = OffsetDateTime.now().plusDays(4);

        List<TurnAssigned> allTurns = turnAssignedRepository.findAll();
        List<TurnAssigned> turnsInRange = allTurns.stream()
            .filter(turn -> turn.getScheduledAt().isAfter(startDate) && 
                           turn.getScheduledAt().isBefore(endDate))
            .toList();        assertEquals(3, turnsInRange.size());
    }

    @Test
    void findTurnsByMultipleCriteria_Success() {
        List<TurnAssigned> activeTurns = turnAssignedRepository.findAll().stream()
            .filter(turn -> !turn.getStatus().equals("CANCELED"))
            .filter(turn -> turn.getScheduledAt().isAfter(OffsetDateTime.now()))
            .toList();        assertEquals(2, activeTurns.size());
        activeTurns.forEach(turn -> assertNotEquals("CANCELED", turn.getStatus()));
    }

    @Test
    void turnAssigned_DoctorRelationship_Success() {
        TurnAssigned turn = turnAssignedRepository.findById(turnAssigned1.getId()).orElse(null);
        assertNotNull(turn);
        assertNotNull(turn.getDoctor());
        assertEquals(doctorUser.getId(), turn.getDoctor().getId());
        assertEquals("DOCTOR", turn.getDoctor().getRole());
    }

    @Test
    void turnAssigned_PatientRelationship_Success() {
        TurnAssigned turn = turnAssignedRepository.findById(turnAssigned1.getId()).orElse(null);
        assertNotNull(turn);
        assertNotNull(turn.getPatient());
        assertEquals(patientUser.getId(), turn.getPatient().getId());
        assertEquals("PATIENT", turn.getPatient().getRole());
    }

    @Test
    void saveTurnAssigned_Success() {        
        TurnAssigned newTurn = createTurnAssigned(doctorUser, patientUser, 
            OffsetDateTime.now().plusDays(5), "SCHEDULED");        
            TurnAssigned savedTurn = turnAssignedRepository.save(newTurn);        
            assertNotNull(savedTurn);
        assertNotNull(savedTurn.getId());
        assertEquals("SCHEDULED", savedTurn.getStatus());
        assertEquals(doctorUser.getId(), savedTurn.getDoctor().getId());
        assertEquals(patientUser.getId(), savedTurn.getPatient().getId());
    }

    @Test
    void updateTurnAssigned_Success() {
        TurnAssigned turn = turnAssignedRepository.findById(turnAssigned1.getId()).orElse(null);
        assertNotNull(turn);
        
        turn.setStatus("COMPLETED");
        TurnAssigned updatedTurn = turnAssignedRepository.save(turn);
        assertNotNull(updatedTurn);
        assertEquals("COMPLETED", updatedTurn.getStatus());
        assertEquals(turnAssigned1.getId(), updatedTurn.getId());
    }

    @Test
    void deleteTurnAssigned_Success() {
        UUID turnId = turnAssigned1.getId();
        assertTrue(turnAssignedRepository.existsById(turnId));
        turnAssignedRepository.deleteById(turnId);
        assertFalse(turnAssignedRepository.existsById(turnId));
    }

    @Test
    void findByDoctor_LargeDateRange_Performance() {
        for (int i = 0; i < 50; i++) {
            TurnAssigned turn = createTurnAssigned(doctorUser, patientUser, 
                OffsetDateTime.now().plusDays(i + 10), "SCHEDULED");
            entityManager.persistAndFlush(turn);
        }
        entityManager.clear();        long startTime = System.currentTimeMillis();
        List<TurnAssigned> turns = turnAssignedRepository
            .findByDoctor_IdOrderByScheduledAtDesc(doctorUser.getId());
        long endTime = System.currentTimeMillis();        assertNotNull(turns);
        assertEquals(53, turns.size());
        assertTrue((endTime - startTime) < 1000);
    }

    @Test
    void findTurns_EdgeCaseDates_Success() {
        TurnAssigned pastTurn = createTurnAssigned(doctorUser, patientUser, 
            OffsetDateTime.now().minusDays(1), "COMPLETED");
        TurnAssigned farFutureTurn = createTurnAssigned(doctorUser, patientUser, 
            OffsetDateTime.now().plusYears(1), "SCHEDULED");
        
        entityManager.persistAndFlush(pastTurn);
        entityManager.persistAndFlush(farFutureTurn);
        entityManager.clear();        
        List<TurnAssigned> allTurns = turnAssignedRepository
            .findByDoctor_IdOrderByScheduledAtDesc(doctorUser.getId());
        assertEquals(5, allTurns.size());

        for (int i = 0; i < allTurns.size() - 1; i++) {
            assertTrue(allTurns.get(i).getScheduledAt()
                .isAfter(allTurns.get(i + 1).getScheduledAt()));
        }
    }

    @Test
    void concurrentAccess_MultipleReads_Success() {
        List<TurnAssigned> result1 = turnAssignedRepository
            .findByDoctor_IdOrderByScheduledAtDesc(doctorUser.getId());
        List<TurnAssigned> result2 = turnAssignedRepository
            .findByPatient_IdOrderByScheduledAtDesc(patientUser.getId());        
            assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(3, result1.size());
        assertEquals(2, result2.size());
    }

    @Test
    void saveTurnWithNullDoctor_ShouldFail() {        
        TurnAssigned turn = createTurnAssigned(null, patientUser, 
            OffsetDateTime.now().plusDays(1), "SCHEDULED");        
            assertThrows(Exception.class, () -> {
            turnAssignedRepository.saveAndFlush(turn);
        });
    }

    @Test
    void saveTurnWithNullPatient_ShouldSucceed() {        
        // Como patient_id no tiene nullable=false, debería permitir null
        TurnAssigned turn = new TurnAssigned();
        turn.setDoctor(doctorUser);
        turn.setPatient(null); 
        turn.setScheduledAt(OffsetDateTime.now().plusDays(1));
        turn.setStatus("SCHEDULED");
        
        // No debería lanzar excepción
        assertDoesNotThrow(() -> {
            turnAssignedRepository.saveAndFlush(turn);
        });
    }

    @Test
    void saveTurnWithNullScheduledAt_ShouldFail() {        
        TurnAssigned turn = new TurnAssigned();
        // No asignar ID - dejar que JPA lo genere automáticamente
        turn.setDoctor(doctorUser);
        turn.setPatient(patientUser);
        turn.setScheduledAt(null); // scheduled_at es NOT NULL, debería fallar
        turn.setStatus("SCHEDULED");
        assertThrows(Exception.class, () -> {
            turnAssignedRepository.saveAndFlush(turn);
        });
    }

    @Test
    void saveTurnWithMotive_Success() {
        TurnAssigned turn = createTurnAssigned(doctorUser, patientUser,
            OffsetDateTime.now().plusDays(7), "SCHEDULED");
        turn.setMotive("Consulta por dolor de cabeza");

        TurnAssigned saved = turnAssignedRepository.saveAndFlush(turn);
        entityManager.clear();

        TurnAssigned found = turnAssignedRepository.findById(saved.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("Consulta por dolor de cabeza", found.getMotive());
    }
}