package com.medibook.api.util;

import com.medibook.api.dto.ErrorResponseDTO;
import com.medibook.api.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

public class AuthorizationUtil {

    public static boolean isAdmin(User user) {
        return user != null && "ADMIN".equals(user.getRole());
    }

    public static boolean isDoctor(User user) {
        return user != null && "DOCTOR".equals(user.getRole());
    }

    public static boolean isPatient(User user) {
        return user != null && "PATIENT".equals(user.getRole());
    }

    public static ResponseEntity<ErrorResponseDTO> createAdminAccessDeniedResponse(String requestUri) {
        ErrorResponseDTO error = ErrorResponseDTO.of(
            "ACCESS_DENIED", 
            "Only administrators can access this endpoint", 
            HttpStatus.FORBIDDEN.value(),
            requestUri
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    public static ResponseEntity<ErrorResponseDTO> createDoctorAccessDeniedResponse(String requestUri) {
        ErrorResponseDTO error = ErrorResponseDTO.of(
            "ACCESS_DENIED", 
            "Only doctors can access this endpoint", 
            HttpStatus.FORBIDDEN.value(),
            requestUri
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    public static ResponseEntity<ErrorResponseDTO> createPatientAccessDeniedResponse(String requestUri) {
        ErrorResponseDTO error = ErrorResponseDTO.of(
            "ACCESS_DENIED", 
            "Only patients can access this endpoint", 
            HttpStatus.FORBIDDEN.value(),
            requestUri
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    public static boolean hasOwnership(User user, UUID resourceOwnerId) {
        return user != null && user.getId() != null && user.getId().equals(resourceOwnerId);
    }

    public static ResponseEntity<Object> createOwnershipAccessDeniedResponse(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message);
    }

    public static ResponseEntity<Object> createInvalidRoleResponse() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid user role");
    }

    /**
     * BSEC-L-1: generic 401 for the case where the authenticated principal is missing
     * (e.g. {@code Authentication} is {@code null} or its principal is not a {@link User}).
     * Returning a consistent {@code UNAUTHORIZED} body avoids dereferencing a null principal
     * (NPE -> 500) and matches the BSEC-M-3 generic-message style.
     */
    public static ResponseEntity<Object> createUnauthenticatedResponse() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(java.util.Map.of("error", "UNAUTHORIZED", "message", "Authentication required"));
    }

    /**
     * BSEC-L-1: null-safe extraction of the authenticated {@link User} from the standard
     * {@code SecurityContext} principal. Returns {@code null} when there is no authenticated
     * user (instead of throwing), so callers can respond with a 401 rather than a 500.
     */
    public static User extractAuthenticatedUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }
}