package com.medibook.api.controller;

import com.medibook.api.dto.ProfileResponseDTO;
import com.medibook.api.dto.ProfileUpdateRequestDTO;
import com.medibook.api.service.ProfileService;
import com.medibook.api.entity.User;
import com.medibook.api.util.AuthorizationUtil;
import com.medibook.api.util.ErrorResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable UUID userId, HttpServletRequest request) {
        try {
            User authenticatedUser = (User) request.getAttribute("authenticatedUser");
            
            if (!AuthorizationUtil.hasOwnership(authenticatedUser, userId) && 
                !AuthorizationUtil.isAdmin(authenticatedUser)) {
                return AuthorizationUtil.createOwnershipAccessDeniedResponse(
                    "You can only access your own profile"
                );
            }

            ProfileResponseDTO profile = profileService.getProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            return ErrorResponseUtil.createNotFoundResponse(
                e.getMessage(), 
                request.getRequestURI()
            );
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody ProfileUpdateRequestDTO updateRequest,
            HttpServletRequest request) {
        try {
            User authenticatedUser = (User) request.getAttribute("authenticatedUser");
            
            if (!AuthorizationUtil.hasOwnership(authenticatedUser, userId) && 
                !AuthorizationUtil.isAdmin(authenticatedUser)) {
                return AuthorizationUtil.createOwnershipAccessDeniedResponse(
                    "You can only update your own profile"
                );
            }

            ProfileResponseDTO updatedProfile = profileService.updateProfile(userId, updateRequest);
            return ResponseEntity.ok(updatedProfile);
        } catch (IllegalArgumentException e) {
            return ErrorResponseUtil.createBadRequestResponse(
                e.getMessage(), 
                request.getRequestURI()
            );
        } catch (RuntimeException e) {
            return ErrorResponseUtil.createNotFoundResponse(
                e.getMessage(), 
                request.getRequestURI()
            );
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(HttpServletRequest request) {
        try {
            User authenticatedUser = (User) request.getAttribute("authenticatedUser");
            ProfileResponseDTO profile = profileService.getProfile(authenticatedUser.getId());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @Valid @RequestBody ProfileUpdateRequestDTO updateRequest,
            HttpServletRequest request) {
        try {
            User authenticatedUser = (User) request.getAttribute("authenticatedUser");
            ProfileResponseDTO updatedProfile = profileService.updateProfile(
                authenticatedUser.getId(), 
                updateRequest
            );
            return ResponseEntity.ok(updatedProfile);
        } catch (IllegalArgumentException e) {
            return ErrorResponseUtil.createBadRequestResponse(
                e.getMessage(), 
                request.getRequestURI()
            );
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }

    @DeleteMapping("/me/deactivate")
    public ResponseEntity<?> deactivateMyAccount(HttpServletRequest request) {
        try {
            User authenticatedUser = (User) request.getAttribute("authenticatedUser");
            profileService.deactivateUser(authenticatedUser.getId());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Account deactivated successfully");
            response.put("status", "DISABLED");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ErrorResponseUtil.createDatabaseErrorResponse(request.getRequestURI());
        }
    }
}
