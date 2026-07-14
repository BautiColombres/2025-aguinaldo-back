package com.medibook.api.service;

import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.dto.PatientDTO;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.DoctorMapper;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import com.medibook.api.repository.BadgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TurnAssignedRepository turnAssignedRepository;
    
    @Mock
    private DoctorMapper doctorMapper;

    @Mock
    private MedicalHistoryService medicalHistoryService;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private BadgeService badgeService;

    @InjectMocks
    private DoctorService doctorService;

    private UUID doctorId;
    private UUID patientId1;
    private UUID patientId2;
    private UUID turnId;
    private TurnAssigned turn;
    private User doctor;
    private User patient1;
    private User patient2;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        patientId1 = UUID.randomUUID();
        patientId2 = UUID.randomUUID();
    turnId = UUID.randomUUID();

        doctor = new User();
        doctor.setId(doctorId);
        doctor.setName("Dr. John");
        doctor.setSurname("Smith");
        doctor.setEmail("doctor@example.com");
        doctor.setRole("DOCTOR");
        doctor.setStatus("ACTIVE");

        patient1 = new User();
        patient1.setId(patientId1);
        patient1.setName("Patient");
        patient1.setSurname("One");
        patient1.setEmail("patient1@example.com");
        patient1.setDni(12345678L);
        patient1.setPhone("555-1234");
        patient1.setBirthdate(LocalDate.of(1990, 1, 1));
        patient1.setGender("MALE");
        patient1.setRole("PATIENT");
        patient1.setStatus("ACTIVE");

        patient2 = new User();
        patient2.setId(patientId2);
        patient2.setName("Patient");
        patient2.setSurname("Two");
        patient2.setEmail("patient2@example.com");
        patient2.setDni(87654321L);
        patient2.setPhone("555-5678");
        patient2.setBirthdate(LocalDate.of(1985, 5, 15));
        patient2.setGender("FEMALE");
        patient2.setRole("PATIENT");
        patient2.setStatus("ACTIVE");

    turn = TurnAssigned.builder()
        .id(turnId)
        .doctor(doctor)
        .patient(patient1)
        .status("COMPLETED")
        .build();
    }

    @Test
    void getAllSpecialties_ActiveDoctorWithoutProfile_ExcludedNoNpe() {
        User doctorWithProfile = new User();
        doctorWithProfile.setId(UUID.randomUUID());
        doctorWithProfile.setRole("DOCTOR");
        doctorWithProfile.setStatus("ACTIVE");
        com.medibook.api.entity.DoctorProfile profile = new com.medibook.api.entity.DoctorProfile();
        profile.setSpecialty("Cardiology");
        doctorWithProfile.setDoctorProfile(profile);

        User doctorWithoutProfile = new User();
        doctorWithoutProfile.setId(UUID.randomUUID());
        doctorWithoutProfile.setRole("DOCTOR");
        doctorWithoutProfile.setStatus("ACTIVE");
        // Intentionally no doctorProfile -> previously NPE'd at getSpecialty()

        when(userRepository.findDoctorsByStatus("ACTIVE"))
                .thenReturn(Arrays.asList(doctorWithProfile, doctorWithoutProfile));

        List<String> result = assertDoesNotThrow(() -> doctorService.getAllSpecialties());

        assertEquals(List.of("Cardiology"), result);
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getPatientsByDoctor_Success() {
        List<User> patients = Arrays.asList(patient1, patient2);
        
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId)).thenReturn(patients);
        when(medicalHistoryService.getPatientMedicalHistory(patientId1)).thenReturn(Collections.emptyList());
        when(medicalHistoryService.getPatientMedicalHistory(patientId2)).thenReturn(Collections.emptyList());
        when(medicalHistoryService.getLatestMedicalHistoryContents(Arrays.asList(patientId1, patientId2))).thenReturn(Collections.emptyMap());
        when(ratingRepository.countSubcategoriesByRatedIds(Arrays.asList(patientId1, patientId2), "DOCTOR")).thenReturn(Collections.emptyList());

        List<PatientDTO> result = doctorService.getPatientsByDoctor(doctorId);

        assertEquals(2, result.size());
        
        PatientDTO patientDTO1 = result.get(0);
        assertEquals(patientId1, patientDTO1.getId());
        assertEquals("Patient", patientDTO1.getName());
        assertEquals("One", patientDTO1.getSurname());
        assertEquals("patient1@example.com", patientDTO1.getEmail());
        assertEquals(12345678L, patientDTO1.getDni());
        assertEquals("555-1234", patientDTO1.getPhone());
        assertEquals(LocalDate.of(1990, 1, 1), patientDTO1.getBirthdate());
        assertEquals("MALE", patientDTO1.getGender());
        assertEquals("ACTIVE", patientDTO1.getStatus());
        assertNotNull(patientDTO1.getMedicalHistories());
        assertTrue(patientDTO1.getMedicalHistories().isEmpty());

        PatientDTO patientDTO2 = result.get(1);
        assertEquals(patientId2, patientDTO2.getId());
        assertEquals("Patient", patientDTO2.getName());
        assertEquals("Two", patientDTO2.getSurname());
        assertEquals("patient2@example.com", patientDTO2.getEmail());
        assertEquals(87654321L, patientDTO2.getDni());
        assertEquals("555-5678", patientDTO2.getPhone());
        assertEquals(LocalDate.of(1985, 5, 15), patientDTO2.getBirthdate());
        assertEquals("FEMALE", patientDTO2.getGender());
        assertEquals("ACTIVE", patientDTO2.getStatus());
        assertNotNull(patientDTO2.getMedicalHistories());
        assertTrue(patientDTO2.getMedicalHistories().isEmpty());

        verify(userRepository).findById(doctorId);
        verify(turnAssignedRepository).findDistinctPatientsByDoctorId(doctorId);
        verify(medicalHistoryService).getPatientMedicalHistory(patientId1);
        verify(medicalHistoryService).getPatientMedicalHistory(patientId2);
        verify(medicalHistoryService).getLatestMedicalHistoryContents(Arrays.asList(patientId1, patientId2));
        verify(ratingRepository).countSubcategoriesByRatedIds(Arrays.asList(patientId1, patientId2), "DOCTOR");
    }

    @Test
    void getPatientsByDoctor_DoctorNotFound() {
        when(userRepository.findById(doctorId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> doctorService.getPatientsByDoctor(doctorId));
        
        assertEquals("Doctor not found", exception.getMessage());
        verify(userRepository).findById(doctorId);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_DoctorNotActiveRole() {
        doctor.setRole("PATIENT");
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> doctorService.getPatientsByDoctor(doctorId));
        
        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        verify(userRepository).findById(doctorId);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_DoctorNotActiveStatus() {
        doctor.setStatus("PENDING");
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> doctorService.getPatientsByDoctor(doctorId));
        
        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        verify(userRepository).findById(doctorId);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_EmptyPatientList() {
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId)).thenReturn(Collections.emptyList());
        when(medicalHistoryService.getLatestMedicalHistoryContents(Collections.emptyList())).thenReturn(Collections.emptyMap());
        when(ratingRepository.countSubcategoriesByRatedIds(Collections.emptyList(), "DOCTOR")).thenReturn(Collections.emptyList());

        List<PatientDTO> result = doctorService.getPatientsByDoctor(doctorId);

        assertTrue(result.isEmpty());
        verify(userRepository).findById(doctorId);
        verify(turnAssignedRepository).findDistinctPatientsByDoctorId(doctorId);
        verify(medicalHistoryService).getLatestMedicalHistoryContents(Collections.emptyList());
        verify(ratingRepository).countSubcategoriesByRatedIds(Collections.emptyList(), "DOCTOR");
    }

    @Test
    void updatePatientMedicalHistory_Success() {
        String medicalHistory = "Patient has diabetes";
        MedicalHistoryDTO expectedResult = MedicalHistoryDTO.builder()
                .id(UUID.randomUUID())
                .content(medicalHistory)
                .patientId(patientId1)
                .doctorId(doctorId)
                .build();

        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(turn));
        when(medicalHistoryService.addMedicalHistory(doctorId, turnId, medicalHistory, null))
                .thenReturn(expectedResult);

        assertDoesNotThrow(() ->
            doctorService.updatePatientMedicalHistory(doctorId, patientId1, turnId, medicalHistory));

        verify(turnAssignedRepository).findById(turnId);
        verify(medicalHistoryService).addMedicalHistory(doctorId, turnId, medicalHistory, null);
    }

    @Test
    void updatePatientMedicalHistory_TurnNotFound() {
    when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.empty());

    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> doctorService.updatePatientMedicalHistory(doctorId, patientId1, turnId, "notes"));

    assertEquals("Turn not found", exception.getMessage());
    verify(turnAssignedRepository).findById(turnId);
    verifyNoInteractions(medicalHistoryService);
    }

    @Test
    void updatePatientMedicalHistory_TurnBelongsToDifferentDoctor() {
    User otherDoctor = new User();
    otherDoctor.setId(UUID.randomUUID());
    otherDoctor.setRole("DOCTOR");
    otherDoctor.setStatus("ACTIVE");

    TurnAssigned foreignTurn = TurnAssigned.builder()
        .id(turnId)
        .doctor(otherDoctor)
        .patient(patient1)
        .status("COMPLETED")
        .build();

    when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(foreignTurn));

    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> doctorService.updatePatientMedicalHistory(doctorId, patientId1, turnId, "notes"));

    assertEquals("Doctor can only update medical history for their own turns", exception.getMessage());
    verify(turnAssignedRepository).findById(turnId);
    verifyNoInteractions(medicalHistoryService);
    }

    @Test
    void updatePatientMedicalHistory_TurnBelongsToDifferentPatient() {
    TurnAssigned foreignPatientTurn = TurnAssigned.builder()
        .id(turnId)
        .doctor(doctor)
        .patient(patient2)
        .status("COMPLETED")
        .build();

    when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(foreignPatientTurn));

    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> doctorService.updatePatientMedicalHistory(doctorId, patientId1, turnId, "notes"));

    assertEquals("Turn does not belong to the specified patient", exception.getMessage());
    verify(turnAssignedRepository).findById(turnId);
    verifyNoInteractions(medicalHistoryService);
    }

    @Test
    void getDoctorMetrics_Success() {
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Collections.emptyList());
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.emptyList());
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(doctorId, result.getDoctorId());
        assertEquals("Dr. John", result.getName());
        assertEquals("Smith", result.getSurname());
        verify(userRepository).findById(doctorId);
        verify(turnAssignedRepository).findByDoctor_IdOrderByScheduledAtDesc(doctorId);
    }

    @Test
    void getDoctorMetrics_DoctorNotFound_ThrowsException() {
        when(userRepository.findById(doctorId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> doctorService.getDoctorMetrics(doctorId));

        assertEquals("Doctor not found", exception.getMessage());
        verify(userRepository).findById(doctorId);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getDoctorMetrics_UserIsNotDoctor_ThrowsException() {
        User patient = new User();
        patient.setId(doctorId);
        patient.setRole("PATIENT");
        patient.setStatus("ACTIVE");

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(patient));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> doctorService.getDoctorMetrics(doctorId));

        assertEquals("User is not a doctor", exception.getMessage());
        verify(userRepository).findById(doctorId);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getDoctorMetrics_WithVariousTurnTypes_CalculatesCorrectly() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        java.time.OffsetDateTime startOfMonth = java.time.YearMonth.now().atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
        java.time.OffsetDateTime lastMonth = startOfMonth.minusMonths(1);
        java.time.OffsetDateTime nextWeek = now.plusWeeks(1);

        TurnAssigned scheduledFuture = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("SCHEDULED")
                .scheduledAt(nextWeek)
                .build();

        TurnAssigned completedThisMonth = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("COMPLETED")
                .scheduledAt(startOfMonth.plusDays(5))
                .build();

        TurnAssigned completedLastMonth = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient2)
                .status("COMPLETED")
                .scheduledAt(lastMonth)
                .build();

        TurnAssigned cancelled = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("CANCELED")
                .scheduledAt(now.minusDays(1))
                .build();

        List<TurnAssigned> allTurns = Arrays.asList(
                scheduledFuture, completedThisMonth, completedLastMonth, cancelled
        );

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(allTurns);
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Arrays.asList(patient1, patient2));
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(1, result.getUpcomingTurns());
        assertNotNull(result.getCompletedTurnsThisMonth());
        assertEquals(1, result.getCancelledTurns());
        assertEquals(2, result.getTotalPatients());
    }

    @Test
    void getDoctorMetrics_WithNoTurns_ReturnsZeroMetrics() {
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Collections.emptyList());
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.emptyList());
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(0, result.getUpcomingTurns());
        assertEquals(0, result.getCompletedTurnsThisMonth());
        assertEquals(0, result.getCancelledTurns());
        assertEquals(0, result.getTotalPatients());
    }

    @Test
    void getDoctorMetrics_OnlyScheduledTurnsInPast_CountsZeroUpcoming() {
        java.time.OffsetDateTime yesterday = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(1);

        TurnAssigned scheduledPast = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("SCHEDULED")
                .scheduledAt(yesterday)
                .build();

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Collections.singletonList(scheduledPast));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.singletonList(patient1));
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(0, result.getUpcomingTurns());
    }

    @Test
    void getDoctorMetrics_CompletedTurnsAtStartOfMonth_CountedCorrectly() {
        java.time.OffsetDateTime startOfMonth = java.time.YearMonth.now().atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();

        TurnAssigned completedAtStart = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("COMPLETED")
                .scheduledAt(startOfMonth.plusMinutes(1))
                .build();

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Collections.singletonList(completedAtStart));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.singletonList(patient1));
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(1, result.getCompletedTurnsThisMonth());
    }

    @Test
    void getDoctorMetrics_CompletedTurnAtEndOfLastMonth_NotCountedThisMonth() {
        java.time.OffsetDateTime startOfMonth = java.time.YearMonth.now().atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
        java.time.OffsetDateTime endOfLastMonth = startOfMonth.minusMinutes(1);

        TurnAssigned completedLastMonth = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("COMPLETED")
                .scheduledAt(endOfLastMonth)
                .build();

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Collections.singletonList(completedLastMonth));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.singletonList(patient1));
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(0, result.getCompletedTurnsThisMonth());
    }

    @Test
    void getDoctorMetrics_OnlyCancelledTurns_CountsCorrectly() {
        java.time.OffsetDateTime yesterday = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(1);
        java.time.OffsetDateTime lastWeek = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusWeeks(1);

        TurnAssigned cancelled1 = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("CANCELED")
                .scheduledAt(yesterday)
                .build();

        TurnAssigned cancelled2 = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient2)
                .status("CANCELED")
                .scheduledAt(lastWeek)
                .build();

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Arrays.asList(cancelled1, cancelled2));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Arrays.asList(patient1, patient2));
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(2, result.getCancelledTurns());
        assertEquals(0, result.getUpcomingTurns());
        assertEquals(0, result.getCompletedTurnsThisMonth());
    }

    @Test
    void getDoctorMetrics_WithRatingSubcategories_ReturnsCorrectly() {
        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Collections.emptyList());
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.emptyList());

        RatingRepository.SubcategoryCount subcategory1 = new RatingRepository.SubcategoryCount() {
            @Override
            public String getSubcategory() {
                return "Puntualidad";
            }

            @Override
            public Long getCount() {
                return 5L;
            }
        };

        RatingRepository.SubcategoryCount subcategory2 = new RatingRepository.SubcategoryCount() {
            @Override
            public String getSubcategory() {
                return "Profesionalismo";
            }

            @Override
            public Long getCount() {
                return 3L;
            }
        };

        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Arrays.asList(subcategory1, subcategory2));

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertNotNull(result.getRatingSubcategories());
        assertEquals(2, result.getRatingSubcategories().size());
        assertEquals("Puntualidad", result.getRatingSubcategories().get(0).getSubcategory());
        assertEquals(5L, result.getRatingSubcategories().get(0).getCount());
        assertEquals("Profesionalismo", result.getRatingSubcategories().get(1).getSubcategory());
        assertEquals(3L, result.getRatingSubcategories().get(1).getCount());
    }

    @Test
    void getDoctorMetrics_MultipleTurnsWithSamePatient_CountsPatientOnce() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);

        TurnAssigned turn1 = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("COMPLETED")
                .scheduledAt(now.minusDays(1))
                .build();

        TurnAssigned turn2 = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(doctor)
                .patient(patient1)
                .status("COMPLETED")
                .scheduledAt(now.minusDays(2))
                .build();

        when(userRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(turnAssignedRepository.findByDoctor_IdOrderByScheduledAtDesc(doctorId))
                .thenReturn(Arrays.asList(turn1, turn2));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId))
                .thenReturn(Collections.singletonList(patient1));
        when(ratingRepository.countSubcategoriesByRatedId(doctorId, "PATIENT"))
                .thenReturn(Collections.emptyList());

        com.medibook.api.dto.DoctorMetricsDTO result = doctorService.getDoctorMetrics(doctorId);

        assertNotNull(result);
        assertEquals(1, result.getTotalPatients());
    }
}