
package com.medibook.api.service;

import com.medibook.api.dto.Turn.TurnCreateRequestDTO;
import com.medibook.api.dto.Turn.TurnResponseDTO;
import com.medibook.api.dto.email.EmailResponseDto;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.TurnAssignedMapper;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnAssignedServiceTest {
    @Mock
    private TurnAssignedRepository turnRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private TurnAssignedMapper mapper;

    @Mock
    private com.medibook.api.repository.RatingRepository ratingRepo;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.medibook.api.service.EmailService emailService;

    @Mock
    private TurnFileService turnFileService;

    @Mock
    private com.medibook.api.service.BadgeEvaluationTriggerService badgeEvaluationTrigger;

    @Mock
    private com.medibook.api.service.MedicalCheckApiService medicalCheckApiService;

    @InjectMocks
    private TurnAssignedService turnAssignedService;

    @Mock
    private com.medibook.api.repository.TurnModifyRequestRepository turnModifyRequestRepository;

    private UUID doctorId;
    private UUID patientId;
    private UUID turnId;
    private User doctor;
    private User patient;
    private TurnCreateRequestDTO createRequest;
    private TurnAssigned turnEntity;
    private TurnResponseDTO turnResponse;
    private OffsetDateTime scheduledAt;
    private TurnAssigned turn;

    @BeforeEach
    void setUp() {
        EmailResponseDto successResponse = EmailResponseDto.builder()
                .success(true)
                .messageId("test-message-id")
                .message("Email sent successfully")
                .build();
                
        when(emailService.sendAppointmentConfirmationToPatientAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));
        when(emailService.sendAppointmentConfirmationToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));
        when(emailService.sendAppointmentCancellationToPatientAsync(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));
        when(emailService.sendAppointmentCancellationToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(successResponse));

        doctorId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        turnId = UUID.randomUUID();
        scheduledAt = OffsetDateTime.now().plusDays(1);

        doctor = new User();
        doctor.setId(doctorId);
        doctor.setName("Dr. Hugo");
        doctor.setSurname("Martinez");
        doctor.setRole("DOCTOR");
        doctor.setStatus("ACTIVE");

        patient = new User();
        patient.setId(patientId);
        patient.setName("Juan");
        patient.setSurname("Perez");
        patient.setRole("PATIENT");
        patient.setStatus("ACTIVE");

        createRequest = new TurnCreateRequestDTO();
        createRequest.setDoctorId(doctorId);
        createRequest.setPatientId(patientId);
        createRequest.setScheduledAt(scheduledAt);

        turnEntity = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledAt)
                .status("SCHEDULED")
                .build();

        turnResponse = TurnResponseDTO.builder()
                .id(turnId)
                .doctorId(doctorId)
                .doctorName("Dr. Hugo Martinez")
                .patientId(patientId)
                .patientName("Juan Perez")
                .scheduledAt(scheduledAt)
                .status("SCHEDULED")
                .build();

        turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledAt)
                .status("SCHEDULED")
                .build();

        // In production the @Lazy self-reference is a transactional proxy; in unit
        // tests we point it at the real instance so the DB portion runs for real
        // (with mocked repos) while the external call stays outside that method.
        org.springframework.test.util.ReflectionTestUtils.setField(turnAssignedService, "self", turnAssignedService);

        patient.setEmail("patient@example.com");
        doctor.setEmail("doctor@example.com");
    }

    @Test
    void createTurn_Success() {
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(turnRepo.existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(doctorId, scheduledAt)).thenReturn(false);
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(turnEntity);
        when(mapper.toDTO(turnEntity)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.createTurn(createRequest);

        assertNotNull(result);
        assertEquals(turnId, result.getId());
        assertEquals(doctorId, result.getDoctorId());
        assertEquals(patientId, result.getPatientId());
        assertEquals("SCHEDULED", result.getStatus());
        assertEquals(scheduledAt, result.getScheduledAt());

        verify(userRepo).findById(doctorId);
        verify(userRepo).findById(patientId);
        verify(turnRepo).existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(doctorId, scheduledAt);
        verify(turnRepo).save(any(TurnAssigned.class));
        verify(mapper).toDTO(turnEntity);
        verify(notificationService).createTurnReservedNotification(
                eq(doctor.getId()), 
                eq(turnEntity.getId()), 
                eq(patient.getName() + " " + patient.getSurname()),
                anyString(), 
                anyString()
        );
    }

    @Test
    void createTurn_DoctorNotFound_ThrowsException() {
        when(userRepo.findById(doctorId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("Doctor not found", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo, never()).findById(patientId);
        verify(turnRepo, never()).existsByDoctor_IdAndScheduledAt(any(), any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void createTurn_PatientNotFound_ThrowsException() {
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(userRepo.findById(patientId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("Patient not found", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo).findById(patientId);
        verify(turnRepo, never()).existsByDoctor_IdAndScheduledAt(any(), any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void createTurn_SlotAlreadyTaken_ThrowsException() {
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(turnRepo.existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(doctorId, scheduledAt)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("Time slot is already taken", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo).findById(patientId);
        verify(turnRepo).existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(doctorId, scheduledAt);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void createTurn_DoctorNotActive_ThrowsException() {
        doctor.setStatus("PENDING");
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("Doctor is not active", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo, never()).findById(patientId);
        verify(turnRepo, never()).existsByDoctor_IdAndScheduledAt(any(), any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void createTurn_DoctorInvalidRole_ThrowsException() {
        doctor.setRole("PATIENT");
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("User is not a doctor", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo, never()).findById(patientId);
        verify(turnRepo, never()).existsByDoctor_IdAndScheduledAt(any(), any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void createTurn_PatientNotActive_ThrowsException() {
        patient.setStatus("DISABLED");
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("Patient is not active", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo).findById(patientId);
        verify(turnRepo, never()).existsByDoctor_IdAndScheduledAt(any(), any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void createTurn_PatientInvalidRole_ThrowsException() {
        patient.setRole("ADMIN");
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.createTurn(createRequest);
        });

        assertEquals("User is not a patient", exception.getMessage());
        verify(userRepo).findById(doctorId);
        verify(userRepo).findById(patientId);
        verify(turnRepo, never()).existsByDoctor_IdAndScheduledAt(any(), any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void reserveTurn_Success() {
        TurnAssigned availableTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(null)
                .scheduledAt(scheduledAt)
                .status("AVAILABLE")
                .build();

        TurnAssigned reservedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledAt)
                .status("RESERVED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(availableTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(reservedTurn);

        TurnAssigned result = turnAssignedService.reserveTurn(turnId, patientId);

        assertNotNull(result);
        assertEquals(turnId, result.getId());
        assertEquals(patient, result.getPatient());
        assertEquals("RESERVED", result.getStatus());

        verify(turnRepo).findById(turnId);
        verify(userRepo).findById(patientId);
        verify(turnRepo).save(any(TurnAssigned.class));
    }

    @Test
    void reserveTurn_TurnNotFound_ThrowsException() {
        when(turnRepo.findById(turnId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.reserveTurn(turnId, patientId);
        });

        assertEquals("Turn not found", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(userRepo, never()).findById(any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void reserveTurn_TurnNotAvailable_ThrowsException() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledAt)
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.reserveTurn(turnId, patientId);
        });

        assertEquals("Turn is not available", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(userRepo, never()).findById(any());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void reserveTurn_PatientNotFound_ThrowsException() {
        TurnAssigned availableTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(null)
                .scheduledAt(scheduledAt)
                .status("AVAILABLE")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(availableTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.reserveTurn(turnId, patientId);
        });

        assertEquals("Patient not found", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(userRepo).findById(patientId);
        verify(turnRepo, never()).save(any());
    }
    
    @Test
    void cancelTurn_Success() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1)) 
                .status("SCHEDULED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);
        when(turnFileService.fileExistsForTurn(turnId)).thenReturn(false);

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
        verify(mapper).toDTO(canceledTurn);
        verify(turnFileService).fileExistsForTurn(turnId);
        assertEquals("CANCELED", scheduledTurn.getStatus());
    }

    @Test
    void cancelTurn_WithFile_DeletesFileSuccessfully() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);
        when(turnFileService.fileExistsForTurn(turnId)).thenReturn(true);
        when(turnFileService.deleteTurnFile(turnId)).thenReturn(reactor.core.publisher.Mono.empty());

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
        verify(mapper).toDTO(canceledTurn);
        verify(turnFileService).fileExistsForTurn(turnId);
        verify(turnFileService).deleteTurnFile(turnId);
        assertEquals("CANCELED", scheduledTurn.getStatus());
    }

    @Test
    void cancelTurn_TurnNotFound_ThrowsException() {
        when(turnRepo.findById(turnId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");
        });

        assertEquals("Turn not found", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_NotPatientTurn_ThrowsException() {
        UUID differentPatientId = UUID.randomUUID();
        User differentPatient = new User();
        differentPatient.setId(differentPatientId);
        differentPatient.setEmail("other@example.com");
        differentPatient.setRole("PATIENT");

        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(differentPatient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");
        });

        assertEquals("You can only cancel your own turns", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_TurnAlreadyCompleted_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");
        });

        assertEquals("Turn cannot be canceled. Current status: COMPLETED", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_PastTurn_ThrowsException() {
        TurnAssigned pastTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1)) 
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(pastTurn));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");
        });

        assertEquals("Cannot cancel past turns", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_ReservedTurn_Success() {
        TurnAssigned reservedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("RESERVED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(reservedTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(reservedTurn);
        verify(mapper).toDTO(canceledTurn);
        assertEquals("CANCELED", reservedTurn.getStatus());
    }
    
    @Test
    void cancelTurn_DoctorCancelOwnTurn_Success() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, doctorId, "DOCTOR");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
        verify(mapper).toDTO(canceledTurn);
        assertEquals("CANCELED", scheduledTurn.getStatus());
    }

    @Test
    void cancelTurn_DoctorCannotCancelOtherDoctorTurn_ThrowsException() {
        UUID differentDoctorId = UUID.randomUUID();
        User differentDoctor = new User();
        differentDoctor.setId(differentDoctorId);
        differentDoctor.setEmail("other@doctor.com");
        differentDoctor.setRole("DOCTOR");

        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(differentDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, doctorId, "DOCTOR");
        });

        assertEquals("You can only cancel your own turns", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_InvalidRole_ThrowsException() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, patientId, "ADMIN");
        });

        assertEquals("Invalid user role for cancellation", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void getTurnsByDoctor_Success() {
        List<TurnAssigned> turns = Arrays.asList(turnEntity);
        List<TurnResponseDTO> expectedResponse = Arrays.asList(turnResponse);

        when(turnRepo.findByDoctor_IdOrderByScheduledAtDesc(doctorId)).thenReturn(turns);
        when(mapper.toDTO(turnEntity)).thenReturn(turnResponse);

        List<TurnResponseDTO> result = turnAssignedService.getTurnsByDoctor(doctorId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedResponse.get(0).getId(), result.get(0).getId());
        verify(turnRepo).findByDoctor_IdOrderByScheduledAtDesc(doctorId);
        verify(mapper).toDTO(turnEntity);
    }

    @Test
    void getTurnsByPatient_Success() {
        List<TurnAssigned> turns = Arrays.asList(turnEntity);
        List<TurnResponseDTO> expectedResponse = Arrays.asList(turnResponse);

        when(turnRepo.findByPatient_IdOrderByScheduledAtDesc(patientId)).thenReturn(turns);
        when(mapper.toDTO(turnEntity)).thenReturn(turnResponse);

        List<TurnResponseDTO> result = turnAssignedService.getTurnsByPatient(patientId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedResponse.get(0).getId(), result.get(0).getId());
        verify(turnRepo).findByPatient_IdOrderByScheduledAtDesc(patientId);
        verify(mapper).toDTO(turnEntity);
    }

    @Test
    void getTurnsByDoctorAndStatus_Success() {
        String status = "SCHEDULED";
        List<TurnAssigned> turns = Arrays.asList(turnEntity);
        List<TurnResponseDTO> expectedResponse = Arrays.asList(turnResponse);

        when(turnRepo.findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorId, status)).thenReturn(turns);
        when(mapper.toDTO(turnEntity)).thenReturn(turnResponse);

        List<TurnResponseDTO> result = turnAssignedService.getTurnsByDoctorAndStatus(doctorId, status);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedResponse.get(0).getId(), result.get(0).getId());
        verify(turnRepo).findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorId, status);
        verify(mapper).toDTO(turnEntity);
    }

    @Test
    void getTurnsByPatientAndStatus_Success() {
        String status = "SCHEDULED";
        List<TurnAssigned> turns = Arrays.asList(turnEntity);
        List<TurnResponseDTO> expectedResponse = Arrays.asList(turnResponse);

        when(turnRepo.findByPatient_IdAndStatusOrderByScheduledAtDesc(patientId, status)).thenReturn(turns);
        when(mapper.toDTO(turnEntity)).thenReturn(turnResponse);

        List<TurnResponseDTO> result = turnAssignedService.getTurnsByPatientAndStatus(patientId, status);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedResponse.get(0).getId(), result.get(0).getId());
        verify(turnRepo).findByPatient_IdAndStatusOrderByScheduledAtDesc(patientId, status);
        verify(mapper).toDTO(turnEntity);
    }

    @Test
    void cancelTurn_PatientRoleWithNullPatient_ThrowsException() {
        TurnAssigned turnWithNullPatient = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(null)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turnWithNullPatient));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");
        });

        assertEquals("You can only cancel your own turns", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_DoctorRoleWithNullDoctor_ThrowsException() {
        TurnAssigned turnWithNullDoctor = TurnAssigned.builder()
                .id(turnId)
                .doctor(null)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turnWithNullDoctor));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.cancelTurn(turnId, doctorId, "DOCTOR");
        });

        assertEquals("You can only cancel your own turns", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void cancelTurn_WithPendingModifyRequest_DeletesRequest() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        com.medibook.api.entity.TurnModifyRequest pendingRequest = new com.medibook.api.entity.TurnModifyRequest();
        pendingRequest.setId(UUID.randomUUID());
        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);
        when(turnModifyRequestRepository.findByTurnAssigned_IdAndStatus(turnId, "PENDING"))
            .thenReturn(Optional.of(pendingRequest));

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
        verify(mapper).toDTO(canceledTurn);
        verify(turnModifyRequestRepository).findByTurnAssigned_IdAndStatus(turnId, "PENDING");
        verify(turnModifyRequestRepository).deleteByTurnAssigned_IdAndStatus(turnId, "PENDING");
        assertEquals("CANCELED", scheduledTurn.getStatus());
    }
    
    @Test
    void cancelTurn_NoPendingModifyRequest_DoesNotDeleteRequest() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);
        when(turnModifyRequestRepository.findByTurnAssigned_IdAndStatus(turnId, "PENDING"))
                .thenReturn(Optional.empty());

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
        verify(mapper).toDTO(canceledTurn);
        verify(turnModifyRequestRepository).findByTurnAssigned_IdAndStatus(turnId, "PENDING");
        verify(turnModifyRequestRepository, never()).deleteByTurnAssigned_IdAndStatus(any(UUID.class), eq("PENDING"));
        assertEquals("CANCELED", scheduledTurn.getStatus());
    }

    @Test
    void addRating_DoctorRatesPatient_Success() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1)) // Turno pasado
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(false);

        com.medibook.api.entity.Rating saved = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(doctor)
                .rated(patient)
                .score(5)
                .subcategory("Respetuoso")
                .createdAt(OffsetDateTime.now())
                .build();

        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(saved);

    com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, doctorId, 5, java.util.List.of("Respetuoso"));

        assertNotNull(result);
        assertEquals("Respetuoso", result.getSubcategory());
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_DoctorRatesPatient_InvalidSubcategory_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1)) // Turno pasado
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            turnAssignedService.addRating(turnId, doctorId, 4, java.util.List.of("Bad Subcategory"));
        });

        assertTrue(ex.getMessage().contains("Invalid subcategory"));
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void completeTurn_ScheduledStatus_Success() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("COMPLETED")
                .build();

        TurnResponseDTO turnResponse = TurnResponseDTO.builder()
                .id(turnId)
                .doctorId(doctorId)
                .patientId(patientId)
                .scheduledAt(completedTurn.getScheduledAt())
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(completedTurn);
        when(mapper.toDTO(completedTurn)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.completeTurn(turnId, doctorId);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("COMPLETED", scheduledTurn.getStatus());
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
    }

    @Test
    void completeTurn_ReservedStatus_Success() {
        TurnAssigned reservedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("RESERVED")
                .build();

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("COMPLETED")
                .build();

        TurnResponseDTO turnResponse = TurnResponseDTO.builder()
                .id(turnId)
                .doctorId(doctorId)
                .patientId(patientId)
                .scheduledAt(completedTurn.getScheduledAt())
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(reservedTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(completedTurn);
        when(mapper.toDTO(completedTurn)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.completeTurn(turnId, doctorId);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("COMPLETED", reservedTurn.getStatus());
    }

    @Test
    void completeTurn_TurnNotFound_ThrowsException() {
        when(turnRepo.findById(turnId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.completeTurn(turnId, doctorId));

        assertEquals("Turn not found", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void completeTurn_NotDoctorTurn_ThrowsException() {
        UUID otherDoctorId = UUID.randomUUID();
        User otherDoctor = new User();
        otherDoctor.setId(otherDoctorId);
        otherDoctor.setEmail("other@test.com");
        otherDoctor.setRole("DOCTOR");
        otherDoctor.setStatus("ACTIVE");

        TurnAssigned turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(otherDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.completeTurn(turnId, doctorId));

        assertEquals("You can only complete your own turns", exception.getMessage());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void completeTurn_InvalidStatus_ThrowsException() {
        TurnAssigned turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.completeTurn(turnId, doctorId));

        assertTrue(exception.getMessage().contains("Turn cannot be completed"));
        verify(turnRepo, never()).save(any());
    }

    @Test
    void completeTurn_HealthCertificate_CallsMedicalCheckAfterPersisting() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .motive("HEALTH CERTIFICATE")
                .build();

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledTurn.getScheduledAt())
                .status("COMPLETED")
                .motive("HEALTH CERTIFICATE")
                .build();

        TurnResponseDTO response = TurnResponseDTO.builder()
                .id(turnId)
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(completedTurn);
        when(mapper.toDTO(completedTurn)).thenReturn(response);

        TurnResponseDTO result = turnAssignedService.completeTurn(turnId, doctorId);

        assertNotNull(result);
        // The external call must happen AFTER the DB persistence (turn saved),
        // never before, so the transaction is not held open across the network call.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(turnRepo, medicalCheckApiService);
        inOrder.verify(turnRepo).save(scheduledTurn);
        inOrder.verify(medicalCheckApiService).processMedicalCheckCompletion("patient@example.com");
    }

    @Test
    void completeTurn_NonHealthCertificate_DoesNotCallMedicalCheck() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .motive("General consultation")
                .build();

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledTurn.getScheduledAt())
                .status("COMPLETED")
                .motive("General consultation")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(completedTurn);
        when(mapper.toDTO(completedTurn)).thenReturn(TurnResponseDTO.builder().id(turnId).status("COMPLETED").build());

        turnAssignedService.completeTurn(turnId, doctorId);

        verify(medicalCheckApiService, never()).processMedicalCheckCompletion(any());
    }

    @Test
    void completeTurnPersist_HealthCertificate_DoesNotCallMedicalCheckInsideTransaction() {
        // The transactional DB method must NOT trigger the external medical-check call;
        // that call belongs to the non-transactional orchestrator (completeTurn).
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .motive("HEALTH CERTIFICATE")
                .build();

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(scheduledTurn.getScheduledAt())
                .status("COMPLETED")
                .motive("HEALTH CERTIFICATE")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(completedTurn);
        when(mapper.toDTO(completedTurn)).thenReturn(TurnResponseDTO.builder().id(turnId).status("COMPLETED").build());

        TurnAssignedService.CompletionResult persisted =
                turnAssignedService.completeTurnPersist(turnId, doctorId);

        assertEquals("patient@example.com", persisted.healthCertificateEmail());
        verify(medicalCheckApiService, never()).processMedicalCheckCompletion(any());
    }

    @Test
    void markTurnAsNoShow_Success() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned noShowTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("NO_SHOW")
                .build();

        TurnResponseDTO turnResponse = TurnResponseDTO.builder()
                .id(turnId)
                .doctorId(doctorId)
                .patientId(patientId)
                .scheduledAt(noShowTurn.getScheduledAt())
                .status("NO_SHOW")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(noShowTurn);
        when(mapper.toDTO(noShowTurn)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.markTurnAsNoShow(turnId, doctorId);

        assertNotNull(result);
        assertEquals("NO_SHOW", result.getStatus());
        assertEquals("NO_SHOW", scheduledTurn.getStatus());
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
    }

    @Test
    void markTurnAsNoShow_TurnNotFound_ThrowsException() {
        when(turnRepo.findById(turnId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.markTurnAsNoShow(turnId, doctorId));

        assertEquals("Turn not found", exception.getMessage());
        verify(turnRepo).findById(turnId);
        verify(turnRepo, never()).save(any());
    }

    @Test
    void markTurnAsNoShow_NotDoctorTurn_ThrowsException() {
        UUID otherDoctorId = UUID.randomUUID();
        User otherDoctor = new User();
        otherDoctor.setId(otherDoctorId);
        otherDoctor.setEmail("other@test.com");
        otherDoctor.setRole("DOCTOR");
        otherDoctor.setStatus("ACTIVE");

        TurnAssigned turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(otherDoctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.markTurnAsNoShow(turnId, doctorId));

        assertEquals("You can only mark no-show for your own turns", exception.getMessage());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void markTurnAsNoShow_InvalidStatus_ThrowsException() {
        TurnAssigned turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.markTurnAsNoShow(turnId, doctorId));

        assertTrue(exception.getMessage().contains("Turn cannot be marked as no-show"));
        verify(turnRepo, never()).save(any());
    }

    @Test
    void addRating_TurnNotFound_ThrowsException() {
        when(turnRepo.findById(turnId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 4, java.util.List.of()));

        assertEquals("Turn not found", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_CanceledTurn_ThrowsException() {
        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(canceledTurn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 4, java.util.List.of()));

        assertEquals("Cannot rate canceled turns", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_RaterNotFound_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 4, java.util.List.of()));

        assertEquals("Rater not found", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_RaterNotInTurn_ThrowsException() {
        UUID randomUserId = UUID.randomUUID();
        User randomUser = new User();
        randomUser.setId(randomUserId);
        randomUser.setEmail("random@test.com");
        randomUser.setRole("PATIENT");
        randomUser.setStatus("ACTIVE");

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(randomUserId)).thenReturn(Optional.of(randomUser));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, randomUserId, 4, java.util.List.of()));

        assertEquals("You can only rate your own turns", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_AlreadyRated_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 4, java.util.List.of()));

        assertEquals("You have already rated this turn", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_InvalidScore_ThrowsException() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 6, java.util.List.of()));

        assertEquals("Score must be between 1 and 5", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_NullScore_ThrowsException() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, null, java.util.List.of()));

        assertEquals("Score must be between 1 and 5", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_PatientRatesDoctor_Success() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(5)
                .subcategory(null)
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 5, java.util.List.of());

        assertNotNull(result);
        assertEquals(5, result.getScore());
        assertNull(result.getSubcategory());
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_PatientWithValidSubcategories_Success() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

    com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(5)
        .subcategory("Excelente atención, Explica claramente")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

    List<String> subcategories = Arrays.asList("Excelente atención", "Explica claramente");
        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 5, subcategories);

        assertNotNull(result);
        assertEquals(5, result.getScore());
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_PatientWithInvalidSubcategory_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);

        List<String> invalidSubcategories = Arrays.asList("InvalidCategory");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, patientId, 5, invalidSubcategories));

        assertTrue(exception.getMessage().contains("Invalid subcategory"));
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_MoreThan3Subcategories_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(false);

        List<String> tooManySubcategories = Arrays.asList("Llega puntual", "Respetuoso", "Buena higiene", "Colabora en consulta");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 4, tooManySubcategories));

        assertEquals("You can select up to 3 subcategories", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_NullSubcategoryInList_FiltersOut() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(doctor)
                .rated(patient)
                .score(4)
                .subcategory("Llega puntual")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        List<String> subcategoriesWithNull = Arrays.asList("Llega puntual", null, "");

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, doctorId, 4, subcategoriesWithNull);

        assertNotNull(result);
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_UnknownRole_ThrowsException() {
        User unknownRoleUser = new User();
        unknownRoleUser.setId(UUID.randomUUID());
        unknownRoleUser.setEmail("unknown@test.com");
        unknownRoleUser.setRole("ADMIN");
        unknownRoleUser.setStatus("ACTIVE");

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(unknownRoleUser.getId())).thenReturn(Optional.of(unknownRoleUser));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, unknownRoleUser.getId())).thenReturn(false);

        List<String> subcategories = Arrays.asList("Puntual");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, unknownRoleUser.getId(), 5, subcategories));

        assertEquals("Only patients and doctors can rate", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_DuplicateSubcategories_RemovesDuplicates() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(doctor)
                .rated(patient)
                .score(4)
                .subcategory("Llega puntual")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        List<String> duplicateSubcategories = Arrays.asList("Llega puntual", "Llega puntual", "Llega puntual");

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, doctorId, 4, duplicateSubcategories);

        assertNotNull(result);
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_NullRoleForRater_HandlesGracefully() {
        User noRoleUser = new User();
        noRoleUser.setId(UUID.randomUUID());
        noRoleUser.setEmail("norole@test.com");
        noRoleUser.setRole(null);
        noRoleUser.setStatus("ACTIVE");

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(noRoleUser) // This patient has no role
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(noRoleUser.getId())).thenReturn(Optional.of(noRoleUser));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, noRoleUser.getId())).thenReturn(false);

        List<String> subcategories = Arrays.asList("Puntual");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, noRoleUser.getId(), 5, subcategories));

        assertTrue(exception.getMessage().contains("Invalid subcategory") || exception.getMessage().contains("Only patients and doctors can rate"));
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void completeTurn_NullDoctor_ThrowsException() {
        TurnAssigned turnWithNullDoctor = TurnAssigned.builder()
                .id(turnId)
                .doctor(null)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turnWithNullDoctor));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.completeTurn(turnId, doctorId));

        assertEquals("You can only complete your own turns", exception.getMessage());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void completeTurn_NullPatient_NoBadgeEvaluation() {
        TurnAssigned turnWithNullPatient = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(null)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(null)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("COMPLETED")
                .build();

        TurnResponseDTO turnResponse = TurnResponseDTO.builder()
                .id(turnId)
                .doctorId(doctorId)
                .patientId(null)
                .scheduledAt(completedTurn.getScheduledAt())
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turnWithNullPatient));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(completedTurn);
        when(mapper.toDTO(completedTurn)).thenReturn(turnResponse);

        TurnResponseDTO result = turnAssignedService.completeTurn(turnId, doctorId);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        verify(badgeEvaluationTrigger, never()).evaluateAfterTurnCompletion(any(UUID.class), any(UUID.class));
    }

    @Test
    void markTurnAsNoShow_NullDoctor_ThrowsException() {
        TurnAssigned turnWithNullDoctor = TurnAssigned.builder()
                .id(turnId)
                .doctor(null)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(turnWithNullDoctor));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.markTurnAsNoShow(turnId, doctorId));

        assertEquals("You can only mark no-show for your own turns", exception.getMessage());
        verify(turnRepo, never()).save(any());
    }

    @Test
    void addRating_DoctorRatingNullPatient_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(null)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, doctorId, 4, java.util.List.of()));

        assertEquals("No patient to rate for this turn", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_PatientRatingNullDoctor_ThrowsException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(null)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, patientId, 4, java.util.List.of()));

        assertEquals("No doctor to rate for this turn", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_FutureTurn_ThrowsException() {
        TurnAssigned futureTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(2))
                .status("SCHEDULED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(futureTurn));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> turnAssignedService.addRating(turnId, patientId, 4, java.util.List.of()));

        assertEquals("Can only rate turns that have already occurred", exception.getMessage());
        verify(ratingRepo, never()).save(any());
    }

    @Test
    void addRating_EmptySubcategoriesList_Success() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(4)
                .subcategory(null)
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 4, java.util.Collections.emptyList());

        assertNotNull(result);
        assertEquals(4, result.getScore());
        assertNull(result.getSubcategory());
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_NullSubcategoriesList_Success() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(4)
                .subcategory(null)
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 4, null);

        assertNotNull(result);
        assertEquals(4, result.getScore());
        assertNull(result.getSubcategory());
        verify(ratingRepo).save(any(com.medibook.api.entity.Rating.class));
    }

    @Test
    void addRating_CommunicationScoreExtraction_CommunicationKeywords() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(4)
                .subcategory("explica claramente, escucha atentamente")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 4,
                Arrays.asList("Explica claramente", "Escucha al paciente"));

        assertNotNull(result);
        verify(badgeEvaluationTrigger).evaluateAfterRating(eq(doctorId), eq(4), isNull(), isNull());
    }

    @Test
    void addRating_EmpathyScoreExtraction_EmpathyKeywords() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(5)
                .subcategory("empatía, confianza")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 5,
                Arrays.asList("Demuestra empatía", "Genera confianza"));

        assertNotNull(result);
        verify(badgeEvaluationTrigger).evaluateAfterRating(eq(doctorId), isNull(), eq(5), isNull());
    }

    @Test
    void addRating_PunctualityScoreExtraction_PunctualityKeywords() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(patient)
                .rated(doctor)
                .score(4)
                .subcategory("respeta horarios, tiempo de espera")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, patientId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, patientId, 4,
                Arrays.asList("Respeta horarios", "Tiempo de espera aceptable"));

        assertNotNull(result);
        verify(badgeEvaluationTrigger).evaluateAfterRating(eq(doctorId), isNull(), isNull(), eq(4));
    }

    @Test
    void addRating_DoctorRatingPatient_ExtractsScoresCorrectly() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().minusDays(1))
                .status("COMPLETED")
                .build();

        com.medibook.api.entity.Rating savedRating = com.medibook.api.entity.Rating.builder()
                .id(UUID.randomUUID())
                .turnAssigned(completedTurn)
                .rater(doctor)
                .rated(patient)
                .score(4)
                .subcategory("Llega puntual")
                .createdAt(OffsetDateTime.now())
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(completedTurn));
        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, doctorId)).thenReturn(false);
        when(ratingRepo.save(any(com.medibook.api.entity.Rating.class))).thenReturn(savedRating);

        com.medibook.api.entity.Rating result = turnAssignedService.addRating(turnId, doctorId, 4,
                Arrays.asList("Llega puntual"));

        assertNotNull(result);
        verify(badgeEvaluationTrigger, never()).evaluateAfterRating(any(), any(), any(), any());
    }

    @Test
    void createTurn_EmailFailure_ContinuesExecution() {
        TurnCreateRequestDTO dto = createTurnRequestDTO();
        TurnAssigned savedTurn = TurnAssigned.builder()
                .id(UUID.randomUUID())
                .doctor(turn.getDoctor())
                .patient(turn.getPatient())
                .scheduledAt(turn.getScheduledAt())
                .status(turn.getStatus())
                .build();

        when(userRepo.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(userRepo.findById(patientId)).thenReturn(Optional.of(patient));
        when(turnRepo.existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(doctorId, dto.getScheduledAt()))
                .thenReturn(false);
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(savedTurn);
        when(mapper.toDTO(savedTurn)).thenReturn(turnResponse);

        when(emailService.sendAppointmentConfirmationToPatientAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Email service error")));
        when(emailService.sendAppointmentConfirmationToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Email service error")));

        TurnResponseDTO result = turnAssignedService.createTurn(dto);

        assertThat(result).isEqualTo(turnResponse);
        verify(turnRepo).save(any(TurnAssigned.class));
        verify(notificationService).createTurnReservedNotification(eq(doctorId), any(UUID.class), anyString(), anyString(), anyString());
    }

    @Test
    void cancelTurn_EmailFailure_ContinuesExecution() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnAssigned canceledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("CANCELED")
                .build();

        when(turnRepo.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnRepo.save(any(TurnAssigned.class))).thenReturn(canceledTurn);
        when(mapper.toDTO(canceledTurn)).thenReturn(turnResponse);
        when(turnFileService.fileExistsForTurn(turnId)).thenReturn(false);

        when(emailService.sendAppointmentCancellationToPatientAsync(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Email service error")));
        when(emailService.sendAppointmentCancellationToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Email service error")));

        TurnResponseDTO result = turnAssignedService.cancelTurn(turnId, patientId, "PATIENT");

        assertNotNull(result);
        verify(turnRepo).findById(turnId);
        verify(turnRepo).save(scheduledTurn);
        verify(mapper).toDTO(canceledTurn);
        assertEquals("CANCELED", scheduledTurn.getStatus());
    }

    private TurnCreateRequestDTO createTurnRequestDTO() {
        TurnCreateRequestDTO dto = new TurnCreateRequestDTO();
        dto.setDoctorId(doctorId);
        dto.setPatientId(patientId);
        dto.setScheduledAt(scheduledAt);
        return dto;
    }
}
