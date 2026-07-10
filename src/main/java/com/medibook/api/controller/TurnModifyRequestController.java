package com.medibook.api.controller;

import com.medibook.api.dto.Turn.TurnModifyRequestDTO;
import com.medibook.api.dto.Turn.TurnModifyRequestResponseDTO;
import com.medibook.api.entity.User;
import com.medibook.api.exception.SlotUnavailableException;
import com.medibook.api.service.TurnModifyRequestService;
import com.medibook.api.util.AuthorizationUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BSEC-M-5: this controller intentionally reads the authenticated principal via
 * {@code request.getAttribute("authenticatedUser")}. That attribute is set by
 * {@code TokenAuthenticationFilter} to the SAME {@code User} as the standard
 * SecurityContext principal, so it is equivalent and secure (see the filter's comment).
 * It is kept on the legacy mechanism deliberately (existing unit tests mock the request
 * attribute); do NOT treat this as a second, divergent authorization source.
 */
@RestController
@RequestMapping("/api/turns/modify-requests")
@RequiredArgsConstructor
@Slf4j
public class TurnModifyRequestController {
    
    private final TurnModifyRequestService turnModifyRequestService;
    
    @PostMapping
    public ResponseEntity<Object> createModifyRequest(
            @Valid @RequestBody TurnModifyRequestDTO dto,
            HttpServletRequest request) {
        
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isPatient(authenticatedUser)) {
            return new ResponseEntity<>(
                    Map.of("error", "Forbidden", "message", "Only patients can request turn modifications"),
                    HttpStatus.FORBIDDEN);
        }
        
        try {
            TurnModifyRequestResponseDTO result = turnModifyRequestService.createModifyRequest(dto, authenticatedUser);
            return new ResponseEntity<>(result, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(
                    Map.of("error", "Bad Request", "message", e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error creating modify request", e);
            return new ResponseEntity<>(
                    Map.of("error", "Internal Server Error", "message", "An unexpected error occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @GetMapping("/my-requests")
    public ResponseEntity<List<TurnModifyRequestResponseDTO>> getMyRequests(HttpServletRequest request) {
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isPatient(authenticatedUser)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        List<TurnModifyRequestResponseDTO> requests = turnModifyRequestService.getPatientRequests(authenticatedUser.getId());
        return ResponseEntity.ok(requests);
    }
    
    @GetMapping("/pending")
    public ResponseEntity<List<TurnModifyRequestResponseDTO>> getPendingRequests(HttpServletRequest request) {
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isDoctor(authenticatedUser)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        List<TurnModifyRequestResponseDTO> requests = turnModifyRequestService.getDoctorPendingRequests(authenticatedUser.getId());
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<Object> approveModifyRequest(
            @PathVariable UUID requestId,
            HttpServletRequest request) {
        
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isDoctor(authenticatedUser)) {
            return new ResponseEntity<>(
                    Map.of("error", "Forbidden", "message", "Only doctors can approve modification requests"),
                    HttpStatus.FORBIDDEN);
        }
        
        try {
            TurnModifyRequestResponseDTO result = turnModifyRequestService.approveModifyRequest(requestId, authenticatedUser);
            return ResponseEntity.ok(result);
        } catch (SlotUnavailableException e) {
            return new ResponseEntity<>(
                    Map.of("error", "Conflict", "message", e.getMessage()),
                    HttpStatus.CONFLICT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(
                    Map.of("error", "Bad Request", "message", e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error approving modify request", e);
            return new ResponseEntity<>(
                    Map.of("error", "Internal Server Error", "message", "An unexpected error occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<Object> rejectModifyRequest(
            @PathVariable UUID requestId,
            HttpServletRequest request) {
        
        User authenticatedUser = (User) request.getAttribute("authenticatedUser");
        
        if (!AuthorizationUtil.isDoctor(authenticatedUser)) {
            return new ResponseEntity<>(
                    Map.of("error", "Forbidden", "message", "Only doctors can reject modification requests"),
                    HttpStatus.FORBIDDEN);
        }
        
        try {
            TurnModifyRequestResponseDTO result = turnModifyRequestService.rejectModifyRequest(requestId, authenticatedUser);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(
                    Map.of("error", "Bad Request", "message", e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error rejecting modify request", e);
            return new ResponseEntity<>(
                    Map.of("error", "Internal Server Error", "message", "An unexpected error occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}