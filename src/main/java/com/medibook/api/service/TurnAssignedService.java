package com.medibook.api.service;

import com.medibook.api.dto.Turn.TurnCreateRequestDTO;
import com.medibook.api.dto.Turn.TurnResponseDTO;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.TurnAssignedMapper;
import com.medibook.api.entity.Rating;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.UserRepository;
import com.medibook.api.util.DateTimeUtils;
import com.medibook.api.util.LogMaskingUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TurnAssignedService {

    private final TurnAssignedRepository turnRepo;
    private final UserRepository userRepo;
    private final TurnAssignedMapper mapper;
    private final RatingRepository ratingRepo;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TurnFileService turnFileService;
    private final BadgeEvaluationTriggerService badgeEvaluationTrigger;
    private final MedicalCheckApiService medicalCheckApiService;
    private static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Self-reference through the Spring transactional proxy, so
     * {@link #completeTurnPersist} runs within its own transaction even though it is
     * invoked from {@link #completeTurn} (self-invocation would otherwise bypass the
     * proxy). Injected lazily to avoid a constructor cycle.
     */
    @Autowired
    @Lazy
    private TurnAssignedService self;

    public TurnResponseDTO createTurn(TurnCreateRequestDTO dto) {
        User doctor = userRepo.findById(dto.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        
        if (!"DOCTOR".equals(doctor.getRole())) {
            throw new RuntimeException("User is not a doctor");
        }
        
        if (!"ACTIVE".equals(doctor.getStatus())) {
            throw new RuntimeException("Doctor is not active");
        }
        
        User patient = userRepo.findById(dto.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        if (!"PATIENT".equals(patient.getRole())) {
            throw new RuntimeException("User is not a patient");
        }
        
        if (!"ACTIVE".equals(patient.getStatus())) {
            throw new RuntimeException("Patient is not active");
        }

        boolean slotTaken = turnRepo.existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(
                dto.getDoctorId(), dto.getScheduledAt());
        
        if (slotTaken) {
            throw new RuntimeException("Time slot is already taken");
        }

        TurnAssigned turn = TurnAssigned.builder()
                .doctor(doctor)
                .patient(patient)
            .scheduledAt(dto.getScheduledAt())
            .motive(dto.getMotive())
                .status("SCHEDULED")
                .build();

        TurnAssigned saved = turnRepo.save(turn);
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long daysDifference = java.time.Duration.between(now, turn.getScheduledAt()).toDays();
        if (daysDifference >= 1) {
            badgeEvaluationTrigger.evaluateAfterAdvanceBooking(dto.getPatientId());
        }
        
        try {
            String date = DateTimeUtils.formatDate(saved.getScheduledAt());
            String time = DateTimeUtils.formatTime(saved.getScheduledAt());
            
            
            final String patientEmail = patient.getEmail();
            final String patientName = patient.getName();
            final String doctorEmail = doctor.getEmail();
            final String doctorName = doctor.getName();

            emailService.sendAppointmentConfirmationToPatientAsync(
                patientEmail,
                patientName,
                doctorName,
                date,
                time,
                saved.getId().toString()
            ).thenAccept(response -> {
                if (response.isSuccess()) {
                    log.info("Confirmación enviada al paciente: {}", LogMaskingUtil.maskEmail(patientEmail));
                } else {
                    log.warn("Falló confirmación al paciente {}: {}", LogMaskingUtil.maskEmail(patientEmail), response.getMessage());
                }
            });
            
            emailService.sendAppointmentConfirmationToDoctorAsync(
                doctorEmail,
                doctorName,
                patientName,
                date,
                time,
                saved.getId().toString()
            ).thenAccept(response -> {
                if (response.isSuccess()) {
                    log.info("Confirmación enviada al doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
                } else {
                    log.warn("Falló confirmación al doctor {}: {}", LogMaskingUtil.maskEmail(doctorEmail), response.getMessage());
                }
            });
            
            log.info("Emails de confirmación de cita encolados para paciente {} y doctor {}",
                    LogMaskingUtil.maskEmail(patientEmail), LogMaskingUtil.maskEmail(doctorEmail));
            
        } catch (Exception e) {
            log.warn("Error encolando emails de confirmación de cita: {}", e.getMessage());
        }

        try {
            String dateFormatted = DateTimeUtils.formatDate(saved.getScheduledAt());
            String timeFormatted = DateTimeUtils.formatTime(saved.getScheduledAt());
            String patientFullName = patient.getName() + " " + patient.getSurname();

            notificationService.createTurnReservedNotification(
                    saved.getDoctor().getId(),
                    saved.getId(),
                    patientFullName,
                    dateFormatted,
                    timeFormatted
            );

            log.info("Notification created for doctor {} about turn creation by patient {}", 
                    saved.getDoctor().getId(), patient.getId());
        } catch (Exception e) {
            log.error("Error creating notification for turn creation: {}", e.getMessage());
        }
        
        return mapper.toDTO(saved);
    }

    public List<TurnResponseDTO> getTurnsByDoctor(UUID doctorId) {
        List<TurnAssigned> turns = turnRepo.findByDoctor_IdOrderByScheduledAtDesc(doctorId);
        return turns.stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TurnResponseDTO> getTurnsByPatient(UUID patientId) {
        List<TurnAssigned> turns = turnRepo.findByPatient_IdOrderByScheduledAtDesc(patientId);
        return turns.stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TurnResponseDTO> getTurnsByDoctorAndStatus(UUID doctorId, String status) {
        List<TurnAssigned> turns = turnRepo.findByDoctor_IdAndStatusOrderByScheduledAtDesc(doctorId, status);
        return turns.stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TurnResponseDTO> getTurnsByPatientAndStatus(UUID patientId, String status) {
        List<TurnAssigned> turns = turnRepo.findByPatient_IdAndStatusOrderByScheduledAtDesc(patientId, status);
        return turns.stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
    
    private final com.medibook.api.repository.TurnModifyRequestRepository turnModifyRequestRepository;

    public TurnResponseDTO cancelTurn(UUID turnId, UUID userId, String userRole) {
        TurnAssigned turn = turnRepo.findById(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found"));
        
        if ("PATIENT".equals(userRole)) {
            if (turn.getPatient() == null || !turn.getPatient().getId().equals(userId)) {
                throw new RuntimeException("You can only cancel your own turns");
            }
        } else if ("DOCTOR".equals(userRole)) {
            if (turn.getDoctor() == null || !turn.getDoctor().getId().equals(userId)) {
                throw new RuntimeException("You can only cancel your own turns");
            }
        } else {
            throw new RuntimeException("Invalid user role for cancellation");
        }
        
        if (!"SCHEDULED".equals(turn.getStatus()) && !"RESERVED".equals(turn.getStatus())) {
            throw new RuntimeException("Turn cannot be canceled. Current status: " + turn.getStatus());
        }
        
        OffsetDateTime now = OffsetDateTime.now(ARGENTINA_ZONE);
        if (turn.getScheduledAt().isBefore(now)) {
            throw new RuntimeException("Cannot cancel past turns");
        }
        
        turn.setStatus("CANCELED");
        TurnAssigned saved = turnRepo.save(turn);

        if (turn.getDoctor() != null) {
            badgeEvaluationTrigger.evaluateAfterTurnCancellation(turn.getDoctor().getId());
        }
        if (turn.getPatient() != null) {
            badgeEvaluationTrigger.evaluateAfterTurnCancellation(turn.getPatient().getId());
        }
        
        // BBUG-H4: never block a reactive chain from here. File cleanup on cancel is
        // best-effort — offload the whole reactive delete onto a bounded scheduler and
        // subscribe (fire-and-forget) instead of calling .block() on the caller thread.
        // Success/failure are logged; cancellation succeeds regardless of file cleanup.
        try {
            if (turnFileService.fileExistsForTurn(turnId)) {
                log.info("Deleting file associated with canceled turn: {}", turnId);
                turnFileService.deleteTurnFile(turnId)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                unused -> log.info("File successfully deleted for canceled turn: {}", turnId),
                                error -> log.warn("Failed to delete file for canceled turn {}: {}",
                                        turnId, error.getMessage()));
            }
        } catch (Exception e) {
            log.warn("Failed to schedule file deletion for canceled turn {}: {}", turnId, e.getMessage());
        }

        try {
            String date = DateTimeUtils.formatDate(saved.getScheduledAt());
            String time = DateTimeUtils.formatTime(saved.getScheduledAt());
            
            
            final String patientEmail = turn.getPatient().getEmail();
            final String patientName = turn.getPatient().getName();
            final String doctorEmail = turn.getDoctor().getEmail();
            final String doctorName = turn.getDoctor().getName();
            
            
            emailService.sendAppointmentCancellationToPatientAsync(
                patientEmail,
                patientName,
                doctorName,
                date,
                time
            ).thenAccept(response -> {
                if (response.isSuccess()) {
                    log.info("Email de cancelación enviado al paciente: {}", LogMaskingUtil.maskEmail(patientEmail));
                } else {
                    log.warn("Falló email de cancelación al paciente {}: {}", LogMaskingUtil.maskEmail(patientEmail), response.getMessage());
                }
            });
            
            emailService.sendAppointmentCancellationToDoctorAsync(
                doctorEmail,
                doctorName,
                patientName,
                date,
                time
            ).thenAccept(response -> {
                if (response.isSuccess()) {
                    log.info("Email de cancelación enviado al doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
                } else {
                    log.warn("Falló email de cancelación al doctor {}: {}", LogMaskingUtil.maskEmail(doctorEmail), response.getMessage());
                }
            });
            
            log.info("Emails de cancelación encolados para paciente {} y doctor {}",
                    LogMaskingUtil.maskEmail(patientEmail), LogMaskingUtil.maskEmail(doctorEmail));
            
        } catch (Exception e) {
            log.warn("Error encolando emails de cancelación: {}", e.getMessage());
        }

        UUID notificationUserId;
        String cancelledBy;
        if ("PATIENT".equals(userRole)) {
            notificationUserId = turn.getDoctor().getId();
            cancelledBy = "patient";
        } else {
            notificationUserId = turn.getPatient().getId();
            cancelledBy = "doctor";
        }
        
        String dateForNotification = DateTimeUtils.formatDate(saved.getScheduledAt());
        String timeForNotification = DateTimeUtils.formatTime(saved.getScheduledAt());
        
        notificationService.createTurnCancellationNotification(
            notificationUserId, 
            turnId, 
            cancelledBy,
            turn.getDoctor().getName() + " " + turn.getDoctor().getSurname(),
            turn.getPatient().getName() + " " + turn.getPatient().getSurname(),
            dateForNotification,
            timeForNotification
        );

        boolean hasPendingRequest = turnModifyRequestRepository.findByTurnAssigned_IdAndStatus(turnId, "PENDING").isPresent();
        if (hasPendingRequest) {
            turnModifyRequestRepository.deleteByTurnAssigned_IdAndStatus(turnId, "PENDING");
        }

        return mapper.toDTO(saved);
    }

    /**
     * Result of the transactional part of completing a turn.
     *
     * @param dto                    the response for the completed turn
     * @param healthCertificateEmail the patient email that requires an external
     *                               medical-check registration, or {@code null} if none
     */
    public record CompletionResult(TurnResponseDTO dto, String healthCertificateEmail) {
    }

    /**
     * Completes a turn. The DB mutation happens inside a transaction
     * ({@link #completeTurnPersist}); the external medical-check API call is issued
     * AFTERWARDS, outside the transactional boundary, so a slow/failing network call
     * never holds a DB connection/transaction open.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TurnResponseDTO completeTurn(UUID turnId, UUID doctorId) {
        CompletionResult result = self.completeTurnPersist(turnId, doctorId);

        String email = result.healthCertificateEmail();
        if (email != null) {
            try {
                log.info("Processing health certificate completion for patient: {}", LogMaskingUtil.maskEmail(email));
                medicalCheckApiService.processMedicalCheckCompletion(email);
            } catch (Exception e) {
                log.error("Error processing medical check API call for turn: {}", turnId, e);
                // Don't fail the turn completion if external API fails
            }
        }

        return result.dto();
    }

    /**
     * Transactional DB work for completing a turn. Does NOT perform any external
     * network call; it only reports (via {@link CompletionResult}) whether a
     * health-certificate medical-check registration is required.
     */
    @Transactional
    public CompletionResult completeTurnPersist(UUID turnId, UUID doctorId) {
        TurnAssigned turn = turnRepo.findById(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found"));

        if (turn.getDoctor() == null || !turn.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("You can only complete your own turns");
        }

        if (!"SCHEDULED".equals(turn.getStatus()) && !"RESERVED".equals(turn.getStatus())) {
            throw new RuntimeException("Turn cannot be completed. Current status: " + turn.getStatus());
        }

        turn.setStatus("COMPLETED");
        TurnAssigned saved = turnRepo.save(turn);

        String healthCertificateEmail = null;
        if ("HEALTH CERTIFICATE".equalsIgnoreCase(turn.getMotive()) && turn.getPatient() != null) {
            healthCertificateEmail = turn.getPatient().getEmail();
        }

        if (turn.getDoctor() != null && turn.getPatient() != null) {
            badgeEvaluationTrigger.evaluateAfterTurnCompletion(
                turn.getDoctor().getId(),
                turn.getPatient().getId()
            );
            badgeEvaluationTrigger.evaluateAfterTurnCompletion(
                turn.getPatient().getId(),
                turn.getDoctor().getId()
            );
        }

        return new CompletionResult(mapper.toDTO(saved), healthCertificateEmail);
    }

    public TurnResponseDTO markTurnAsNoShow(UUID turnId, UUID doctorId) {
        TurnAssigned turn = turnRepo.findById(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found"));

        if (turn.getDoctor() == null || !turn.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("You can only mark no-show for your own turns");
        }

        if (!"SCHEDULED".equals(turn.getStatus())) {
            throw new RuntimeException("Turn cannot be marked as no-show. Current status: " + turn.getStatus());
        }

        turn.setStatus("NO_SHOW");
        TurnAssigned saved = turnRepo.save(turn);

        if (turn.getDoctor() != null) {
            badgeEvaluationTrigger.evaluateAfterTurnNoShow(turn.getDoctor().getId());
        }

        if (turn.getPatient() != null) {
            badgeEvaluationTrigger.evaluateAfterTurnNoShow(turn.getPatient().getId());
        }

        return mapper.toDTO(saved);
    }

    public com.medibook.api.entity.Rating addRating(UUID turnId, UUID raterId, Integer score, java.util.List<String> subcategories) {
        if (score == null || score < 1 || score > 5) {
            throw new RuntimeException("Score must be between 1 and 5");
        }

        TurnAssigned turn = turnRepo.findById(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found"));

        if ("CANCELED".equals(turn.getStatus()) || "CANCELLED".equals(turn.getStatus())) {
            throw new RuntimeException("Cannot rate canceled turns");
        }
        
        OffsetDateTime now = OffsetDateTime.now();
        if (turn.getScheduledAt().isAfter(now)) {
            throw new RuntimeException("Can only rate turns that have already occurred");
        }

        User rater = userRepo.findById(raterId)
                .orElseThrow(() -> new RuntimeException("Rater not found"));

        User ratedUser;
        if ("PATIENT".equals(rater.getRole())) {
            if (turn.getPatient() == null || !turn.getPatient().getId().equals(raterId)) {
                throw new RuntimeException("You can only rate your own turns");
            }
            if (turn.getDoctor() == null) {
                throw new RuntimeException("No doctor to rate for this turn");
            }
            ratedUser = turn.getDoctor();
        } else if ("DOCTOR".equals(rater.getRole())) {
            if (turn.getDoctor() == null || !turn.getDoctor().getId().equals(raterId)) {
                throw new RuntimeException("You can only rate your own turns");
            }
            if (turn.getPatient() == null) {
                throw new RuntimeException("No patient to rate for this turn");
            }
            ratedUser = turn.getPatient();
        } else {
            throw new RuntimeException("Only patients and doctors can rate");
        }
        boolean exists = ratingRepo.existsByTurnAssigned_IdAndRater_Id(turnId, raterId);
        if (exists) {
            throw new RuntimeException("You have already rated this turn");
        }

        String normalizedSubcategory = null;
        if (subcategories != null && !subcategories.isEmpty()) {
            if (subcategories.size() > 3) {
                throw new RuntimeException("You can select up to 3 subcategories");
            }

            String role = rater.getRole() == null ? "" : rater.getRole().trim();
            java.util.List<String> normalized = new java.util.ArrayList<>();

            for (String s : subcategories) {
                if (s == null || s.trim().isEmpty()) continue;
                if ("PATIENT".equalsIgnoreCase(role)) {
                    com.medibook.api.dto.Rating.RatingSubcategory cat = com.medibook.api.dto.Rating.RatingSubcategory.fromString(s);
                    if (cat == null) {
                        String patientAllowed = com.medibook.api.dto.Rating.RatingSubcategory.allowedValues();
                        String doctorAllowed = com.medibook.api.dto.Rating.RatingSubcategoryPatient.allowedValues();
                        log.warn("Invalid subcategory '{}' for rater role '{}'. Patient allowed: {}. Doctor allowed: {}", s, role, patientAllowed, doctorAllowed);
                        throw new RuntimeException("Invalid subcategory. Allowed for patients: " + patientAllowed + "; Allowed for doctors: " + doctorAllowed);
                    }
                    normalized.add(cat.getLabel());
                } else if ("DOCTOR".equalsIgnoreCase(role)) {
                    com.medibook.api.dto.Rating.RatingSubcategoryPatient cat = com.medibook.api.dto.Rating.RatingSubcategoryPatient.fromString(s);
                    if (cat == null) {
                        String doctorAllowed = com.medibook.api.dto.Rating.RatingSubcategoryPatient.allowedValues();
                        log.warn("Invalid subcategory '{}' for rater role '{}'. Doctor allowed: {}", s, role, doctorAllowed);
                        throw new RuntimeException("Invalid subcategory. Allowed for doctors: " + doctorAllowed);
                    }
                    normalized.add(cat.getLabel());
                } else {
                    com.medibook.api.dto.Rating.RatingSubcategory cat = com.medibook.api.dto.Rating.RatingSubcategory.fromString(s);
                    if (cat == null) {
                        String patientAllowed = com.medibook.api.dto.Rating.RatingSubcategory.allowedValues();
                        String doctorAllowed = com.medibook.api.dto.Rating.RatingSubcategoryPatient.allowedValues();
                        throw new RuntimeException("Invalid subcategory. Allowed for patients: " + patientAllowed + "; Allowed for doctors: " + doctorAllowed);
                    }
                    normalized.add(cat.getLabel());
                }
            }

     
            java.util.List<String> unique = new java.util.ArrayList<>();
            for (String v : normalized) if (!unique.contains(v)) unique.add(v);

            normalizedSubcategory = String.join(", ", unique);
        }

        Rating rating = Rating.builder()
                .turnAssigned(turn)
                .rater(rater)
                .rated(ratedUser)
                .score(score)
                .subcategory(normalizedSubcategory)
                .createdAt(OffsetDateTime.now())
                .build();

        Rating saved = ratingRepo.save(rating);

        
        try {
            Double avg = ratingRepo.findAverageScoreByRatedId(ratedUser.getId());
           
            ratedUser.setScore(avg == null ? null : Math.round(avg * 100.0) / 100.0);
            userRepo.save(ratedUser);
        } catch (Exception e) {
            log.warn("Failed to update average score for user {}: {}", ratedUser.getId(), e.getMessage());
        }

        if ("DOCTOR".equals(ratedUser.getRole())) {
            Integer communicationScore = extractCommunicationScore(score, normalizedSubcategory);
            Integer empathyScore = extractEmpathyScore(score, normalizedSubcategory);
            Integer punctualityScore = extractPunctualityScore(score, normalizedSubcategory);
            
            badgeEvaluationTrigger.evaluateAfterRating(ratedUser.getId(), communicationScore, empathyScore, punctualityScore);
        } else if ("PATIENT".equals(ratedUser.getRole())) {
            badgeEvaluationTrigger.evaluateAfterRatingReceived(ratedUser.getId());
        }

        if ("PATIENT".equals(rater.getRole())) {
            badgeEvaluationTrigger.evaluateAfterRatingGiven(rater.getId());
        }

        return saved;
    }
    
    private Integer extractCommunicationScore(Integer score, String subcategories) {
        if (subcategories == null || score == null) return null;
        // Labels exactas de RatingSubcategory relacionadas con comunicación
        if (subcategories.contains("Explica claramente") || subcategories.contains("Escucha al paciente")) {
            return score;
        }
        return null;
    }
    
    private Integer extractEmpathyScore(Integer score, String subcategories) {
        if (subcategories == null || score == null) return null;
        // Labels exactas de RatingSubcategory relacionadas con empatía
        if (subcategories.contains("Demuestra empatía") || subcategories.contains("Genera confianza") || subcategories.contains("Excelente atención")) {
            return score;
        }
        return null;
    }
    
    private Integer extractPunctualityScore(Integer score, String subcategories) {
        if (subcategories == null || score == null) return null;
        // Labels exactas de RatingSubcategory relacionadas con puntualidad
        if (subcategories.contains("Respeta horarios") || subcategories.contains("Tiempo de espera aceptable")) {
            return score;
        }
        return null;
    }

    public boolean hasHealthCertificateWithinLastYear(String email) {
        OffsetDateTime oneYearAgo = OffsetDateTime.now(ARGENTINA_ZONE).minusYears(1);
        return turnRepo.existsHealthCertificateWithinLastYear(email, oneYearAgo);
    }
}
