package com.medibook.api.service;

import com.medibook.api.dto.Turn.TurnModifyRequestDTO;
import com.medibook.api.dto.Turn.TurnModifyRequestResponseDTO;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.TurnModifyRequest;
import com.medibook.api.entity.User;
import com.medibook.api.exception.SlotUnavailableException;
import com.medibook.api.mapper.TurnModifyRequestMapper;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.TurnModifyRequestRepository;
import com.medibook.api.util.DateTimeUtils;
import com.medibook.api.util.LogMaskingUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnModifyRequestService {
    
    private final TurnModifyRequestRepository turnModifyRequestRepository;
    private static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private final TurnAssignedRepository turnAssignedRepository;
    private final TurnModifyRequestMapper mapper;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final BadgeEvaluationTriggerService badgeEvaluationTrigger;
    
    @Transactional
    public TurnModifyRequestResponseDTO createModifyRequest(TurnModifyRequestDTO dto, User patient) {
        Optional<TurnAssigned> turnOpt = turnAssignedRepository.findById(dto.getTurnId());
        if (turnOpt.isEmpty()) {
            throw new IllegalArgumentException("Turn not found");
        }
        
        TurnAssigned turn = turnOpt.get();
        
        if (!turn.getPatient().getId().equals(patient.getId())) {
            throw new IllegalArgumentException("Turn does not belong to this patient");
        }
        
        if (turn.getScheduledAt().isBefore(OffsetDateTime.now(ARGENTINA_ZONE))) {
            throw new IllegalArgumentException("Cannot modify past appointments");
        }
        
        if (dto.getNewScheduledAt().isBefore(OffsetDateTime.now(ARGENTINA_ZONE))) {
            throw new IllegalArgumentException("Cannot schedule appointments in the past");
        }
        
        Optional<TurnModifyRequest> existingRequest = turnModifyRequestRepository
                .findPendingRequestByTurnAndPatient(dto.getTurnId(), patient.getId());
        if (existingRequest.isPresent()) {
            throw new IllegalArgumentException("There is already a pending modification request for this appointment");
        }
        
        TurnModifyRequest modifyRequest = TurnModifyRequest.builder()
                .turnAssigned(turn)
                .patient(patient)
                .doctor(turn.getDoctor())
                .currentScheduledAt(turn.getScheduledAt())
                .requestedScheduledAt(dto.getNewScheduledAt())
                .status("PENDING")
                .build();
        
        TurnModifyRequest savedRequest = turnModifyRequestRepository.save(modifyRequest);
                
        return mapper.toResponseDTO(savedRequest);
    }
    
    public List<TurnModifyRequestResponseDTO> getPatientRequests(UUID patientId) {
        List<TurnModifyRequest> requests = turnModifyRequestRepository.findByPatient_IdOrderByIdDesc(patientId);
        return requests.stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }
    
    public List<TurnModifyRequestResponseDTO> getDoctorPendingRequests(UUID doctorId) {
        List<TurnModifyRequest> requests = turnModifyRequestRepository
                .findByDoctor_IdAndStatusOrderByIdDesc(doctorId, "PENDING");
        return requests.stream()
                .map(mapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TurnModifyRequestResponseDTO approveModifyRequest(UUID requestId, User doctor) {
        Optional<TurnModifyRequest> requestOpt = turnModifyRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new IllegalArgumentException("Modify request not found");
        }

        TurnModifyRequest request = requestOpt.get();

        if (!request.getDoctor().getId().equals(doctor.getId())) {
            throw new IllegalArgumentException("You can only approve requests for your own appointments");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Request is not pending");
        }

        TurnAssigned turn = request.getTurnAssigned();

        // Re-check the target slot is free (excluding the current turn)
        // before committing, so approval cannot double-book an already-taken slot.
        boolean slotTaken = turnAssignedRepository.existsConflictingTurnExcludingId(
                request.getDoctor().getId(),
                request.getRequestedScheduledAt(),
                turn.getId());
        if (slotTaken) {
            throw new SlotUnavailableException("The requested time slot is no longer available");
        }

        String oldDate = DateTimeUtils.formatDate(turn.getScheduledAt());
        String oldTime = DateTimeUtils.formatTime(turn.getScheduledAt());
        
        
        turn.setScheduledAt(request.getRequestedScheduledAt());
        turnAssignedRepository.save(turn);

        String newDate = DateTimeUtils.formatDate(turn.getScheduledAt());
        String newTime = DateTimeUtils.formatTime(turn.getScheduledAt());

        request.setStatus("APPROVED");
        TurnModifyRequest savedRequest = turnModifyRequestRepository.save(request);

        try {
            
            final String patientEmail = request.getPatient().getEmail();
            final String patientName = request.getPatient().getName();
            final String doctorEmail = request.getDoctor().getEmail();
            final String doctorName = request.getDoctor().getName();
            
            
            emailService.sendAppointmentModificationApprovedToPatientAsync(
                patientEmail,
                patientName,
                doctorName,
                oldDate,
                oldTime,
                newDate,
                newTime
            ).thenAccept(response -> {
                if (response.isSuccess()) {
                    log.info("Email de modificación aprobada enviado al paciente: {}", LogMaskingUtil.maskEmail(patientEmail));
                } else {
                    log.warn("Falló email de modificación al paciente {}: {}", LogMaskingUtil.maskEmail(patientEmail), response.getMessage());
                }
            });
            
            emailService.sendAppointmentModificationApprovedToDoctorAsync(
                doctorEmail,
                doctorName,
                patientName,
                oldDate,
                oldTime,
                newDate,
                newTime
            ).thenAccept(response -> {
                if (response.isSuccess()) {
                    log.info("Email de modificación aprobada enviado al doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
                } else {
                    log.warn("Falló email de modificación al doctor {}: {}", LogMaskingUtil.maskEmail(doctorEmail), response.getMessage());
                }
            });
            
            log.info("Emails de modificación aprobada encolados para paciente {} y doctor {}",
                    LogMaskingUtil.maskEmail(patientEmail), LogMaskingUtil.maskEmail(doctorEmail));
            
        } catch (Exception e) {
            log.warn("Error encolando emails de modificación aprobada: {}", e.getMessage());
        }

        notificationService.createModifyRequestApprovedNotification(
                request.getPatient().getId(), 
                requestId,
                request.getDoctor().getName() + " " + request.getDoctor().getSurname(),
                oldDate,
                oldTime,
                newDate,
                newTime
        );

        badgeEvaluationTrigger.evaluateAfterModifyRequestHandled(doctor.getId());

        return mapper.toResponseDTO(savedRequest);
    }

    @Transactional
    public TurnModifyRequestResponseDTO rejectModifyRequest(UUID requestId, User doctor) {
        Optional<TurnModifyRequest> requestOpt = turnModifyRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new IllegalArgumentException("Modify request not found");
        }

        TurnModifyRequest request = requestOpt.get();

        if (!request.getDoctor().getId().equals(doctor.getId())) {
            throw new IllegalArgumentException("You can only reject requests for your own appointments");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Request is not pending");
        }

        request.setStatus("REJECTED");
        TurnModifyRequest savedRequest = turnModifyRequestRepository.save(request);

        String currentDate = DateTimeUtils.formatDate(request.getCurrentScheduledAt());
        String currentTime = DateTimeUtils.formatTime(request.getCurrentScheduledAt());
        String requestedDate = DateTimeUtils.formatDate(request.getRequestedScheduledAt());
        String requestedTime = DateTimeUtils.formatTime(request.getRequestedScheduledAt());

        notificationService.createModifyRequestRejectedNotification(
                request.getPatient().getId(), 
                requestId, 
                null, 
                request.getDoctor().getName() + " " + request.getDoctor().getSurname(),
                currentDate,
                currentTime,
                requestedDate,
                requestedTime
        );

        badgeEvaluationTrigger.evaluateAfterModifyRequestHandled(doctor.getId());

        return mapper.toResponseDTO(savedRequest);
    }
}