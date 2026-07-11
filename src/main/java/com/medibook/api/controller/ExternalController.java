package com.medibook.api.controller;

import com.medibook.api.service.RateLimitService;
import com.medibook.api.service.TurnAssignedService;
import com.medibook.api.dto.HealthCertificateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.HashMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@RequestMapping("/api/gymcloud")
@Slf4j
public class ExternalController {

    private final TurnAssignedService turnAssignedService;
    private final RateLimitService externalRateLimitService;

    public ExternalController(TurnAssignedService turnAssignedService,
                              @Qualifier("externalRateLimitService") RateLimitService externalRateLimitService) {
        this.turnAssignedService = turnAssignedService;
        this.externalRateLimitService = externalRateLimitService;
    }

    @Value("${gymcloud.api.keys}")
    private String allowedApiKeysString;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    @PostMapping("/health-certificate")
    public ResponseEntity<Object> checkHealthCertificate(
            @Valid @RequestBody HealthCertificateRequest requestBody,
            @RequestHeader(value = "Authorization", required = true) String authorizationHeader, HttpServletRequest request) {

        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains("application/json")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Bad Request", "message", "Content-Type must be application/json"));
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized", "message", "Authorization header must start with Bearer"));
        }

        String apiKey = authorizationHeader.substring("Bearer ".length()).trim();

        String[] allowedKeys = allowedApiKeysString.split(",");
        boolean isValidKey = Arrays.stream(allowedKeys).map(String::trim).anyMatch(key -> key.equals(apiKey));

        if (!isValidKey) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Invalid API Key"));
        }

        if (!externalRateLimitService.tryConsume(apiKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too Many Requests", "message", "Rate limit exceeded"));
        }

        String email = HtmlUtils.htmlEscape(requestBody.getEmail().trim().toLowerCase()); 

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Bad Request", "message", "Invalid email format"));
        }

        try {
            boolean hasCertificate = turnAssignedService.hasHealthCertificateWithinLastYear(email);
            return ResponseEntity.ok(Map.of("hasHealthCertificate", hasCertificate));
        } catch (Exception e) {
            log.error("Error checking health certificate for email: {}", HtmlUtils.htmlEscape(email), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "message", "An error occurred while processing the request"));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((error) -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Bad Request", "message", "Validation failed", "details", errors));
    }
}