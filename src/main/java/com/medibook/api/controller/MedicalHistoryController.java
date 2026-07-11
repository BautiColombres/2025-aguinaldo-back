package com.medibook.api.controller;

import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.service.MedicalHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medical-history")
@RequiredArgsConstructor
@Slf4j
public class MedicalHistoryController {

    private final MedicalHistoryService medicalHistoryService;

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@medAuthz.canRead(authentication, #patientId)")
    public ResponseEntity<List<MedicalHistoryDTO>> getPatientMedicalHistory(
            @PathVariable UUID patientId,
            Authentication authentication) {
        // Service-layer enforcement as defense in depth (mirrors @PreAuthorize above).
        List<MedicalHistoryDTO> histories =
                medicalHistoryService.getPatientMedicalHistoryAuthorized(authentication, patientId);
        return ResponseEntity.ok(histories);
    }

    @GetMapping("/{historyId}")
    public ResponseEntity<MedicalHistoryDTO> getMedicalHistoryById(
            @PathVariable UUID historyId,
            Authentication authentication) {
        // Cannot resolve the patient relationship in SpEL before loading the entry,
        // so authorization is enforced inside the service (same @medAuthz read rules).
        MedicalHistoryDTO history = medicalHistoryService.getMedicalHistoryById(authentication, historyId);
        return ResponseEntity.ok(history);
    }
}
