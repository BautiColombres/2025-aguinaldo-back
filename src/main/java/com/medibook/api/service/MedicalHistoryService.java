package com.medibook.api.service;

import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.repository.MedicalHistoryRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.security.MedicalHistoryAuthorization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MedicalHistoryService {
    
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final TurnAssignedRepository turnAssignedRepository;
    private final BadgeEvaluationTriggerService badgeEvaluationTrigger;
    private final MedicalHistoryAuthorization medicalHistoryAuthorization;
    private static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Transactional
    public MedicalHistoryDTO addMedicalHistory(UUID doctorId, UUID turnId, String content) {
        TurnAssigned turn = turnAssignedRepository.findById(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found"));

        if (!turn.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Doctor can only add medical history for their own turns");
        }

        if (turn.getPatient() == null) {
            throw new RuntimeException("Turn must have an assigned patient before recording medical history");
        }

        User doctor = turn.getDoctor();
        if (!"DOCTOR".equals(doctor.getRole()) || !"ACTIVE".equals(doctor.getStatus())) {
            throw new RuntimeException("Invalid doctor or doctor is not active");
        }

        User patient = turn.getPatient();
        if (!"PATIENT".equals(patient.getRole()) || !"ACTIVE".equals(patient.getStatus())) {
            throw new RuntimeException("Invalid patient or patient is not active");
        }

        if (medicalHistoryRepository.existsByTurn_Id(turnId)) {
            throw new RuntimeException("Medical history already exists for this turn");
        }

        MedicalHistory medicalHistory = MedicalHistory.builder()
                .patient(patient)
                .doctor(doctor)
                .turn(turn)
                .content(content)
                .build();

        MedicalHistory savedHistory = medicalHistoryRepository.save(medicalHistory);
        log.info("Added medical history entry for turn {} (patient {} by doctor {})", turnId, patient.getId(), doctorId);

        badgeEvaluationTrigger.evaluateAfterMedicalHistoryDocumented(doctorId, content);

        return mapToDTO(savedHistory);
    }

    @Transactional
    public MedicalHistoryDTO updateMedicalHistory(UUID doctorId, UUID historyId, String content) {
        MedicalHistory medicalHistory = medicalHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Medical history entry not found"));

        if (!medicalHistory.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Doctor can only update their own medical history entries");
        }

        medicalHistory.setContent(content);
        medicalHistory.setUpdatedAt(LocalDateTime.now(ARGENTINA_ZONE));

        MedicalHistory updatedHistory = medicalHistoryRepository.save(medicalHistory);
        log.info("Updated medical history entry {} by doctor {}", historyId, doctorId);
        
        badgeEvaluationTrigger.evaluateAfterMedicalHistoryDocumented(doctorId, content);
        
        return mapToDTO(updatedHistory);
    }

    public List<MedicalHistoryDTO> getPatientMedicalHistory(UUID patientId) {
        return medicalHistoryRepository.findByPatient_IdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Authorization-enforced read of a patient's medical history (defense in depth,
     * mirrors the controller {@code @PreAuthorize("@medAuthz.canRead(...)")} check).
     */
    public List<MedicalHistoryDTO> getPatientMedicalHistoryAuthorized(Authentication authentication, UUID patientId) {
        if (!medicalHistoryAuthorization.canRead(authentication, patientId)) {
            throw new AccessDeniedException("Not authorized to read this patient's medical history");
        }
        return getPatientMedicalHistory(patientId);
    }

    /**
     * Authorization-enforced lookup of a single medical history entry by id.
     * Loads the entry first so the patient relationship can be evaluated, then
     * applies the same {@code @medAuthz} read rules.
     */
    public MedicalHistoryDTO getMedicalHistoryById(Authentication authentication, UUID historyId) {
        MedicalHistory medicalHistory = medicalHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Medical history entry not found"));

        if (!medicalHistoryAuthorization.canRead(authentication, medicalHistory.getPatient().getId())) {
            throw new AccessDeniedException("Not authorized to read this medical history entry");
        }
        return mapToDTO(medicalHistory);
    }

    public List<MedicalHistoryDTO> getDoctorMedicalHistoryEntries(UUID doctorId) {
        return medicalHistoryRepository.findByDoctor_IdOrderByCreatedAtDesc(doctorId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }


    public List<MedicalHistoryDTO> getPatientMedicalHistoryByDoctor(UUID patientId, UUID doctorId) {
        return medicalHistoryRepository.findByPatient_IdAndDoctor_IdOrderByCreatedAtDesc(patientId, doctorId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public String getLatestMedicalHistoryContent(UUID patientId) {
        MedicalHistory latestHistory = medicalHistoryRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patientId);
        return latestHistory != null ? latestHistory.getContent() : null;
    }

    public Map<UUID, String> getLatestMedicalHistoryContents(List<UUID> patientIds) {
        List<Object[]> results = medicalHistoryRepository.findLatestContentsByPatientIds(patientIds);
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (String) row[1]
                ));
    }

    @Transactional
    public void deleteMedicalHistory(UUID doctorId, UUID historyId) {
        MedicalHistory medicalHistory = medicalHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Medical history entry not found"));

        if (!medicalHistory.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Doctor can only delete their own medical history entries");
        }

        medicalHistoryRepository.delete(medicalHistory);
        log.info("Deleted medical history entry {} by doctor {}", historyId, doctorId);
    }

    private MedicalHistoryDTO mapToDTO(MedicalHistory medicalHistory) {
        return MedicalHistoryDTO.builder()
                .id(medicalHistory.getId())
                .content(medicalHistory.getContent())
                .createdAt(medicalHistory.getCreatedAt())
                .updatedAt(medicalHistory.getUpdatedAt())
                .patientId(medicalHistory.getPatient().getId())
                .patientName(medicalHistory.getPatient().getName())
                .patientSurname(medicalHistory.getPatient().getSurname())
                .doctorId(medicalHistory.getDoctor().getId())
                .doctorName(medicalHistory.getDoctor().getName())
                .doctorSurname(medicalHistory.getDoctor().getSurname())
        .turnId(medicalHistory.getTurn() != null ? medicalHistory.getTurn().getId() : null)
                .build();
    }
}