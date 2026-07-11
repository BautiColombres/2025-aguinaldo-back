package com.medibook.api.controller;

import com.medibook.api.dto.ErrorResponseDTO;
import com.medibook.api.dto.Admin.AdminStatsDTO;
import com.medibook.api.dto.Admin.AdminRatingsResponseDTO;
import com.medibook.api.dto.Admin.DoctorApprovalResponseDTO;
import com.medibook.api.dto.Admin.PendingDoctorDTO;
import com.medibook.api.dto.Rating.RatingResponseDTO;
import com.medibook.api.entity.Rating;
import com.medibook.api.entity.User;
import com.medibook.api.mapper.AdminMapper;
import com.medibook.api.mapper.RatingMapper;
import com.medibook.api.repository.RatingRepository;
import com.medibook.api.repository.UserRepository;
import com.medibook.api.service.EmailService;
import com.medibook.api.util.AuthorizationUtil;
import com.medibook.api.util.ErrorResponseUtil;
import com.medibook.api.util.LogMaskingUtil;
import com.medibook.api.util.UserValidationUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BSEC-M-5: this controller intentionally reads the authenticated principal via
 * {@code request.getAttribute("authenticatedUser")}. That attribute is set by
 * {@code TokenAuthenticationFilter} to the SAME {@code User} as the standard
 * SecurityContext principal, so it is equivalent and secure (see the filter's comment).
 * It is kept on the legacy mechanism deliberately (existing tests mock the request
 * attribute); do NOT treat this as a second, divergent authorization source.
 */
@RestController
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final EmailService emailService;
    private final RatingRepository ratingRepository;
    private final RatingMapper ratingMapper;

    public AdminController(UserRepository userRepository, AdminMapper adminMapper, EmailService emailService, 
                          RatingRepository ratingRepository, RatingMapper ratingMapper) {
        this.userRepository = userRepository;
        this.adminMapper = adminMapper;
        this.emailService = emailService;
        this.ratingRepository = ratingRepository;
        this.ratingMapper = ratingMapper;
    }

    @GetMapping("/pending-doctors")
    public ResponseEntity<?> getPendingDoctors(HttpServletRequest request) {
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isAdmin(authenticatedUser)) {
            return AuthorizationUtil.createAdminAccessDeniedResponse(request.getRequestURI());
        }

        try {
            List<User> pendingDoctors = userRepository.findByRoleAndStatusAndEmailVerified("DOCTOR", "PENDING", true);
            List<PendingDoctorDTO> pendingDoctorDTOs = pendingDoctors.stream()
                .map(adminMapper::convertToPendingDoctorDTO)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(pendingDoctorDTOs);
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @PostMapping("/approve-doctor/{doctorId}")
    public ResponseEntity<?> approveDoctor(
            @PathVariable UUID doctorId, 
            HttpServletRequest request) {
        
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isAdmin(authenticatedUser)) {
            return AuthorizationUtil.createAdminAccessDeniedResponse(request.getRequestURI());
        }

        try {
            User doctor = userRepository.findById(doctorId)
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found with ID: " + doctorId));

            ResponseEntity<ErrorResponseDTO> validationError = UserValidationUtil.validateDoctorForStatusChange(doctor, request.getRequestURI());
            if (validationError != null) {
                return validationError;
            }

            doctor.setStatus("ACTIVE");
            userRepository.save(doctor);

            try {
                final String doctorEmail = doctor.getEmail();
                final String doctorName = doctor.getName();
                
                emailService.sendApprovalEmailToDoctorAsync(doctorEmail, doctorName)
                    .thenAccept(response -> {
                        if (response.isSuccess()) {
                            log.info("Email de aprobación enviado al doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
                        } else {
                            log.warn("Falló email de aprobación al doctor {}: {}", LogMaskingUtil.maskEmail(doctorEmail), response.getMessage());
                        }
                    });

                log.info("Email de aprobación encolado para doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
            } catch (Exception e) {
                log.warn("Error encolando email de aprobación al doctor: {}", e.getMessage());
            }

            DoctorApprovalResponseDTO response = new DoctorApprovalResponseDTO(
                "Doctor approved successfully",
                doctorId.toString(),
                "ACTIVE"
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ErrorResponseUtil.createDoctorNotFoundResponse(e.getMessage(), request.getRequestURI());
        } catch (Exception e) {
            return ErrorResponseUtil.createErrorResponse(
                "APPROVAL_FAILED", 
                "Failed to approve doctor", 
                HttpStatus.INTERNAL_SERVER_ERROR, 
                request.getRequestURI()
            );
        }
    }

    @PostMapping("/reject-doctor/{doctorId}")
    public ResponseEntity<?> rejectDoctor(
            @PathVariable UUID doctorId, 
            HttpServletRequest request) {
        
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isAdmin(authenticatedUser)) {
            return AuthorizationUtil.createAdminAccessDeniedResponse(request.getRequestURI());
        }

        try {
            User doctor = userRepository.findById(doctorId)
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found with ID: " + doctorId));

            ResponseEntity<ErrorResponseDTO> validationError = UserValidationUtil.validateDoctorForStatusChange(doctor, request.getRequestURI());
            if (validationError != null) {
                return validationError;
            }

            doctor.setStatus("REJECTED");
            userRepository.save(doctor);

            try {
                final String doctorEmail = doctor.getEmail();
                final String doctorName = doctor.getName();
                final String rejectionReason = "Tu solicitud de registro como médico no ha sido aprobada. Por favor, contacta al equipo de soporte si necesitas más información.";
                
                emailService.sendRejectionEmailToDoctorAsync(doctorEmail, doctorName, rejectionReason)
                    .thenAccept(response -> {
                        if (response.isSuccess()) {
                            log.info("Email de rechazo enviado al doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
                        } else {
                            log.warn("Falló email de rechazo al doctor {}: {}", LogMaskingUtil.maskEmail(doctorEmail), response.getMessage());
                        }
                    });

                log.info("Email de rechazo encolado para doctor: {}", LogMaskingUtil.maskEmail(doctorEmail));
            } catch (Exception e) {
                log.warn("Error encolando email de rechazo al doctor: {}", e.getMessage());
            }

            DoctorApprovalResponseDTO response = new DoctorApprovalResponseDTO(
                "Doctor rejected successfully",
                doctorId.toString(),
                "REJECTED"
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ErrorResponseUtil.createDoctorNotFoundResponse(e.getMessage(), request.getRequestURI());
        } catch (Exception e) {
            return ErrorResponseUtil.createErrorResponse(
                "REJECTION_FAILED", 
                "Failed to reject doctor", 
                HttpStatus.INTERNAL_SERVER_ERROR, 
                request.getRequestURI()
            );
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats(HttpServletRequest request) {
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isAdmin(authenticatedUser)) {
            return AuthorizationUtil.createAdminAccessDeniedResponse(request.getRequestURI());
        }

        try {
            long patientsCount = userRepository.countByRoleAndEmailVerified("PATIENT", true);
            long doctorsCount = userRepository.countByRoleAndStatusAndEmailVerified("DOCTOR", "ACTIVE", true);
            long pendingCount = userRepository.countByRoleAndStatusAndEmailVerified("DOCTOR", "PENDING", true);

            AdminStatsDTO stats = new AdminStatsDTO(
                (int) patientsCount,
                (int) doctorsCount,
                (int) pendingCount
            );

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @GetMapping("/ratings")
    public ResponseEntity<?> getAllRatings(HttpServletRequest request) {
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isAdmin(authenticatedUser)) {
            return AuthorizationUtil.createAdminAccessDeniedResponse(request.getRequestURI());
        }

        try {
            List<Rating> allRatings = ratingRepository.findAllOrderByCreatedAtDesc();
            
            List<RatingResponseDTO> allRatingDTOs = allRatings.stream()
                .map(ratingMapper::toDTO)
                .collect(Collectors.toList());
            
            List<RatingResponseDTO> patientRatings = allRatingDTOs.stream()
                .filter(rating -> {
                    return allRatings.stream()
                        .anyMatch(r -> r.getId().equals(rating.getId()) && "PATIENT".equals(r.getRater().getRole()));
                })
                .collect(Collectors.toList());
                
            List<RatingResponseDTO> doctorRatings = allRatingDTOs.stream()
                .filter(rating -> {
                    return allRatings.stream()
                        .anyMatch(r -> r.getId().equals(rating.getId()) && "DOCTOR".equals(r.getRater().getRole()));
                })
                .collect(Collectors.toList());
            
            AdminRatingsResponseDTO response = AdminRatingsResponseDTO.builder()
                .allRatings(allRatingDTOs)
                .patientRatings(patientRatings)
                .doctorRatings(doctorRatings)
                .stats(null) 
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching admin ratings: ", e);
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        
        return ErrorResponseUtil.createBadRequestResponse(ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        return ErrorResponseUtil.createInternalServerErrorResponse(
            "An unexpected error occurred", 
            request.getRequestURI()
        );
    }
}