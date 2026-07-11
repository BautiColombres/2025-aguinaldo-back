package com.medibook.api.service;

import com.medibook.api.dto.MedicalHistoryDTO;
import com.medibook.api.dto.TagFrequencyDTO;
import com.medibook.api.entity.MedicalHistory;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.MedicalHistoryRepository;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.security.MedicalHistoryAuthorization;
import com.medibook.api.util.LogMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
    private final AuditLogService auditLogService;
    private static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String RESOURCE_TYPE = "MEDICAL_HISTORY";
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 50;
    /**
     * Server-side character allowlist for tags (OQ-6): Unicode letters
     * (incl. accents / ñ), digits, spaces and hyphens. Anything else is rejected.
     */
    private static final Pattern TAG_ALLOWLIST = Pattern.compile("^[\\p{L}\\p{Nd} -]+$");

    @Transactional
    public MedicalHistoryDTO addMedicalHistory(UUID doctorId, UUID turnId, String content, List<String> tags) {
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
                .tags(normalizeTags(tags))
                .build();

        MedicalHistory savedHistory = medicalHistoryRepository.save(medicalHistory);
        log.info("Added medical history entry for turn {} (patient {} by doctor {})",
                LogMaskingUtil.maskId(turnId), LogMaskingUtil.maskId(patient.getId()), LogMaskingUtil.maskId(doctorId));

        auditLogService.record(AuditAction.CREATE, AuditOutcome.ALLOW,
                patient.getId(), RESOURCE_TYPE,
                savedHistory.getId() != null ? savedHistory.getId().toString() : null);

        badgeEvaluationTrigger.evaluateAfterMedicalHistoryDocumented(doctorId, content);

        return mapToDTO(savedHistory);
    }

    @Transactional
    public MedicalHistoryDTO updateMedicalHistory(UUID doctorId, UUID historyId, String content, List<String> tags) {
        MedicalHistory medicalHistory = medicalHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Medical history entry not found"));

        if (!medicalHistory.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("Doctor can only update their own medical history entries");
        }

        medicalHistory.setContent(content);
        medicalHistory.setTags(normalizeTags(tags));
        medicalHistory.setUpdatedAt(LocalDateTime.now(ARGENTINA_ZONE));

        MedicalHistory updatedHistory = medicalHistoryRepository.save(medicalHistory);
        log.info("Updated medical history entry {} by doctor {}",
                LogMaskingUtil.maskId(historyId), LogMaskingUtil.maskId(doctorId));

        auditLogService.record(AuditAction.UPDATE, AuditOutcome.ALLOW,
                updatedHistory.getPatient().getId(), RESOURCE_TYPE, historyId.toString());

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
            auditLogService.record(AuditAction.READ, AuditOutcome.DENY, patientId, RESOURCE_TYPE, null);
            throw new AccessDeniedException("Not authorized to read this patient's medical history");
        }
        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW, patientId, RESOURCE_TYPE, null);
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

        UUID patientId = medicalHistory.getPatient().getId();
        if (!medicalHistoryAuthorization.canRead(authentication, patientId)) {
            auditLogService.record(AuditAction.READ, AuditOutcome.DENY,
                    patientId, RESOURCE_TYPE, historyId.toString());
            throw new AccessDeniedException("Not authorized to read this medical history entry");
        }
        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW,
                patientId, RESOURCE_TYPE, historyId.toString());
        return mapToDTO(medicalHistory);
    }

    public List<MedicalHistoryDTO> getDoctorMedicalHistoryEntries(UUID doctorId) {
        return medicalHistoryRepository.findByDoctor_IdOrderByCreatedAtDesc(doctorId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }


    /**
     * PHI read reached from {@code DoctorController} at
     * {@code GET /{doctorId}/patients/{patientId}/medical-history} (authorization is
     * enforced upstream via {@code @medAuthz.canRead}). Records a READ/ALLOW audit
     * entry keyed on the subject patient id (id-only, no PHI content).
     */
    public List<MedicalHistoryDTO> getPatientMedicalHistoryByDoctor(UUID patientId, UUID doctorId) {
        List<MedicalHistoryDTO> histories = medicalHistoryRepository
                .findByPatient_IdAndDoctor_IdOrderByCreatedAtDesc(patientId, doctorId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW, patientId, RESOURCE_TYPE, null);
        return histories;
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

        UUID patientId = medicalHistory.getPatient().getId();
        medicalHistoryRepository.delete(medicalHistory);
        log.info("Deleted medical history entry {} by doctor {}",
                LogMaskingUtil.maskId(historyId), LogMaskingUtil.maskId(doctorId));

        auditLogService.record(AuditAction.DELETE, AuditOutcome.ALLOW,
                patientId, RESOURCE_TYPE, historyId.toString());
    }

    /**
     * Per-patient, per-requesting-doctor tag frequencies (OQ-5). Authorization is
     * enforced INSIDE the service (OQ-8a) so a DENY is audited: the caller must be
     * the doctor named in the path ({@code principal.id == doctorId}) AND have an
     * active relationship with the patient ({@code @medAuthz.canRead}). On denial a
     * READ/DENY audit row (id-only, no PHI) is written and access is refused; the
     * frequency query itself is scoped to this doctor's own entries only.
     */
    public List<TagFrequencyDTO> getFrequentTags(Authentication authentication, UUID doctorId, UUID patientId) {
        UUID principalId = (authentication != null
                && authentication.getPrincipal() instanceof User principal) ? principal.getId() : null;
        boolean isOwner = principalId != null && principalId.equals(doctorId);

        if (!isOwner || !medicalHistoryAuthorization.canRead(authentication, patientId)) {
            auditLogService.record(AuditAction.READ, AuditOutcome.DENY, patientId, RESOURCE_TYPE, null);
            throw new AccessDeniedException("Not authorized to read this patient's tags");
        }

        auditLogService.record(AuditAction.READ, AuditOutcome.ALLOW, patientId, RESOURCE_TYPE, null);

        return medicalHistoryRepository.findTagFrequencyByPatientAndDoctor(patientId, doctorId)
                .stream()
                .map(row -> TagFrequencyDTO.builder()
                        .tag((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Normalizes tags per OQ-6: trim, reject blanks, lowercase, dedupe (preserving
     * order), enforce the character allowlist and count/length caps. Rejections are
     * surfaced as {@link IllegalArgumentException} (mapped to HTTP 400 by the
     * controller). A {@code null} input yields an empty set.
     */
    private Set<String> normalizeTags(List<String> rawTags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawTags == null) {
            return normalized;
        }
        for (String raw : rawTags) {
            if (raw == null) {
                throw new IllegalArgumentException("Tags must not be blank");
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Tags must not be blank");
            }
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException("Each tag must be at most " + MAX_TAG_LENGTH + " characters");
            }
            if (!TAG_ALLOWLIST.matcher(trimmed).matches()) {
                throw new IllegalArgumentException(
                        "Tags may only contain letters, digits, spaces and hyphens");
            }
            normalized.add(trimmed.toLowerCase(Locale.ROOT));
        }
        if (normalized.size() > MAX_TAGS) {
            throw new IllegalArgumentException("A medical history entry can have at most " + MAX_TAGS + " tags");
        }
        return normalized;
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
                .tags(medicalHistory.getTags() != null
                        ? new ArrayList<>(medicalHistory.getTags())
                        : new ArrayList<>())
                .build();
    }
}