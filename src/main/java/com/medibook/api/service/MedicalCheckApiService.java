package com.medibook.api.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MedicalCheckApiService {

    @Value("${medical.check.api.url:https://mock-pid-api.onrender.com}")
    private String apiBaseUrl;

    @Value("${medical.check.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public MedicalCheckApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fail-fast validation: the external medical-check API key must be configured
     * (provided via the gitignored secrets file / environment). It must never
     * silently fall back to an empty or committed default.
     */
    @PostConstruct
    void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "medical.check.api.key must be configured. Set the MEDICAL_CHECK_API_KEY environment variable "
                    + "(see application-secrets.properties.example).");
        }
    }

    /**
     * Check if user exists in the external system
     * @param email Patient email
     * @return true if user exists, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean isUser(String email) {
        try {
            String url = apiBaseUrl + "/isUser";
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("mail", email);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                (Class<Map<String, Object>>)(Class<?>)Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object existsValue = response.getBody().get("isUser");
                if (existsValue instanceof Boolean) {
                    return Boolean.TRUE.equals(existsValue);
                }
                // Fail-closed: absent or malformed "isUser" is treated as "not a user".
                log.warn("Malformed medical-check response for email {}: missing/invalid 'isUser' field", email);
                return false;
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking if user exists for email: {}", email, e);
            return false;
        }
    }

    /**
     * Register that a user has completed a medical check (apto físico)
     * @param email Patient email
     * @param hasMedicalCheck true to register completion
     * @return true if successfully registered, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean registerMedicalCheck(String email, boolean hasMedicalCheck) {
        try {
            String url = apiBaseUrl + "/medical-check";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("email", email);
            requestBody.put("medicalCheck", hasMedicalCheck);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                (Class<Map<String, Object>>)(Class<?>)Map.class
            );

            boolean success = response.getStatusCode() == HttpStatus.OK;
            if (success) {
                log.info("Successfully registered medical check for email: {}", email);
            } else {
                log.warn("Failed to register medical check for email: {}. Status: {}", email, response.getStatusCode());
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error registering medical check for email: {}", email, e);
            return false;
        }
    }

    /**
     * Process medical check completion for a turn
     * Checks if user exists and registers medical check if applicable
     * @param email Patient email
     */
    public void processMedicalCheckCompletion(String email) {
        try {
            log.info("Processing medical check completion for email: {}", email);
            
            // First check if user exists in the external system
            if (isUser(email)) {
                log.info("User exists in external system, registering medical check");
                // Register that the user has completed their medical check
                registerMedicalCheck(email, true);
            } else {
                log.info("User does not exist in external system, skipping medical check registration");
            }
        } catch (Exception e) {
            log.error("Error processing medical check completion for email: {}", email, e);
        }
    }
}
