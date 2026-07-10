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
import reactor.core.scheduler.Schedulers;

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
    private final TurnFilePersister turnFilePersister;

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
        // BBUG-H4 rec #2: use a random UUID (not System.currentTimeMillis()) as the key
        // suffix so two concurrent uploads for the same turn+filename in the same
        // millisecond cannot collide on the storage key. A collision would let a loser's
        // compensating delete remove a winner's committed object.
        String customFileName = sanitizedOriginalName + "_" + turnId + "_" + UUID.randomUUID();
        log.info("Generated filename: {} for turnId: {}", customFileName, turnId);

        // BBUG-H4: The storage upload is reactive. The DB persistence is BLOCKING JPA and
        // must (1) run off the reactive event-loop thread, on a bounded scheduler, and
        // (2) happen inside a real @Transactional boundary (via the TurnFilePersister proxy
        // bean — a self-invocation would run with no transaction at all). If the DB write
        // fails AFTER the storage upload succeeded, we compensate by deleting the just
        // uploaded (now-orphaned) file from storage, then propagate the original error, so
        // storage and DB never diverge.
        return supabaseStorageService.uploadFile(BUCKET_NAME, customFileName, file)
                .flatMap(publicUrl -> persistWithCompensation(turnId, publicUrl, customFileName))
                .flatMap(publicUrl -> Mono
                        .fromCallable(() -> recordSideEffectsAndBuildResponse(turnId, actor, file, publicUrl, customFileName))
                        .subscribeOn(Schedulers.boundedElastic()))
                .doOnError(error -> log.error("Error uploading turn file for turnId {}: {}", turnId, error.getMessage()));
    }

    /**
     * Persists the {@link TurnFile} row on a bounded scheduler inside the
     * {@link TurnFilePersister} transactional boundary. On DB failure it performs a
     * compensating storage delete of the just-uploaded file and re-propagates the error.
     */
    private Mono<String> persistWithCompensation(UUID turnId, String publicUrl, String customFileName) {
        return Mono.fromCallable(() -> {
                    TurnFile turnFile = TurnFile.builder()
                            .turnId(turnId)
                            .fileUrl(publicUrl)
                            .fileName(customFileName)
                            .build();
                    turnFilePersister.persist(turnFile);
                    log.info("File upload persisted successfully for turnId: {}", turnId);
                    return publicUrl;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(dbError -> {
                    log.error("DB persistence FAILED after storage upload for turnId {}: {}. "
                                    + "Compensating by deleting orphaned storage object {}.",
                            turnId, dbError.getMessage(), customFileName);
                    return supabaseStorageService.deleteFile(BUCKET_NAME, customFileName)
                            .onErrorResume(cleanupError -> {
                                log.error("Compensating storage delete FAILED for file {} (turnId {}): {}. "
                                                + "File may be orphaned in storage.",
                                        customFileName, turnId, cleanupError.getMessage());
                                return Mono.empty();
                            })
                            .then(Mono.error(dbError));
                });
    }

    /**
     * Best-effort side effects (audit + doctor notification + badge evaluation) after a
     * successful persist. A failure here does NOT trigger the compensating storage delete
     * (the file row is already committed and consistent). Runs on a bounded scheduler.
     */
    private String recordSideEffectsAndBuildResponse(UUID turnId, User actor, MultipartFile file,
                                                     String publicUrl, String customFileName) {
        Optional<TurnAssigned> auditTurnOpt = turnAssignedRepository.findById(turnId);
        UUID subjectPatientId = auditTurnOpt
                .map(TurnAssigned::getPatient)
                .map(User::getId)
                .orElse(null);
        // BBUG-H4 rec #1: the file + row are already committed at this point. Wrap the
        // audit record in a best-effort try/catch (matching the notification block below)
        // so a post-commit audit hiccup is never reported to the client as an upload
        // failure. (AuditLogService.record already swallows internally; this is
        // belt-and-suspenders to remove any perception divergence.)
        try {
            auditLogService.record(actor, AuditAction.CREATE, AuditOutcome.ALLOW,
                    subjectPatientId, RESOURCE_TYPE, turnId.toString());
        } catch (Exception e) {
            log.error("Error recording audit for file upload (turnId {}): {}", turnId, e.getMessage());
        }

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