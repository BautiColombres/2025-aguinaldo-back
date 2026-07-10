package com.medibook.api.service;

import com.medibook.api.dto.DoctorDTO;
import com.medibook.api.dto.PatientDTO;
import com.medibook.api.entity.DoctorProfile;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.DoctorMapper;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
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

import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceComprehensiveTest {

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

    @InjectMocks
    private DoctorService doctorService;

    private User doctorUser1;
    private User doctorUser2;
    private User patientUser1;
    private User patientUser2;
    private DoctorProfile doctorProfile1;
    private DoctorProfile doctorProfile2;
    private DoctorDTO doctorDTO1;
    private DoctorDTO doctorDTO2;
    private UUID doctorId1;
    private UUID doctorId2;
    private UUID patientId1;
    private UUID patientId2;

    @BeforeEach
    void setUp() {
        doctorId1 = UUID.randomUUID();
        doctorId2 = UUID.randomUUID();
        patientId1 = UUID.randomUUID();
        patientId2 = UUID.randomUUID();

        doctorUser1 = createUser(doctorId1, "doctor1@test.com", 12345678L, "DOCTOR", "ACTIVE");
        doctorUser2 = createUser(doctorId2, "doctor2@test.com", 87654321L, "DOCTOR", "ACTIVE");
        
        patientUser1 = createUser(patientId1, "patient1@test.com", 11111111L, "PATIENT", "ACTIVE");
        patientUser2 = createUser(patientId2, "patient2@test.com", 22222222L, "PATIENT", "ACTIVE");

        doctorProfile1 = new DoctorProfile();
        doctorProfile1.setId(doctorId1);
        doctorProfile1.setUser(doctorUser1);
        doctorProfile1.setSpecialty("Cardiología");
        doctorProfile1.setMedicalLicense("MED123456");
        doctorProfile1.setSlotDurationMin(30);

        doctorProfile2 = new DoctorProfile();
        doctorProfile2.setId(doctorId2);
        doctorProfile2.setUser(doctorUser2);
        doctorProfile2.setSpecialty("Neurología");
        doctorProfile2.setMedicalLicense("MED789012");
        doctorProfile2.setSlotDurationMin(45);

        doctorUser1.setDoctorProfile(doctorProfile1);
        doctorUser2.setDoctorProfile(doctorProfile2);

        doctorDTO1 = DoctorDTO.builder()
                .id(doctorId1)
                .name("Doctor")
                .surname("One")
                .email("doctor1@test.com")
                .specialty("Cardiología")
                .medicalLicense("MED123456")
                .slotDurationMin(30)
                .build();

        doctorDTO2 = DoctorDTO.builder()
                .id(doctorId2)
                .name("Doctor")
                .surname("Two")
                .email("doctor2@test.com")
                .specialty("Neurología")
                .medicalLicense("MED789012")
                .slotDurationMin(45)
                .build();
        
        // Set up default mocks for medical history service (lenient because not all tests use them)
        lenient().when(medicalHistoryService.getPatientMedicalHistory(any(UUID.class)))
                .thenReturn(Collections.emptyList());
        lenient().when(medicalHistoryService.getLatestMedicalHistoryContents(anyList()))
                .thenReturn(Collections.emptyMap());
        lenient().when(ratingRepository.countSubcategoriesByRatedIds(anyList(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    private User createUser(UUID id, String email, Long dni, String role, String status) {
        User user = new User();
        user.setId(id);
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

    @Test
    void getAllDoctors_ActiveDoctorsExist_ReturnsListOfDoctors() {        List<User> doctors = Arrays.asList(doctorUser1, doctorUser2);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(doctors);
        when(doctorMapper.toDTO(doctorUser1)).thenReturn(doctorDTO1);
        when(doctorMapper.toDTO(doctorUser2)).thenReturn(doctorDTO2);        List<DoctorDTO> result = doctorService.getAllDoctors();        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(doctorDTO1, result.get(0));
        assertEquals(doctorDTO2, result.get(1));
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
        verify(doctorMapper).toDTO(doctorUser1);
        verify(doctorMapper).toDTO(doctorUser2);
    }

    @Test
    void getAllDoctors_NoDoctorsExist_ReturnsEmptyList() {        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(Collections.emptyList());        List<DoctorDTO> result = doctorService.getAllDoctors();        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
        verifyNoInteractions(doctorMapper);
    }

    @Test
    void getAllDoctors_OnlyInactiveDoctors_ReturnsEmptyList() {        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(Collections.emptyList());        List<DoctorDTO> result = doctorService.getAllDoctors();        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
        verifyNoInteractions(doctorMapper);
    }

    @Test
    void getDoctorsBySpecialty_ValidSpecialty_ReturnsFilteredDoctors() {        String specialty = "Cardiología";
        List<User> doctors = Arrays.asList(doctorUser1);
        when(userRepository.findDoctorsByStatusAndSpecialty("ACTIVE", specialty)).thenReturn(doctors);
        when(doctorMapper.toDTO(doctorUser1)).thenReturn(doctorDTO1);        List<DoctorDTO> result = doctorService.getDoctorsBySpecialty(specialty);        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(doctorDTO1, result.get(0));
        assertEquals("Cardiología", result.get(0).getSpecialty());
        
        verify(userRepository).findDoctorsByStatusAndSpecialty("ACTIVE", specialty);
        verify(doctorMapper).toDTO(doctorUser1);
    }

    @Test
    void getDoctorsBySpecialty_NonExistentSpecialty_ReturnsEmptyList() {        String specialty = "Dermatología";
        when(userRepository.findDoctorsByStatusAndSpecialty("ACTIVE", specialty)).thenReturn(Collections.emptyList());        List<DoctorDTO> result = doctorService.getDoctorsBySpecialty(specialty);        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findDoctorsByStatusAndSpecialty("ACTIVE", specialty);
        verifyNoInteractions(doctorMapper);
    }

    @Test
    void getDoctorsBySpecialty_CaseInsensitiveSpecialty_ReturnsFilteredDoctors() {        String specialty = "CARDIOLOGÍA";
        List<User> doctors = Arrays.asList(doctorUser1);
        when(userRepository.findDoctorsByStatusAndSpecialty("ACTIVE", specialty)).thenReturn(doctors);
        when(doctorMapper.toDTO(doctorUser1)).thenReturn(doctorDTO1);        List<DoctorDTO> result = doctorService.getDoctorsBySpecialty(specialty);        assertNotNull(result);
        assertEquals(1, result.size());
        
        verify(userRepository).findDoctorsByStatusAndSpecialty("ACTIVE", specialty);
        verify(doctorMapper).toDTO(doctorUser1);
    }

    @Test
    void getAllSpecialties_MultipleDoctors_ReturnsUniqueSpecialties() {        List<User> doctors = Arrays.asList(doctorUser1, doctorUser2);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(doctors);        List<String> result = doctorService.getAllSpecialties();        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("Cardiología"));
        assertTrue(result.contains("Neurología"));
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getAllSpecialties_DuplicateSpecialties_ReturnsUniqueSpecialties() {        User doctorUser3 = createUser(UUID.randomUUID(), "doctor3@test.com", 33333333L, "DOCTOR", "ACTIVE");
        DoctorProfile doctorProfile3 = new DoctorProfile();
        doctorProfile3.setSpecialty("Cardiología");
        doctorUser3.setDoctorProfile(doctorProfile3);
        
        List<User> doctors = Arrays.asList(doctorUser1, doctorUser2, doctorUser3);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(doctors);        List<String> result = doctorService.getAllSpecialties();        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("Cardiología"));
        assertTrue(result.contains("Neurología"));
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getAllSpecialties_NoDoctors_ReturnsEmptyList() {        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(Collections.emptyList());        List<String> result = doctorService.getAllSpecialties();        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getAllSpecialties_SpecialtiesAreSorted_ReturnsSortedList() {        User doctorUser3 = createUser(UUID.randomUUID(), "doctor3@test.com", 33333333L, "DOCTOR", "ACTIVE");
        DoctorProfile doctorProfile3 = new DoctorProfile();
        doctorProfile3.setSpecialty("Dermatología");
        doctorUser3.setDoctorProfile(doctorProfile3);
        
        List<User> doctors = Arrays.asList(doctorUser1, doctorUser2, doctorUser3);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(doctors);        List<String> result = doctorService.getAllSpecialties();        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Cardiología", result.get(0));
        assertEquals("Dermatología", result.get(1));
        assertEquals("Neurología", result.get(2));
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getPatientsByDoctor_ValidDoctor_ReturnsPatients() {
        when(userRepository.findById(doctorId1)).thenReturn(Optional.of(doctorUser1));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId1))
                .thenReturn(Arrays.asList(patientUser1, patientUser2));
        when(medicalHistoryService.getLatestMedicalHistoryContents(Arrays.asList(patientId1, patientId2))).thenReturn(Collections.emptyMap());
        when(ratingRepository.countSubcategoriesByRatedIds(Arrays.asList(patientId1, patientId2), "DOCTOR"))
                .thenReturn(Collections.emptyList());

        List<PatientDTO> result = doctorService.getPatientsByDoctor(doctorId1);

        assertNotNull(result);
        assertEquals(2, result.size());
        
        PatientDTO patient1 = result.get(0);
        assertEquals(patientId1, patient1.getId());
        assertEquals("Test", patient1.getName());
        assertEquals("User", patient1.getSurname());
        
        PatientDTO patient2 = result.get(1);
        assertEquals(patientId2, patient2.getId());
        assertEquals("Test", patient2.getName());
        assertEquals("User", patient2.getSurname());
        
        verify(userRepository).findById(doctorId1);
        verify(turnAssignedRepository).findDistinctPatientsByDoctorId(doctorId1);
        verify(medicalHistoryService).getPatientMedicalHistory(patientId1);
        verify(medicalHistoryService).getPatientMedicalHistory(patientId2);
        verify(medicalHistoryService).getLatestMedicalHistoryContents(Arrays.asList(patientId1, patientId2));
        verify(ratingRepository).countSubcategoriesByRatedIds(Arrays.asList(patientId1, patientId2), "DOCTOR");
    }

    @Test
    void getPatientsByDoctor_DoctorNotFound_ThrowsException() {        when(userRepository.findById(doctorId1)).thenReturn(Optional.empty());        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getPatientsByDoctor(doctorId1));
        
        assertEquals("Doctor not found", exception.getMessage());
        
        verify(userRepository).findById(doctorId1);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_UserIsNotDoctor_ThrowsException() {        User patientUserAsDoctor = createUser(doctorId1, "patient@test.com", 99999999L, "PATIENT", "ACTIVE");
        when(userRepository.findById(doctorId1)).thenReturn(Optional.of(patientUserAsDoctor));        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getPatientsByDoctor(doctorId1));
        
        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        
        verify(userRepository).findById(doctorId1);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_DoctorIsInactive_ThrowsException() {        User inactiveDoctor = createUser(doctorId1, "doctor@test.com", 12345678L, "DOCTOR", "INACTIVE");
        when(userRepository.findById(doctorId1)).thenReturn(Optional.of(inactiveDoctor));        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getPatientsByDoctor(doctorId1));
        
        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        
        verify(userRepository).findById(doctorId1);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_NoPatients_ReturnsEmptyList() {        when(userRepository.findById(doctorId1)).thenReturn(Optional.of(doctorUser1));
        when(turnAssignedRepository.findDistinctPatientsByDoctorId(doctorId1))
                .thenReturn(Collections.emptyList());        when(medicalHistoryService.getLatestMedicalHistoryContents(Collections.emptyList())).thenReturn(Collections.emptyMap());
        when(ratingRepository.countSubcategoriesByRatedIds(Collections.emptyList(), "DOCTOR")).thenReturn(Collections.emptyList());        List<PatientDTO> result = doctorService.getPatientsByDoctor(doctorId1);        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findById(doctorId1);
        verify(turnAssignedRepository).findDistinctPatientsByDoctorId(doctorId1);
        verify(medicalHistoryService).getLatestMedicalHistoryContents(Collections.emptyList());
        verify(ratingRepository).countSubcategoriesByRatedIds(Collections.emptyList(), "DOCTOR");
    }

    @Test
    void getAllDoctors_MapperThrowsException_PropagatesException() {        List<User> doctors = Arrays.asList(doctorUser1);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(doctors);
        when(doctorMapper.toDTO(doctorUser1)).thenThrow(new RuntimeException("Mapping error"));        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getAllDoctors());
        
        assertEquals("Mapping error", exception.getMessage());
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
        verify(doctorMapper).toDTO(doctorUser1);
    }

    @Test
    void getDoctorsBySpecialty_NullSpecialty_HandlesGracefully() {        when(userRepository.findDoctorsByStatusAndSpecialty("ACTIVE", null))
                .thenReturn(Collections.emptyList());        List<DoctorDTO> result = doctorService.getDoctorsBySpecialty(null);        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findDoctorsByStatusAndSpecialty("ACTIVE", null);
        verifyNoInteractions(doctorMapper);
    }

    @Test
    void getDoctorsBySpecialty_EmptySpecialty_HandlesGracefully() {        String emptySpecialty = "";
        when(userRepository.findDoctorsByStatusAndSpecialty("ACTIVE", emptySpecialty))
                .thenReturn(Collections.emptyList());        List<DoctorDTO> result = doctorService.getDoctorsBySpecialty(emptySpecialty);        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userRepository).findDoctorsByStatusAndSpecialty("ACTIVE", emptySpecialty);
        verifyNoInteractions(doctorMapper);
    }

    @Test
    void getAllSpecialties_DoctorProfileIsNull_SkipsGracefully() {        User doctorWithoutProfile = createUser(UUID.randomUUID(), "doctor@test.com", 99999999L, "DOCTOR", "ACTIVE");
        doctorWithoutProfile.setDoctorProfile(null);
        
        List<User> doctors = Arrays.asList(doctorUser1, doctorWithoutProfile);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(doctors);

        // BBUG-M1: an ACTIVE doctor without a profile must be skipped, not NPE.
        List<String> result = assertDoesNotThrow(() -> doctorService.getAllSpecialties());

        assertEquals(List.of("Cardiología"), result);
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getAllDoctors_LargeNumberOfDoctors_HandlesEfficiently() {        List<User> largeDoctorList = Collections.nCopies(1000, doctorUser1);
        DoctorDTO largeDoctorDTOList = doctorDTO1;
        
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(largeDoctorList);
        when(doctorMapper.toDTO(any(User.class))).thenReturn(largeDoctorDTOList);        long startTime = System.currentTimeMillis();
        List<DoctorDTO> result = doctorService.getAllDoctors();
        long endTime = System.currentTimeMillis();        assertNotNull(result);
        assertEquals(1000, result.size());
        assertTrue(endTime - startTime < 1000, "Should handle large doctor list efficiently");
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
        verify(doctorMapper, times(1000)).toDTO(any(User.class));
    }

    @Test
    void getAllSpecialties_LargeNumberOfDoctors_HandlesEfficiently() {        List<User> largeDoctorList = Collections.nCopies(1000, doctorUser1);
        when(userRepository.findDoctorsByStatus("ACTIVE")).thenReturn(largeDoctorList);        long startTime = System.currentTimeMillis();
        List<String> result = doctorService.getAllSpecialties();
        long endTime = System.currentTimeMillis();        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(endTime - startTime < 1000, "Should handle large specialty list efficiently");
        
        verify(userRepository).findDoctorsByStatus("ACTIVE");
    }

    @Test
    void getPatientsByDoctor_UnauthorizedAccess_ValidatesDoctor() {        UUID unauthorizedDoctorId = UUID.randomUUID();
        when(userRepository.findById(unauthorizedDoctorId)).thenReturn(Optional.empty());        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getPatientsByDoctor(unauthorizedDoctorId));
        
        assertEquals("Doctor not found", exception.getMessage());
        
        verify(userRepository).findById(unauthorizedDoctorId);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_ValidatesUserRole_PreventsUnauthorizedAccess() {        User adminUser = createUser(doctorId1, "admin@test.com", 99999999L, "ADMIN", "ACTIVE");
        when(userRepository.findById(doctorId1)).thenReturn(Optional.of(adminUser));        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getPatientsByDoctor(doctorId1));
        
        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        
        verify(userRepository).findById(doctorId1);
        verifyNoInteractions(turnAssignedRepository);
    }

    @Test
    void getPatientsByDoctor_ValidatesUserStatus_PreventsInactiveAccess() {        User pendingDoctor = createUser(doctorId1, "doctor@test.com", 12345678L, "DOCTOR", "PENDING");
        when(userRepository.findById(doctorId1)).thenReturn(Optional.of(pendingDoctor));        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> doctorService.getPatientsByDoctor(doctorId1));
        
        assertEquals("Invalid doctor or doctor is not active", exception.getMessage());
        
        verify(userRepository).findById(doctorId1);
        verifyNoInteractions(turnAssignedRepository);
    }
}