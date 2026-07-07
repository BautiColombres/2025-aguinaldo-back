package com.medibook.api.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.TurnFile;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.TurnFileRepository;
import com.medibook.api.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurnFileServiceImpl implements TurnFileService {

    private final TurnFileRepository turnFileRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final TurnAssignedRepository turnAssignedRepository;
    private final NotificationService notificationService;
    private final BadgeEvaluationTriggerService badgeEvaluationTrigger;
    private final AuditLogService auditLogService;

    private static final String BUCKET_NAME = "archivosTurnos";
    private static final String RESOURCE_TYPE = "TURN_FILE";

    @Override
    public Mono<String> uploadTurnFile(UUID turnId, MultipartFile file) {
        log.info("Starting upload process for turnId: {}", turnId);

        // Capture the actor on the request thread before the reactive chain runs
        // (the security context may not be propagated onto reactive scheduler threads).
        final User actor = currentActor();

        if (turnFileRepository.existsByTurnId(turnId)) {
            return Mono.error(new IllegalStateException("Ya existe un archivo para este turno. Elimínalo antes de subir uno nuevo."));
        }

        String sanitizedOriginalName = sanitizeFileName(file.getOriginalFilename());
        String customFileName = sanitizedOriginalName + "_" + turnId + "_" + System.currentTimeMillis();
        log.info("Generated filename: {} for turnId: {}", customFileName, turnId);

        return supabaseStorageService.uploadFile(BUCKET_NAME, customFileName, file)
                .map(publicUrl -> {
                    TurnFile turnFile = TurnFile.builder()
                            .turnId(turnId)
                            .fileUrl(publicUrl)
                            .fileName(customFileName)
                            .build();
                    
                    turnFileRepository.save(turnFile);
                    log.info("File upload completed successfully for turnId: {}", turnId);

                    Optional<TurnAssigned> auditTurnOpt = turnAssignedRepository.findById(turnId);
                    UUID subjectPatientId = auditTurnOpt
                            .map(TurnAssigned::getPatient)
                            .map(User::getId)
                            .orElse(null);
                    auditLogService.record(actor, AuditAction.CREATE, AuditOutcome.ALLOW,
                            subjectPatientId, RESOURCE_TYPE, turnId.toString());

                    try {
                        Optional<TurnAssigned> turnOpt = turnAssignedRepository.findById(turnId);
                        if (turnOpt.isPresent()) {
                            TurnAssigned turn = turnOpt.get();
                            if (turn.getDoctor() != null && turn.getPatient() != null) {
                                String appointmentDate = DateTimeUtils.formatDate(turn.getScheduledAt());
                                String appointmentTime = DateTimeUtils.formatTime(turn.getScheduledAt());
                                String patientName = turn.getPatient().getName() + " " + turn.getPatient().getSurname();
                                
                                notificationService.createPatientFileUploadedNotification(
                                    turn.getDoctor().getId(),
                                    turnId,
                                    patientName,
                                    appointmentDate,
                                    appointmentTime,
                                    file.getOriginalFilename()
                                );
                                
                                log.info("Notification created for doctor {} about file upload by patient {}", 
                                        turn.getDoctor().getId(), turn.getPatient().getId());

                                badgeEvaluationTrigger.evaluateAfterFileUploaded(turn.getPatient().getId());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error creating notification for file upload: {}", e.getMessage());
                    }
                    
                    // BSEC-H-2: build JSON via Jackson so quotes in values are escaped
                    // (a raw quote in publicUrl/fileName previously produced malformed JSON).
                    ObjectNode json = JsonNodeFactory.instance.objectNode();
                    json.put("url", publicUrl);
                    json.put("fileName", customFileName);
                    return json.toString();
                })
                .doOnError(error -> log.error("Error uploading turn file for turnId {}: {}", turnId, error.getMessage()));
    }

    @Override
    public Mono<Void> deleteTurnFile(UUID turnId) {
        log.info("Starting delete process for turnId: {}", turnId);

        // Capture the actor on the request thread before the reactive chain runs.
        final User actor = currentActor();

        return Mono.fromCallable(() -> {
            Optional<TurnAssigned> turnOpt = turnAssignedRepository.findById(turnId);
            UUID subjectPatientId = null;
            if (turnOpt.isPresent()) {
                TurnAssigned turn = turnOpt.get();
                if ("COMPLETED".equals(turn.getStatus())) {
                    throw new IllegalStateException("No se puede eliminar el archivo de un turno completado");
                }
                if (turn.getPatient() != null) {
                    subjectPatientId = turn.getPatient().getId();
                }
            }

            return new DeleteContext(subjectPatientId, turnFileRepository.findByTurnId(turnId));
        })
                .flatMap(ctx -> {
                    if (ctx.file().isEmpty()) {
                        log.warn("No file found in database for turnId: {}", turnId);
                        return Mono.error(new IllegalArgumentException("Archivo no encontrado"));
                    }

                    TurnFile turnFile = ctx.file().get();
                    String fileName = turnFile.getFileName();
                    log.info("Found file in database: {} for turnId: {}", fileName, turnId);

                    return supabaseStorageService.deleteFile(BUCKET_NAME, fileName)
                            .then(Mono.fromRunnable(() -> {
                                log.info("File {} deleted successfully from Supabase, now deleting from database", fileName);
                                turnFileRepository.deleteByTurnId(turnId);
                                log.info("Database record deleted successfully for turnId: {}", turnId);
                                auditLogService.record(actor, AuditAction.DELETE, AuditOutcome.ALLOW,
                                        ctx.subjectPatientId(), RESOURCE_TYPE, turnId.toString());
                            }));
                })
                .then()
                .doOnError(error -> log.error("Error deleting turn file for turnId {}: {}", turnId, error.getMessage()));
    }

    /** Carries the resolved subject patient id alongside the file lookup through the delete chain. */
    private record DeleteContext(UUID subjectPatientId, Optional<TurnFile> file) {
    }

    @Override
    public Optional<TurnFile> getTurnFileInfo(UUID turnId) {
        return turnFileRepository.findByTurnId(turnId);
    }

    @Override
    public boolean fileExistsForTurn(UUID turnId) {
        return turnFileRepository.existsByTurnId(turnId);
    }

    private User currentActor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                return user;
            }
        } catch (Exception e) {
            log.debug("Could not resolve audit actor from security context: {}", e.getMessage());
        }
        return null;
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null) {
            return "archivo_sin_nombre.bin";
        }
        
        String normalized = java.text.Normalizer.normalize(originalFileName, java.text.Normalizer.Form.NFD);
        
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        
        String sanitized = normalized.replaceAll("[^a-zA-Z0-9._]", "_");
        
        sanitized = sanitized.replaceAll("_{2,}", "_");
        
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        if (sanitized.isEmpty()) {
            sanitized = "archivo_sin_nombre";
        }
        
        return sanitized;
    }
}