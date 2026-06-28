package com.medibook.api.service;

import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.MedicalHistoryRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.security.MedicalHistoryAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicalHistoryServiceTest {

    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;

    @Mock
    private TurnAssignedRepository turnAssignedRepository;

    @Mock
    private BadgeEvaluationTriggerService badgeEvaluationTrigger;

    @Mock
    private MedicalHistoryAuthorization medicalHistoryAuthorization;

    @InjectMocks
    private MedicalHistoryService medicalHistoryService;

    private User doctor;
    private User patient;
    private MedicalHistory medicalHistory;
    private TurnAssigned turn;
    private UUID doctorId;
    private UUID patientId;
    private UUID historyId;
    private UUID turnId;
    private OffsetDateTime scheduledAt;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        historyId = UUID.randomUUID();
        turnId = UUID.randomUUID();
        scheduledAt = OffsetDateTime.now();

        doctor = new User();
        doctor.setId(doctorId);
        doctor.setName("Dr. John");
        doctor.setSurname("Smith");
        doctor.setRole("DOCTOR");
        doctor.setStatus("ACTIVE");

        patient = new User();
        patient.setId(patientId);
        patient.setName("Patient");
        patient.setSurname("One");
        patient.setRole("PATIENT");
        patient.setStatus("ACTIVE");

        turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledAt)
                .status("COMPLETED")
                .build();

        medicalHistory = new MedicalHistory();
        medicalHistory.setId(historyId);
        medicalHistory.setDoctor(doctor);
        medicalHistory.setPatient(patient);
        medicalHistory.setContent("Test medical history content");
        medicalHistory.setCreatedAt(LocalDateTime.now());
        medicalHistory.setUpdatedAt(LocalDateTime.now());
        medicalHistory.setTurn(turn);
    }

    @Test
    void addMedicalHistory_Success() {
        String content = "New medical history entry";

        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(turn));
        when(medicalHistoryRepository.existsByTurn_Id(turnId)).thenReturn(false);
        when(medicalHistoryRepository.save(any(MedicalHistory.class))).thenReturn(medicalHistory);

        MedicalHistoryDTO result = medicalHistoryService.addMedicalHistory(doctorId, turnId, content);

        assertNotNull(result);
        assertEquals(historyId, result.getId());
        assertEquals(doctorId, result.getDoctorId());
        assertEquals(patientId, result.getPatientId());
        assertEquals("Dr. John", result.getDoctorName());
        assertEquals("Smith", result.getDoctorSurname());
        assertEquals("Patient", result.getPatientName());
        assertEquals("One", result.getPatientSurname());
        assertEquals(turnId, result.getTurnId());

        verify(turnAssignedRepository).findById(turnId);
        verify(medicalHistoryRepository).existsByTurn_Id(turnId);
        verify(medicalHistoryRepository).save(any(MedicalHistory.class));
    }

    @Test
    void addMedicalHistory_TurnNotFound() {
        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "content"));

        assertEquals("Turn not found", exception.getMessage());
        verify(turnAssignedRepository).findById(turnId);
        verifyNoInteractions(medicalHistoryRepository);
    }

    @Test
    void addMedicalHistory_DoctorMismatch() {
    User otherDoctor = new User();
    otherDoctor.setId(UUID.randomUUID());
    otherDoctor.setRole("DOCTOR");
    otherDoctor.setStatus("ACTIVE");
    otherDoctor.setName("Other");
    otherDoctor.setSurname("Doctor");

    TurnAssigned foreignTurn = createTurn(otherDoctor, patient);

        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(foreignTurn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "content"));

        assertEquals("Doctor can only add medical history for their own turns", exception.getMessage());
        verify(turnAssignedRepository).findById(turnId);
        verifyNoInteractions(medicalHistoryRepository);
    }

    @Test
    void addMedicalHistory_TurnWithoutPatient() {
    TurnAssigned patientlessTurn = createTurn(doctor, null);
        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(patientlessTurn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "content"));

        assertEquals("Turn must have an assigned patient before recording medical history", exception.getMessage());
        verify(turnAssignedRepository).findById(turnId);
        verifyNoInteractions(medicalHistoryRepository);
    }

    @Test
    void addMedicalHistory_DoctorInactive() {
    User inactiveDoctor = new User();
    inactiveDoctor.setId(doctorId);
    inactiveDoctor.setRole("DOCTOR");
    inactiveDoctor.setStatus("INACTIVE");
    inactiveDoctor.setName("Dr. John");
    inactiveDoctor.setSurname("Smith");

    TurnAssigned inactiveDoctorTurn = createTurn(inactiveDoctor, patient);
        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(inactiveDoctorTurn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "content"));

        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        verify(turnAssignedRepository).findById(turnId);
        verifyNoInteractions(medicalHistoryRepository);
    }

    @Test
    void addMedicalHistory_PatientInactive() {
    User inactivePatient = new User();
    inactivePatient.setId(patientId);
    inactivePatient.setRole("PATIENT");
    inactivePatient.setStatus("INACTIVE");
    inactivePatient.setName("Patient");
    inactivePatient.setSurname("One");

    TurnAssigned inactivePatientTurn = createTurn(doctor, inactivePatient);
        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(inactivePatientTurn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "content"));

        assertEquals("Invalid patient or patient is not active", exception.getMessage());
        verify(turnAssignedRepository).findById(turnId);
        verifyNoInteractions(medicalHistoryRepository);
    }

    @Test
    void addMedicalHistory_AlreadyExistsForTurn() {
        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(turn));
        when(medicalHistoryRepository.existsByTurn_Id(turnId)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "content"));

        assertEquals("Medical history already exists for this turn", exception.getMessage());
        verify(turnAssignedRepository).findById(turnId);
        verify(medicalHistoryRepository).existsByTurn_Id(turnId);
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void getPatientMedicalHistory_Success() {
        List<MedicalHistory> histories = Arrays.asList(medicalHistory);
        when(medicalHistoryRepository.findByPatient_IdOrderByCreatedAtDesc(patientId)).thenReturn(histories);

        List<MedicalHistoryDTO> result = medicalHistoryService.getPatientMedicalHistory(patientId);

        assertEquals(1, result.size());
        MedicalHistoryDTO dto = result.get(0);
        assertEquals(historyId, dto.getId());
        assertEquals("Test medical history content", dto.getContent());

        verify(medicalHistoryRepository).findByPatient_IdOrderByCreatedAtDesc(patientId);
    }

    @Test
    void getPatientMedicalHistoryAuthorized_RelatedDoctor_Success() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        doctor, null, java.util.List.of());
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(true);
        when(medicalHistoryRepository.findByPatient_IdOrderByCreatedAtDesc(patientId))
                .thenReturn(Arrays.asList(medicalHistory));

        List<MedicalHistoryDTO> result =
                medicalHistoryService.getPatientMedicalHistoryAuthorized(auth, patientId);

        assertEquals(1, result.size());
        verify(medicalHistoryAuthorization).canRead(auth, patientId);
        verify(medicalHistoryRepository).findByPatient_IdOrderByCreatedAtDesc(patientId);
    }

    @Test
    void getPatientMedicalHistoryAuthorized_UnrelatedDoctor_Throws() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        doctor, null, java.util.List.of());
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> medicalHistoryService.getPatientMedicalHistoryAuthorized(auth, patientId));

        verify(medicalHistoryAuthorization).canRead(auth, patientId);
        verify(medicalHistoryRepository, never()).findByPatient_IdOrderByCreatedAtDesc(any());
    }

    @Test
    void getMedicalHistoryById_RelatedDoctor_ReturnsEntry() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        doctor, null, java.util.List.of());
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(true);

        MedicalHistoryDTO result = medicalHistoryService.getMedicalHistoryById(auth, historyId);

        assertNotNull(result);
        assertEquals(historyId, result.getId());
        assertEquals(patientId, result.getPatientId());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryAuthorization).canRead(auth, patientId);
    }

    @Test
    void getMedicalHistoryById_UnrelatedDoctor_Throws() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        doctor, null, java.util.List.of());
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> medicalHistoryService.getMedicalHistoryById(auth, historyId));

        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryAuthorization).canRead(auth, patientId);
    }

    @Test
    void getMedicalHistoryById_PatientReadingOwn_ReturnsEntry() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        patient, null, java.util.List.of());
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(true);

        MedicalHistoryDTO result = medicalHistoryService.getMedicalHistoryById(auth, historyId);

        assertNotNull(result);
        assertEquals(historyId, result.getId());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryAuthorization).canRead(auth, patientId);
    }

    @Test
    void getMedicalHistoryById_PatientReadingOthers_Throws() {
        User otherPatient = new User();
        otherPatient.setId(UUID.randomUUID());
        otherPatient.setRole("PATIENT");
        otherPatient.setStatus("ACTIVE");
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        otherPatient, null, java.util.List.of());
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> medicalHistoryService.getMedicalHistoryById(auth, historyId));

        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryAuthorization).canRead(auth, patientId);
    }

    @Test
    void getMedicalHistoryById_Admin_ReturnsEntry() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, java.util.List.of());
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));
        when(medicalHistoryAuthorization.canRead(auth, patientId)).thenReturn(true);

        MedicalHistoryDTO result = medicalHistoryService.getMedicalHistoryById(auth, historyId);

        assertNotNull(result);
        assertEquals(historyId, result.getId());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryAuthorization).canRead(auth, patientId);
    }

    @Test
    void updateMedicalHistory_Success() {
        String newContent = "Updated medical history content";
        medicalHistory.setContent(newContent);

        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));
        when(medicalHistoryRepository.save(any(MedicalHistory.class))).thenReturn(medicalHistory);

        MedicalHistoryDTO result = medicalHistoryService.updateMedicalHistory(doctorId, historyId, newContent);

        assertNotNull(result);
        assertEquals(historyId, result.getId());
        assertEquals(newContent, result.getContent());

        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryRepository).save(medicalHistory);
    }

    @Test
    void updateMedicalHistory_NotFound() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.updateMedicalHistory(doctorId, historyId, "content"));

        assertEquals("Medical history entry not found", exception.getMessage());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void updateMedicalHistory_WrongDoctor() {
        UUID wrongDoctorId = UUID.randomUUID();
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.updateMedicalHistory(wrongDoctorId, historyId, "content"));

        assertEquals("Doctor can only update their own medical history entries", exception.getMessage());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void getDoctorMedicalHistoryEntries_Success() {
        List<MedicalHistory> histories = Arrays.asList(medicalHistory);
        when(medicalHistoryRepository.findByDoctor_IdOrderByCreatedAtDesc(doctorId)).thenReturn(histories);

        List<MedicalHistoryDTO> result = medicalHistoryService.getDoctorMedicalHistoryEntries(doctorId);

        assertEquals(1, result.size());
        MedicalHistoryDTO dto = result.get(0);
        assertEquals(historyId, dto.getId());
        assertEquals("Test medical history content", dto.getContent());

        verify(medicalHistoryRepository).findByDoctor_IdOrderByCreatedAtDesc(doctorId);
    }

    @Test
    void getPatientMedicalHistoryByDoctor_Success() {
        List<MedicalHistory> histories = Arrays.asList(medicalHistory);
        when(medicalHistoryRepository.findByPatient_IdAndDoctor_IdOrderByCreatedAtDesc(patientId, doctorId)).thenReturn(histories);

        List<MedicalHistoryDTO> result = medicalHistoryService.getPatientMedicalHistoryByDoctor(patientId, doctorId);

        assertEquals(1, result.size());
        MedicalHistoryDTO dto = result.get(0);
        assertEquals(historyId, dto.getId());
        assertEquals("Test medical history content", dto.getContent());

        verify(medicalHistoryRepository).findByPatient_IdAndDoctor_IdOrderByCreatedAtDesc(patientId, doctorId);
    }

    @Test
    void getLatestMedicalHistoryContent_Success() {
        when(medicalHistoryRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patientId)).thenReturn(medicalHistory);

        String result = medicalHistoryService.getLatestMedicalHistoryContent(patientId);

        assertEquals("Test medical history content", result);
        verify(medicalHistoryRepository).findFirstByPatient_IdOrderByCreatedAtDesc(patientId);
    }

    @Test
    void getLatestMedicalHistoryContent_NoHistory() {
        when(medicalHistoryRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patientId)).thenReturn(null);

        String result = medicalHistoryService.getLatestMedicalHistoryContent(patientId);

        assertNull(result);
        verify(medicalHistoryRepository).findFirstByPatient_IdOrderByCreatedAtDesc(patientId);
    }

    @Test
    void deleteMedicalHistory_Success() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));

        assertDoesNotThrow(() -> medicalHistoryService.deleteMedicalHistory(doctorId, historyId));

        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryRepository).delete(medicalHistory);
    }

    @Test
    void deleteMedicalHistory_NotFound() {
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.deleteMedicalHistory(doctorId, historyId));

        assertEquals("Medical history entry not found", exception.getMessage());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryRepository, never()).delete(any());
    }

    @Test
    void deleteMedicalHistory_WrongDoctor() {
        UUID wrongDoctorId = UUID.randomUUID();
        when(medicalHistoryRepository.findById(historyId)).thenReturn(Optional.of(medicalHistory));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> medicalHistoryService.deleteMedicalHistory(wrongDoctorId, historyId));

        assertEquals("Doctor can only delete their own medical history entries", exception.getMessage());
        verify(medicalHistoryRepository).findById(historyId);
        verify(medicalHistoryRepository, never()).delete(any());
    }

    @Test
    void addMedicalHistory_DoctorNotFound_ShouldThrowException() {
    User adminDoctor = new User();
    adminDoctor.setId(doctorId);
    adminDoctor.setRole("ADMIN");
    adminDoctor.setStatus("ACTIVE");
    adminDoctor.setName("Admin");
    adminDoctor.setSurname("Doctor");

    TurnAssigned invalidRoleTurn = createTurn(adminDoctor, patient);
    when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(invalidRoleTurn));

    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> medicalHistoryService.addMedicalHistory(doctorId, turnId, "Test content"));

    assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
    verify(turnAssignedRepository).findById(turnId);
    }

    private TurnAssigned createTurn(User doctorEntity, User patientEntity) {
    return TurnAssigned.builder()
        .id(turnId)
        .doctor(doctorEntity)
        .patient(patientEntity)
        .scheduledAt(scheduledAt)
        .status("COMPLETED")
        .build();
    }
}