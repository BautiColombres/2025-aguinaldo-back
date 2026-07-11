package com.medibook.api.service;

import com.medibook.api.dto.email.EmailResponseDto;
import com.medibook.api.dto.Turn.TurnModifyRequestDTO;
import com.medibook.api.dto.Turn.TurnModifyRequestResponseDTO;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.TurnModifyRequest;
import com.medibook.api.entity.User;
import com.medibook.api.exception.SlotUnavailableException;
import com.medibook.api.mapper.TurnModifyRequestMapper;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.TurnModifyRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnModifyRequestServiceTest {

    @Mock
    private TurnModifyRequestRepository turnModifyRequestRepository;
    
    @Mock
    private TurnAssignedRepository turnAssignedRepository;
    
    @Mock
    private TurnModifyRequestMapper mapper;
    
    @Mock
    private com.medibook.api.service.EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BadgeEvaluationTriggerService badgeEvaluationTrigger;
    
    @InjectMocks
    private TurnModifyRequestService service;
    
    private User patient;
    private User doctor;
    private TurnAssigned turnAssigned;
    private TurnModifyRequestDTO requestDTO;
    private TurnModifyRequest modifyRequest;
    private TurnModifyRequestResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        patient = new User();
        patient.setId(UUID.randomUUID());
        patient.setEmail("patient@test.com");
        patient.setName("Juan Pérez");
        
        doctor = new User();
        doctor.setId(UUID.randomUUID());
        doctor.setEmail("doctor@test.com");
        doctor.setName("Dr. García");
        
        OffsetDateTime originalDate = OffsetDateTime.parse("2028-10-09T10:00:00Z");
        OffsetDateTime newDate = OffsetDateTime.parse("2028-10-10T11:00:00Z");
        
        turnAssigned = new TurnAssigned();
        turnAssigned.setId(UUID.randomUUID());
        turnAssigned.setPatient(patient);
        turnAssigned.setDoctor(doctor);
        turnAssigned.setScheduledAt(originalDate);
        
        requestDTO = new TurnModifyRequestDTO();
        requestDTO.setTurnId(turnAssigned.getId());
        requestDTO.setNewScheduledAt(newDate);
        
        modifyRequest = TurnModifyRequest.builder()
                .id(UUID.randomUUID())
                .turnAssigned(turnAssigned)
                .patient(patient)
                .doctor(doctor)
                .currentScheduledAt(turnAssigned.getScheduledAt())
                .requestedScheduledAt(requestDTO.getNewScheduledAt())
                .status("PENDING")
                .build();
        
        responseDTO = new TurnModifyRequestResponseDTO();
        responseDTO.setId(modifyRequest.getId());
        responseDTO.setStatus("PENDING");
        
        EmailResponseDto successEmailResponse = EmailResponseDto.builder()
                .success(true)
                .messageId("test-message-id")
                .message("Email sent successfully")
                .build();
        
        when(emailService.sendAppointmentModificationApprovedToPatientAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(successEmailResponse));
        when(emailService.sendAppointmentModificationApprovedToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(successEmailResponse));
    }

    @Test
    void createModifyRequest_WithValidRequest_ShouldCreateSuccessfully() {
        when(turnAssignedRepository.findById(requestDTO.getTurnId())).thenReturn(Optional.of(turnAssigned));
        when(turnModifyRequestRepository.findPendingRequestByTurnAndPatient(any(), any())).thenReturn(Optional.empty());
        when(turnModifyRequestRepository.save(any(TurnModifyRequest.class))).thenReturn(modifyRequest);
        when(mapper.toResponseDTO(modifyRequest)).thenReturn(responseDTO);
        
        TurnModifyRequestResponseDTO result = service.createModifyRequest(requestDTO, patient);
        
        assertNotNull(result);
        assertEquals(modifyRequest.getId(), result.getId());
        verify(turnAssignedRepository).findById(requestDTO.getTurnId());
        verify(turnModifyRequestRepository).save(any(TurnModifyRequest.class));
        verify(mapper).toResponseDTO(modifyRequest);
    }

    @Test
    void createModifyRequest_WithNonExistentTurn_ShouldThrowException() {
        when(turnAssignedRepository.findById(requestDTO.getTurnId())).thenReturn(Optional.empty());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createModifyRequest(requestDTO, patient));
        assertEquals("Turn not found", exception.getMessage());
        
        verify(turnAssignedRepository).findById(requestDTO.getTurnId());
        verifyNoInteractions(turnModifyRequestRepository);
    }

    @Test
    void createModifyRequest_WithWrongPatient_ShouldThrowException() {
        User otherPatient = new User();
        otherPatient.setId(UUID.randomUUID());
        
        when(turnAssignedRepository.findById(requestDTO.getTurnId())).thenReturn(Optional.of(turnAssigned));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createModifyRequest(requestDTO, otherPatient));
        assertEquals("Turn does not belong to this patient", exception.getMessage());
        
        verify(turnAssignedRepository).findById(requestDTO.getTurnId());
        verifyNoMoreInteractions(turnModifyRequestRepository);
    }

    @Test
    void createModifyRequest_WithPastTurn_ShouldThrowException() {
        turnAssigned.setScheduledAt(OffsetDateTime.parse("2024-10-08T10:00:00Z"));
        when(turnAssignedRepository.findById(requestDTO.getTurnId())).thenReturn(Optional.of(turnAssigned));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createModifyRequest(requestDTO, patient));
        assertEquals("Cannot modify past appointments", exception.getMessage());
        
        verify(turnAssignedRepository).findById(requestDTO.getTurnId());
        verifyNoMoreInteractions(turnModifyRequestRepository);
    }

    @Test
    void createModifyRequest_WithPastNewScheduledAt_ShouldThrowException() {
        requestDTO.setNewScheduledAt(OffsetDateTime.parse("2024-10-08T10:00:00Z"));
        when(turnAssignedRepository.findById(requestDTO.getTurnId())).thenReturn(Optional.of(turnAssigned));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createModifyRequest(requestDTO, patient));
        assertEquals("Cannot schedule appointments in the past", exception.getMessage());
        
        verify(turnAssignedRepository).findById(requestDTO.getTurnId());
        verifyNoMoreInteractions(turnModifyRequestRepository);
    }

    @Test
    void createModifyRequest_WithExistingPendingRequest_ShouldThrowException() {
        when(turnAssignedRepository.findById(requestDTO.getTurnId())).thenReturn(Optional.of(turnAssigned));
        when(turnModifyRequestRepository.findPendingRequestByTurnAndPatient(any(), any()))
                .thenReturn(Optional.of(modifyRequest));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createModifyRequest(requestDTO, patient));
        assertEquals("There is already a pending modification request for this appointment", exception.getMessage());
        
        verify(turnAssignedRepository).findById(requestDTO.getTurnId());
        verify(turnModifyRequestRepository).findPendingRequestByTurnAndPatient(requestDTO.getTurnId(), patient.getId());
        verify(turnModifyRequestRepository, never()).save(any());
    }

    @Test
    void getPatientRequests_ShouldReturnMappedRequests() {
        List<TurnModifyRequest> requests = Arrays.asList(modifyRequest);
        when(turnModifyRequestRepository.findByPatient_IdOrderByIdDesc(patient.getId())).thenReturn(requests);
        when(mapper.toResponseDTO(modifyRequest)).thenReturn(responseDTO);
        
        List<TurnModifyRequestResponseDTO> result = service.getPatientRequests(patient.getId());
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(responseDTO, result.get(0));
        verify(turnModifyRequestRepository).findByPatient_IdOrderByIdDesc(patient.getId());
        verify(mapper).toResponseDTO(modifyRequest);
    }

    @Test
    void getDoctorPendingRequests_ShouldReturnMappedRequests() {
        List<TurnModifyRequest> requests = Arrays.asList(modifyRequest);
        when(turnModifyRequestRepository.findByDoctor_IdAndStatusOrderByIdDesc(doctor.getId(), "PENDING"))
                .thenReturn(requests);
        when(mapper.toResponseDTO(modifyRequest)).thenReturn(responseDTO);
        
        List<TurnModifyRequestResponseDTO> result = service.getDoctorPendingRequests(doctor.getId());
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(responseDTO, result.get(0));
        verify(turnModifyRequestRepository).findByDoctor_IdAndStatusOrderByIdDesc(doctor.getId(), "PENDING");
        verify(mapper).toResponseDTO(modifyRequest);
    }

    @Test
    void approveModifyRequest_WithValidRequest_ShouldApproveSuccessfully() {
        modifyRequest.setStatus("PENDING");
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));
        when(turnAssignedRepository.save(any(TurnAssigned.class))).thenReturn(turnAssigned);
        when(turnModifyRequestRepository.save(any(TurnModifyRequest.class))).thenAnswer(invocation -> {
            TurnModifyRequest req = invocation.getArgument(0);
            req.setStatus("APPROVED");
            return req;
        });
        when(mapper.toResponseDTO(any(TurnModifyRequest.class))).thenReturn(responseDTO);

        TurnModifyRequestResponseDTO result = service.approveModifyRequest(modifyRequest.getId(), doctor);

        assertNotNull(result);
        assertEquals("APPROVED", modifyRequest.getStatus());
        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verify(turnAssignedRepository).save(turnAssigned);
        verify(turnModifyRequestRepository).save(modifyRequest);
        verify(emailService).sendAppointmentModificationApprovedToPatientAsync(
                patient.getEmail(), patient.getName(), doctor.getName(),
                "09/10/2028", "07:00", "10/10/2028", "08:00");
        verify(emailService).sendAppointmentModificationApprovedToDoctorAsync(
                doctor.getEmail(), doctor.getName(), patient.getName(),
                "09/10/2028", "07:00", "10/10/2028", "08:00");
        verify(notificationService).createModifyRequestApprovedNotification(
                eq(patient.getId()), 
                eq(modifyRequest.getId()),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class)
        );
    }

    @Test
    void approveModifyRequest_TargetSlotFree_ShouldApprove() {
        modifyRequest.setStatus("PENDING");
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));
        when(turnAssignedRepository.existsConflictingTurnExcludingId(
                doctor.getId(), modifyRequest.getRequestedScheduledAt(), turnAssigned.getId()))
                .thenReturn(false);
        when(turnAssignedRepository.save(any(TurnAssigned.class))).thenReturn(turnAssigned);
        when(turnModifyRequestRepository.save(any(TurnModifyRequest.class))).thenAnswer(invocation -> {
            TurnModifyRequest req = invocation.getArgument(0);
            req.setStatus("APPROVED");
            return req;
        });
        when(mapper.toResponseDTO(any(TurnModifyRequest.class))).thenReturn(responseDTO);

        TurnModifyRequestResponseDTO result = service.approveModifyRequest(modifyRequest.getId(), doctor);

        assertNotNull(result);
        assertEquals("APPROVED", modifyRequest.getStatus());
        verify(turnAssignedRepository).existsConflictingTurnExcludingId(
                doctor.getId(), modifyRequest.getRequestedScheduledAt(), turnAssigned.getId());
        verify(turnAssignedRepository).save(turnAssigned);
    }

    @Test
    void approveModifyRequest_TargetSlotAlreadyBooked_ShouldThrowConflictAndNotDoubleBook() {
        modifyRequest.setStatus("PENDING");
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));
        when(turnAssignedRepository.existsConflictingTurnExcludingId(
                doctor.getId(), modifyRequest.getRequestedScheduledAt(), turnAssigned.getId()))
                .thenReturn(true);

        assertThrows(SlotUnavailableException.class,
                () -> service.approveModifyRequest(modifyRequest.getId(), doctor));

        // No double-book: neither the turn nor the request are persisted
        verify(turnAssignedRepository, never()).save(any(TurnAssigned.class));
        verify(turnModifyRequestRepository, never()).save(any(TurnModifyRequest.class));
        assertEquals("PENDING", modifyRequest.getStatus());
    }

    @Test
    void approveModifyRequest_WithNonExistentRequest_ShouldThrowException() {
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveModifyRequest(modifyRequest.getId(), doctor));
        assertEquals("Modify request not found", exception.getMessage());

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verifyNoMoreInteractions(turnAssignedRepository);
    }

    @Test
    void approveModifyRequest_WithWrongDoctor_ShouldThrowException() {
        User otherDoctor = new User();
        otherDoctor.setId(UUID.randomUUID());

        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveModifyRequest(modifyRequest.getId(), otherDoctor));
        assertEquals("You can only approve requests for your own appointments", exception.getMessage());

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verifyNoMoreInteractions(turnAssignedRepository);
    }

    @Test
    void approveModifyRequest_WithNonPendingRequest_ShouldThrowException() {
        modifyRequest.setStatus("APPROVED");
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveModifyRequest(modifyRequest.getId(), doctor));
        assertEquals("Request is not pending", exception.getMessage());

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verifyNoMoreInteractions(turnAssignedRepository);
    }

    @Test
    void rejectModifyRequest_WithValidRequest_ShouldRejectSuccessfully() {
        modifyRequest.setStatus("PENDING");
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));
        when(turnModifyRequestRepository.save(any(TurnModifyRequest.class))).thenAnswer(invocation -> {
            TurnModifyRequest req = invocation.getArgument(0);
            req.setStatus("REJECTED");
            return req;
        });
        when(mapper.toResponseDTO(any(TurnModifyRequest.class))).thenReturn(responseDTO);

        TurnModifyRequestResponseDTO result = service.rejectModifyRequest(modifyRequest.getId(), doctor);

        assertNotNull(result);
        assertEquals("REJECTED", modifyRequest.getStatus());
        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verify(turnModifyRequestRepository).save(modifyRequest);
        verify(notificationService).createModifyRequestRejectedNotification(
                eq(patient.getId()), 
                eq(modifyRequest.getId()), 
                isNull(),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class)
        );
        verify(mapper).toResponseDTO(any(TurnModifyRequest.class));
    }

    @Test
    void rejectModifyRequest_WithNonExistentRequest_ShouldThrowException() {
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.rejectModifyRequest(modifyRequest.getId(), doctor));
        assertEquals("Modify request not found", exception.getMessage());

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verifyNoMoreInteractions(turnModifyRequestRepository);
    }

    @Test
    void rejectModifyRequest_WithWrongDoctor_ShouldThrowException() {
        User otherDoctor = new User();
        otherDoctor.setId(UUID.randomUUID());

        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.rejectModifyRequest(modifyRequest.getId(), otherDoctor));
        assertEquals("You can only reject requests for your own appointments", exception.getMessage());

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verifyNoMoreInteractions(turnModifyRequestRepository);
    }

    @Test
    void rejectModifyRequest_WithNonPendingRequest_ShouldThrowException() {
        modifyRequest.setStatus("APPROVED");
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.rejectModifyRequest(modifyRequest.getId(), doctor));
        assertEquals("Request is not pending", exception.getMessage());

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verifyNoMoreInteractions(turnModifyRequestRepository);
    }

    @Test
    void approveModifyRequest_WithEmailFailure_ShouldStillApproveAndLogWarning() {
        when(turnModifyRequestRepository.findById(modifyRequest.getId())).thenReturn(Optional.of(modifyRequest));
        when(turnAssignedRepository.save(any(TurnAssigned.class))).thenReturn(turnAssigned);
        when(turnModifyRequestRepository.save(any(TurnModifyRequest.class))).thenReturn(modifyRequest);
        when(mapper.toResponseDTO(any(TurnModifyRequest.class))).thenReturn(responseDTO);

        CompletableFuture<EmailResponseDto> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Email service error"));
        when(emailService.sendAppointmentModificationApprovedToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        TurnModifyRequestResponseDTO result = service.approveModifyRequest(modifyRequest.getId(), doctor);

        assertNotNull(result);
        verify(turnModifyRequestRepository).save(modifyRequest);

        verify(turnModifyRequestRepository).findById(modifyRequest.getId());
        verify(turnAssignedRepository).save(turnAssigned);
        verify(turnModifyRequestRepository).save(modifyRequest);
        verify(emailService).sendAppointmentModificationApprovedToPatientAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(emailService).sendAppointmentModificationApprovedToDoctorAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(notificationService).createModifyRequestApprovedNotification(
                any(UUID.class), 
                any(UUID.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class)
        );
    }
}