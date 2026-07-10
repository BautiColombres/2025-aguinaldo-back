package com.medibook.api.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.medibook.api.dto.Turn.TurnCreateRequestDTO;
import com.medibook.api.dto.Turn.TurnResponseDTO;

import static com.medibook.api.util.DateTimeUtils.ARGENTINA_ZONE;
import com.medibook.api.entity.User;
import com.medibook.api.dto.Availability.AvailableSlotDTO;
import com.medibook.api.service.DoctorAvailabilityService;
import com.medibook.api.service.TurnAssignedService;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.util.AuthorizationUtil;
import com.medibook.api.util.TurnAuthorizationUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.medibook.api.util.ErrorResponseUtil;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/turns")
@RequiredArgsConstructor
@Slf4j
public class TurnAssignedController {

    private final TurnAssignedService turnService;
    private final DoctorAvailabilityService doctorAvailabilityService;
    private final TurnAssignedRepository turnAssignedRepository;

    @PostMapping
    public ResponseEntity<Object> createTurn(
            @Valid @RequestBody TurnCreateRequestDTO dto,
            Authentication authentication) {

        // BSEC-M-5: read the principal from the standard SecurityContext (set by
        // TokenAuthenticationFilter) instead of the request attribute.
        User authenticatedUser = (User) authentication.getPrincipal();
        
        if (!AuthorizationUtil.isPatient(authenticatedUser)) {
            return new ResponseEntity<>(
                Map.of("error", "Forbidden", "message", "Only patients can create turns"), 
                HttpStatus.FORBIDDEN);
        }
        
        ResponseEntity<Object> validationError = TurnAuthorizationUtil.validatePatientTurnCreation(authenticatedUser, dto.getPatientId());
        if (validationError != null) {
            return validationError;
        }
        
        if (dto.getScheduledAt() != null && dto.getScheduledAt().isBefore(OffsetDateTime.now(ARGENTINA_ZONE))) {
            return new ResponseEntity<>(
                Map.of("error", "Bad Request", "message", "Cannot schedule turns in the past"), 
                HttpStatus.BAD_REQUEST);
        }
        
        // BBUG-M4: only surface a message for the KNOWN business case (slot conflict → 409;
        // that string is safe/intended). Any OTHER RuntimeException (NPE, persistence
        // failure, unexpected fault) must NOT be echoed to the client nor masked as a 400:
        // let it propagate so the framework returns a generic 500 (server.error.include-*
        // are set to never), matching the BSEC-M-3 generic-message rule.
        try {
            TurnResponseDTO result = turnService.createTurn(dto);
            return new ResponseEntity<>(result, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message != null && message.contains("already taken")) {
                return new ResponseEntity<>(
                        Map.of("error", HttpStatus.CONFLICT.getReasonPhrase(), "message", message),
                        HttpStatus.CONFLICT);
            }
            throw e;
        }
    }

