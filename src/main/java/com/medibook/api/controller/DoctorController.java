package com.medibook.api.controller;

import com.medibook.api.dto.Availability.*;
import com.medibook.api.dto.DoctorDTO;
import com.medibook.api.dto.DoctorMetricsDTO;
import com.medibook.api.dto.PatientDTO;
import com.medibook.api.dto.UpdateMedicalHistoryRequestDTO;
import com.medibook.api.dto.CreateMedicalHistoryRequestDTO;
import com.medibook.api.dto.UpdateMedicalHistoryContentRequestDTO;
import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.dto.TagFrequencyDTO;
import com.medibook.api.service.DoctorAvailabilityService;
import com.medibook.api.service.DoctorService;
import com.medibook.api.service.MedicalHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Slf4j
public class DoctorController {

    private final DoctorService doctorService;
    private final DoctorAvailabilityService availabilityService;
    private final MedicalHistoryService medicalHistoryService;

    @GetMapping
    public ResponseEntity<List<DoctorDTO>> getAllDoctors() {
        List<DoctorDTO> doctors = doctorService.getAllDoctors();
        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/specialty/{specialty}")
    public ResponseEntity<List<DoctorDTO>> getDoctorsBySpecialty(@PathVariable String specialty) {
        List<DoctorDTO> doctors = doctorService.getDoctorsBySpecialty(specialty);
        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/specialties")
    public ResponseEntity<List<String>> getAllSpecialties() {
        List<String> specialties = doctorService.getAllSpecialties();
        return ResponseEntity.ok(specialties);
    }

    @GetMapping("/{doctorId}/patients")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<List<PatientDTO>> getPatientsByDoctor(@PathVariable UUID doctorId) {
        List<PatientDTO> patients = doctorService.getPatientsByDoctor(doctorId);
        return ResponseEntity.ok(patients);
    }

    @PostMapping("/{doctorId}/availability")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<Void> saveAvailability(
            @PathVariable UUID doctorId,
            @Valid @RequestBody DoctorAvailabilityRequestDTO request) {
        
        availabilityService.saveAvailability(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{doctorId}/availability")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<DoctorAvailabilityResponseDTO> getAvailability(@PathVariable UUID doctorId) {
        DoctorAvailabilityResponseDTO availability = availabilityService.getAvailability(doctorId);
        return ResponseEntity.ok(availability);
    }

    @GetMapping("/{doctorId}/available-slots")
    public ResponseEntity<List<AvailableSlotDTO>> getAvailableSlots(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<AvailableSlotDTO> slots = availabilityService.getAvailableSlots(doctorId, fromDate, toDate);
        return ResponseEntity.ok(slots);
    }

    @PutMapping("/{doctorId}/patients/medical-history")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<Void> updatePatientMedicalHistory(
            @PathVariable UUID doctorId,
            @Valid @RequestBody UpdateMedicalHistoryRequestDTO request) {
        
        doctorService.updatePatientMedicalHistory(doctorId, request.getPatientId(), request.getTurnId(), request.getMedicalHistory());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{doctorId}/medical-history")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<MedicalHistoryDTO> addMedicalHistory(
            @PathVariable UUID doctorId,
            @Valid @RequestBody CreateMedicalHistoryRequestDTO request) {
        
    MedicalHistoryDTO medicalHistory = medicalHistoryService.addMedicalHistory(
        doctorId, request.getTurnId(), request.getContent(), request.getTags());
        return ResponseEntity.ok(medicalHistory);
    }

    @PutMapping("/{doctorId}/medical-history/{historyId}")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<MedicalHistoryDTO> updateMedicalHistory(
            @PathVariable UUID doctorId,
            @PathVariable UUID historyId,
            @Valid @RequestBody UpdateMedicalHistoryContentRequestDTO request) {
        
        MedicalHistoryDTO updatedHistory = medicalHistoryService.updateMedicalHistory(
                doctorId, historyId, request.getContent(), request.getTags());
        return ResponseEntity.ok(updatedHistory);
    }

    @GetMapping("/{doctorId}/medical-history")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<List<MedicalHistoryDTO>> getDoctorMedicalHistoryEntries(@PathVariable UUID doctorId) {
        List<MedicalHistoryDTO> histories = medicalHistoryService.getDoctorMedicalHistoryEntries(doctorId);
        return ResponseEntity.ok(histories);
    }

    @GetMapping("/{doctorId}/patients/{patientId}/medical-history")
    @PreAuthorize("(hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId) "
            + "and @medAuthz.canRead(authentication, #patientId)) or hasRole('ADMIN')")
    public ResponseEntity<List<MedicalHistoryDTO>> getPatientMedicalHistoryByDoctor(
            @PathVariable UUID doctorId,
            @PathVariable UUID patientId) {
        
        List<MedicalHistoryDTO> histories = medicalHistoryService.getPatientMedicalHistoryByDoctor(patientId, doctorId);
        return ResponseEntity.ok(histories);
    }

    /**
     * Per-patient tag frequencies scoped to the requesting doctor's own entries
     * (OQ-5). Coarse-gated to {@code DOCTOR} only; the fine-grained
     * {@code principal.id == doctorId} + {@code @medAuthz.canRead} ownership check
     * (and its id-only DENY audit) is enforced INSIDE the service (OQ-8a) — putting
     * the ownership SpEL in {@code @PreAuthorize} would throw before the service and
     * audit nothing.
     * <p>
     * DELIBERATE: the {@code hasRole('DOCTOR')}-only gate excludes ADMIN (→ 403) by
     * design and intentionally diverges from the sibling
     * {@link #getPatientMedicalHistoryByDoctor} (which allows ADMIN). Tag frequency is
     * a per-doctor clinical inference (OQ-5 per-doctor scoping); an admin has no "own
     * doctorId" to satisfy the service {@code isOwner} check, so admin is excluded on
     * purpose — a future refactor must NOT "harmonize" the two matchers.
     */
    @GetMapping("/{doctorId}/patients/{patientId}/tags")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<TagFrequencyDTO>> getPatientFrequentTags(
            @PathVariable UUID doctorId,
            @PathVariable UUID patientId,
            Authentication authentication) {

        List<TagFrequencyDTO> tags = medicalHistoryService.getFrequentTags(authentication, doctorId, patientId);
        return ResponseEntity.ok(tags);
    }

    @DeleteMapping("/{doctorId}/medical-history/{historyId}")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<Void> deleteMedicalHistory(
            @PathVariable UUID doctorId,
            @PathVariable UUID historyId) {
        
        medicalHistoryService.deleteMedicalHistory(doctorId, historyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{doctorId}/metrics")
    @PreAuthorize("hasRole('DOCTOR') and authentication.principal.id.equals(#doctorId)")
    public ResponseEntity<DoctorMetricsDTO> getDoctorMetrics(@PathVariable UUID doctorId) {
        DoctorMetricsDTO metrics = doctorService.getDoctorMetrics(doctorId);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Maps invalid tag input (server-side allowlist / blank rejection in
     * {@code MedicalHistoryService}) to HTTP 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}