package com.medibook.api.controller;

import com.medibook.api.dto.ErrorResponseDTO;
import com.medibook.api.dto.Auth.RegisterRequestDTO;
import com.medibook.api.dto.Auth.RegisterResponseDTO;
import com.medibook.api.dto.Auth.SignInRequestDTO;
import com.medibook.api.dto.Auth.SignInResultDTO;
import com.medibook.api.service.AuthService;
import com.medibook.api.util.RefreshTokenCookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieUtil refreshTokenCookieUtil;

    public AuthController(AuthService authService, RefreshTokenCookieUtil refreshTokenCookieUtil) {
        this.authService = authService;
        this.refreshTokenCookieUtil = refreshTokenCookieUtil;
    }

    @PostMapping("/register/patient")
    public ResponseEntity<?> registerPatient(
            @Valid @RequestBody RegisterRequestDTO request, 
            HttpServletRequest httpRequest) {
        try {
            RegisterResponseDTO response = authService.registerPatient(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                "REGISTRATION_FAILED", 
                e.getMessage(), 
                HttpStatus.BAD_REQUEST.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/register/doctor")
    public ResponseEntity<?> registerDoctor(
            @Valid @RequestBody RegisterRequestDTO request, 
            HttpServletRequest httpRequest) {
        try {
            RegisterResponseDTO response = authService.registerDoctor(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                "DOCTOR_REGISTRATION_FAILED", 
                e.getMessage(), 
                HttpStatus.BAD_REQUEST.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registerAdmin(
            @Valid @RequestBody RegisterRequestDTO request, 
            HttpServletRequest httpRequest) {
        try {
            RegisterResponseDTO response = authService.registerAdmin(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                "ADMIN_REGISTRATION_FAILED", 
                e.getMessage(), 
                HttpStatus.BAD_REQUEST.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyAccount(@RequestParam("token") String token) {
        try {
            authService.verifyAccount(token);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Mail validado correctamente");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(
            @Valid @RequestBody SignInRequestDTO request, 
            HttpServletRequest httpRequest) {
        try {
            SignInResultDTO result = authService.signIn(request);
            // FSEC-H1 Stage 3: the refresh token travels ONLY via the httpOnly cookie.
            // It is never placed in the JSON body (closes the XSS/body-exposure surface).
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            refreshTokenCookieUtil.build(result.refreshToken()).toString())
                    .body(result.response());
        } catch (IllegalArgumentException e) {
            String errorCode = determineSignInErrorCode(e.getMessage());
            ErrorResponseDTO error = ErrorResponseDTO.of(
                errorCode, 
                e.getMessage(), 
                HttpStatus.UNAUTHORIZED.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signOut(HttpServletRequest httpRequest) {
        // FSEC-H1 Stage 3: read the refresh token ONLY from the httpOnly cookie (the
        // Refresh-Token header fallback has been removed). Always clear the cookie so
        // signout is idempotent even when the token is absent.
        String refreshToken = refreshTokenCookieUtil.read(httpRequest).orElse(null);
        try {
            authService.signOut(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookieUtil.clear().toString())
                    .build();
        } catch (IllegalArgumentException e) {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                "SIGNOUT_FAILED",
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest httpRequest) {
        // FSEC-H1 Stage 3: read the refresh token ONLY from the httpOnly cookie (the
        // Refresh-Token header fallback has been removed). Missing cookie -> 401.
        String refreshToken = refreshTokenCookieUtil.read(httpRequest).orElse(null);
        if (refreshToken == null) {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                "TOKEN_REFRESH_FAILED",
                "Missing refresh token",
                HttpStatus.UNAUTHORIZED.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }
        try {
            SignInResultDTO result = authService.refreshToken(refreshToken);
            // Rotate the cookie with the freshly-minted raw refresh token (cookie-only).
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            refreshTokenCookieUtil.build(result.refreshToken()).toString())
                    .body(result.response());
        } catch (IllegalArgumentException e) {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                "TOKEN_REFRESH_FAILED",
                e.getMessage(),
                HttpStatus.UNAUTHORIZED.value(),
                httpRequest.getRequestURI()
            );
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }
    }

    private String determineSignInErrorCode(String message) {
        if (message.contains("Correo o contraseña incorrecto")) {
            return "INVALID_CREDENTIALS";
        }
        if (message.contains("not found") || message.contains("ACTIVE")) {
            return "ACCOUNT_NOT_ACTIVE";
        }
        return "SIGNIN_FAILED";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("message", "Please check the following fields");
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("path", request.getRequestURI());
        response.put("fieldErrors", validationErrors);
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        
        ErrorResponseDTO error = ErrorResponseDTO.of(
            "BAD_REQUEST", 
            ex.getMessage(), 
            HttpStatus.BAD_REQUEST.value(),
            request.getRequestURI()
        );
        
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {

        ErrorResponseDTO error = ErrorResponseDTO.of(
            "FORBIDDEN",
            "Access denied",
            HttpStatus.FORBIDDEN.value(),
            request.getRequestURI()
        );

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        ErrorResponseDTO error = ErrorResponseDTO.of(
            "INTERNAL_SERVER_ERROR", 
            "An unexpected error occurred", 
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getRequestURI()
        );
        
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}