    @GetMapping("/available")
    public ResponseEntity<List<OffsetDateTime>> getAvailableTurns(
            @RequestParam UUID doctorId,
            @RequestParam String date,
            HttpServletRequest request) {
        
        LocalDate localDate = LocalDate.parse(date);
        
        List<AvailableSlotDTO> availableSlots = doctorAvailabilityService.getAvailableSlots(
                doctorId, localDate, localDate);
        
        if (availableSlots.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        
        List<OffsetDateTime> availableTimes = new ArrayList<>();
        ZoneOffset argentinaOffset = ZoneOffset.of("-03:00");
        
        for (AvailableSlotDTO slot : availableSlots) {
            OffsetDateTime slotDateTime = slot.getDate().atTime(slot.getStartTime()).atOffset(argentinaOffset);
            
            boolean isOccupied = turnAssignedRepository.existsByDoctor_IdAndScheduledAtAndStatusNotCancelled(doctorId, slotDateTime);
            
            if (!isOccupied) {
                availableTimes.add(slotDateTime);
            }
        }
        
        return ResponseEntity.ok(availableTimes);
    }

    @GetMapping("/my-turns")
    public ResponseEntity<Object> getMyTurns(
            @RequestParam(required = false) String status,
            Authentication authentication) {

        User authenticatedUser = (User) authentication.getPrincipal();
        
        List<TurnResponseDTO> turns;
        
        if (AuthorizationUtil.isDoctor(authenticatedUser)) {
            if (status != null && !status.isEmpty()) {
                turns = turnService.getTurnsByDoctorAndStatus(authenticatedUser.getId(), status);
            } else {
                turns = turnService.getTurnsByDoctor(authenticatedUser.getId());
            }
        } else if (AuthorizationUtil.isPatient(authenticatedUser)) {
            if (status != null && !status.isEmpty()) {
                turns = turnService.getTurnsByPatientAndStatus(authenticatedUser.getId(), status);
            } else {
                turns = turnService.getTurnsByPatient(authenticatedUser.getId());
            }
        } else {
            return AuthorizationUtil.createInvalidRoleResponse();
        }
        
        return ResponseEntity.ok(turns);
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<Object> getTurnsByDoctor(
            @PathVariable UUID doctorId,
            @RequestParam(required = false) String status,
            Authentication authentication) {

        User authenticatedUser = (User) authentication.getPrincipal();
        
        ResponseEntity<Object> validationError = TurnAuthorizationUtil.validateDoctorTurnAccess(authenticatedUser, doctorId);
        if (validationError != null) {
            return validationError;
        }
        
        List<TurnResponseDTO> turns;
        if (status != null && !status.isEmpty()) {
            turns = turnService.getTurnsByDoctorAndStatus(doctorId, status);
        } else {
            turns = turnService.getTurnsByDoctor(doctorId);
        }
        
        return ResponseEntity.ok(turns);
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Object> getTurnsByPatient(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String status,
            Authentication authentication) {

        User authenticatedUser = (User) authentication.getPrincipal();
        
        ResponseEntity<Object> validationError = TurnAuthorizationUtil.validatePatientTurnAccess(authenticatedUser, patientId);
        if (validationError != null) {
            return validationError;
        }
        
        List<TurnResponseDTO> turns;
        if (status != null && !status.isEmpty()) {
            turns = turnService.getTurnsByPatientAndStatus(patientId, status);
        } else {
            turns = turnService.getTurnsByPatient(patientId);
        }
        
        return ResponseEntity.ok(turns);
    }
    
    @PatchMapping("/{turnId}/cancel")
    public ResponseEntity<Object> cancelTurn(
            @PathVariable UUID turnId,
            Authentication authentication) {

        User authenticatedUser = (User) authentication.getPrincipal();
        
        if (!"PATIENT".equals(authenticatedUser.getRole()) && !"DOCTOR".equals(authenticatedUser.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only patients and doctors can cancel turns");
        }
        
        try {
            TurnResponseDTO canceledTurn = turnService.cancelTurn(turnId, authenticatedUser.getId(), authenticatedUser.getRole());
            return ResponseEntity.ok(canceledTurn);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage());
        }
    }

    @PostMapping("/{turnId}/complete")
    public ResponseEntity<Object> completeTurn(
            @PathVariable UUID turnId,
            Authentication authentication,
            HttpServletRequest request) {

        // BSEC-M-5: principal from SecurityContext; request kept only for getRequestURI().
        User authenticatedUser = (User) authentication.getPrincipal();

        if (!"DOCTOR".equals(authenticatedUser.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only doctors can complete turns");
        }

        try {
            TurnResponseDTO completed = turnService.completeTurn(turnId, authenticatedUser.getId());
            return ResponseEntity.ok(completed);
        } catch (RuntimeException e) {
            var resp = ErrorResponseUtil.createBadRequestResponse(e.getMessage(), request.getRequestURI());
            return new ResponseEntity<Object>(resp.getBody(), resp.getStatusCode());
        }
    }

    @PostMapping("/{turnId}/no-show")
    public ResponseEntity<Object> markTurnAsNoShow(
            @PathVariable UUID turnId,
            Authentication authentication,
            HttpServletRequest request) {

        // BSEC-M-5: principal from SecurityContext; request kept only for getRequestURI().
        User authenticatedUser = (User) authentication.getPrincipal();

        if (!"DOCTOR".equals(authenticatedUser.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only doctors can mark turns as no-show");
        }

        try {
            TurnResponseDTO noShow = turnService.markTurnAsNoShow(turnId, authenticatedUser.getId());
            return ResponseEntity.ok(noShow);
        } catch (RuntimeException e) {
            var resp = ErrorResponseUtil.createBadRequestResponse(e.getMessage(), request.getRequestURI());
            return new ResponseEntity<Object>(resp.getBody(), resp.getStatusCode());
        }
    }
   
}
