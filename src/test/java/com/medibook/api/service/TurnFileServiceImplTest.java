package com.medibook.api.service;

import com.medibook.api.entity.TurnAssigned;
import com.medibook.api.entity.TurnFile;
import com.medibook.api.entity.User;
import com.medibook.api.model.AuditAction;
import com.medibook.api.model.AuditOutcome;
import com.medibook.api.repository.TurnAssignedRepository;
import com.medibook.api.repository.TurnFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnFileServiceImplTest {

    @Mock
    private TurnFileRepository turnFileRepository;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private TurnAssignedRepository turnAssignedRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BadgeEvaluationTriggerService badgeEvaluationTrigger;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MultipartFile file;

    @InjectMocks
    private TurnFileServiceImpl turnFileService;

    private UUID turnId;
    private User doctor;
    private User patient;
    private TurnAssigned turn;

    @BeforeEach
    void setUp() {
        turnId = UUID.randomUUID();
        
        doctor = new User();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Juan");
        doctor.setSurname("Pérez");
        doctor.setRole("DOCTOR");

        patient = new User();
        patient.setId(UUID.randomUUID());
        patient.setName("Ana");
        patient.setSurname("García");
        patient.setRole("PATIENT");

        turn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("ASSIGNED")
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void uploadTurnFile_Success() {
        String fileName = "test-file.pdf";
        String publicUrl = "https://storage.example.com/test-file.pdf";

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.just(publicUrl));
        when(turnAssignedRepository.findById(any(UUID.class))).thenReturn(Optional.of(turn));

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .assertNext(result -> {
                    assertTrue(result.contains("\"url\":\"" + publicUrl + "\""));
                    assertTrue(result.contains("\"fileName\":\""));
                })
                .verifyComplete();

        verify(turnFileRepository).save(any(TurnFile.class));
        verify(notificationService).createPatientFileUploadedNotification(
                eq(doctor.getId()), any(UUID.class), anyString(), anyString(), anyString(), eq(fileName));
        verify(auditLogService).record(any(), eq(AuditAction.CREATE), eq(AuditOutcome.ALLOW),
                eq(patient.getId()), eq("TURN_FILE"), eq(turnId.toString()));
    }

    @Test
    void uploadTurnFile_FileAlreadyExists_ThrowsException() {
        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(true);

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .expectErrorMatches(error -> error instanceof IllegalStateException && 
                        error.getMessage().contains("Ya existe un archivo para este turno"))
                .verify();

        verify(supabaseStorageService, never()).uploadFile(anyString(), anyString(), any());
        verify(turnFileRepository, never()).save(any());
    }

    @Test
    void uploadTurnFile_StorageFailure_PropagatesError() {
        String fileName = "test-file.pdf";
        RuntimeException storageError = new RuntimeException("Storage error");

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.error(storageError));

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .expectError(RuntimeException.class)
                .verify();

        verify(turnFileRepository, never()).save(any());
    }

    @Test
    void uploadTurnFile_NotificationFailure_DoesNotFailUpload() {
        String fileName = "test-file.pdf";
        String publicUrl = "https://storage.example.com/test-file.pdf";

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.just(publicUrl));
        when(turnAssignedRepository.findById(any(UUID.class))).thenReturn(Optional.of(turn));
        doThrow(new RuntimeException("Notification error"))
                .when(notificationService).createPatientFileUploadedNotification(
                        any(), any(), any(), any(), any(), any());

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .assertNext(result -> {
                    assertTrue(result.contains("\"url\":\"" + publicUrl + "\""));
                })
                .verifyComplete();

        verify(turnFileRepository).save(any(TurnFile.class));
    }

    @Test
    void deleteTurnFile_Success() {
        String fileName = "test-file.pdf";
        TurnFile turnFile = TurnFile.builder()
                .id(UUID.randomUUID())
                .turnId(turnId)
                .fileName(fileName)
                .fileUrl("https://storage.example.com/test-file.pdf")
                .build();

        when(turnFileRepository.findByTurnId(any(UUID.class))).thenReturn(Optional.of(turnFile));
        when(supabaseStorageService.deleteFile("archivosTurnos", fileName)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(turnFileService.deleteTurnFile(turnId))
                .verifyComplete();

        verify(turnFileRepository).deleteByTurnId(any(UUID.class));
    }

    @Test
    void deleteTurnFile_FileNotFound_ThrowsException() {
        when(turnFileRepository.findByTurnId(any(UUID.class))).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(turnFileService.deleteTurnFile(turnId))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException && 
                        error.getMessage().contains("Archivo no encontrado"))
                .verify();

        verify(supabaseStorageService, never()).deleteFile(anyString(), anyString());
        verify(turnFileRepository, never()).deleteByTurnId(any());
    }

    @Test
    void deleteTurnFile_StorageFailure_PropagatesError() {
        String fileName = "test-file.pdf";
        TurnFile turnFile = TurnFile.builder()
                .id(UUID.randomUUID())
                .turnId(turnId)
                .fileName(fileName)
                .fileUrl("https://storage.example.com/test-file.pdf")
                .build();

        RuntimeException storageError = new RuntimeException("Storage delete error");

        when(turnFileRepository.findByTurnId(any(UUID.class))).thenReturn(Optional.of(turnFile));
        when(supabaseStorageService.deleteFile("archivosTurnos", fileName)).thenReturn(Mono.error(storageError));

        // Act & Assert
        StepVerifier.create(turnFileService.deleteTurnFile(turnId))
                .expectError(RuntimeException.class)
                .verify();

        verify(turnFileRepository, never()).deleteByTurnId(any());
    }

    @Test
    void getTurnFileInfo_FileExists_ReturnsFile() {
        TurnFile expectedFile = TurnFile.builder()
                .id(UUID.randomUUID())
                .turnId(turnId)
                .fileName("test-file.pdf")
                .fileUrl("https://storage.example.com/test-file.pdf")
                .build();

        when(turnFileRepository.findByTurnId(any(UUID.class))).thenReturn(Optional.of(expectedFile));

        Optional<TurnFile> result = turnFileService.getTurnFileInfo(turnId);

        assertTrue(result.isPresent());
        assertEquals(expectedFile, result.get());
        verify(turnFileRepository).findByTurnId(any(UUID.class));
    }

    @Test
    void getTurnFileInfo_FileNotExists_ReturnsEmpty() {
        when(turnFileRepository.findByTurnId(any(UUID.class))).thenReturn(Optional.empty());

        Optional<TurnFile> result = turnFileService.getTurnFileInfo(turnId);

        assertFalse(result.isPresent());
        verify(turnFileRepository).findByTurnId(any(UUID.class));
    }

    @Test
    void fileExistsForTurn_FileExists_ReturnsTrue() {
        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(true);

        boolean result = turnFileService.fileExistsForTurn(turnId);

        assertTrue(result);
        verify(turnFileRepository).existsByTurnId(any(UUID.class));
    }

    @Test
    void fileExistsForTurn_FileNotExists_ReturnsFalse() {
        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);

        boolean result = turnFileService.fileExistsForTurn(turnId);

        assertFalse(result);
        verify(turnFileRepository).existsByTurnId(any(UUID.class));
    }

    @Test
    void uploadTurnFile_TurnNotFound_SkipsNotification() {
        String fileName = "test-file.pdf";
        String publicUrl = "https://storage.example.com/test-file.pdf";

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.just(publicUrl));
        when(turnAssignedRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .assertNext(result -> {
                    assertTrue(result.contains("\"url\":\"" + publicUrl + "\""));
                })
                .verifyComplete();

        verify(turnFileRepository).save(any(TurnFile.class));
        verify(notificationService, never()).createPatientFileUploadedNotification(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadTurnFile_TurnWithoutDoctor_SkipsNotification() {
        String fileName = "test-file.pdf";
        String publicUrl = "https://storage.example.com/test-file.pdf";
        
        TurnAssigned turnWithoutDoctor = TurnAssigned.builder()
                .id(turnId)
                .doctor(null)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("ASSIGNED")
                .build();

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.just(publicUrl));
        when(turnAssignedRepository.findById(any(UUID.class))).thenReturn(Optional.of(turnWithoutDoctor));

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .assertNext(result -> {
                    assertTrue(result.contains("\"url\":\"" + publicUrl + "\""));
                })
                .verifyComplete();

        verify(turnFileRepository).save(any(TurnFile.class));
        verify(notificationService, never()).createPatientFileUploadedNotification(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadTurnFile_FileNameWithSpecialCharacters_SanitizesFileName() {
        String originalFileName = "Estudio Médico - Análisis #1 (2024).pdf";
        String publicUrl = "https://storage.example.com/sanitized-file.pdf";

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(originalFileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.just(publicUrl));
        when(turnAssignedRepository.findById(any(UUID.class))).thenReturn(Optional.of(turn));

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .assertNext(result -> {
                    assertTrue(result.contains("\"url\":\"" + publicUrl + "\""));
                })
                .verifyComplete();

        // Verify that supabaseStorageService.uploadFile was called with a sanitized filename
        verify(supabaseStorageService).uploadFile(eq("archivosTurnos"), argThat(fileName -> {
            // El nombre del archivo debe empezar con el nombre sanitizado
            return fileName.startsWith("Estudio_Medico_Analisis_1_2024_.pdf_") && 
                   !fileName.contains(" ") && 
                   !fileName.contains("á") && 
                   !fileName.contains("é") && 
                   !fileName.contains("#") && 
                   !fileName.contains("(") && 
                   !fileName.contains(")");
        }), eq(file));

        verify(turnFileRepository).save(any(TurnFile.class));
    }

    @Test
    void uploadTurnFile_FileNameWithOnlySpecialCharacters_UsesDefaultName() {
        String originalFileName = "!@#$%^&*()";
        String publicUrl = "https://storage.example.com/default-file.pdf";

        when(turnFileRepository.existsByTurnId(any(UUID.class))).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(originalFileName);
        when(supabaseStorageService.uploadFile(eq("archivosTurnos"), anyString(), eq(file)))
                .thenReturn(Mono.just(publicUrl));
        when(turnAssignedRepository.findById(any(UUID.class))).thenReturn(Optional.of(turn));

        // Act & Assert
        StepVerifier.create(turnFileService.uploadTurnFile(turnId, file))
                .assertNext(result -> {
                    assertTrue(result.contains("\"url\":\"" + publicUrl + "\""));
                })
                .verifyComplete();

        // Verify that supabaseStorageService.uploadFile was called with a default filename
        verify(supabaseStorageService).uploadFile(eq("archivosTurnos"), argThat(fileName -> {
            return fileName.startsWith("archivo_sin_nombre_");
        }), eq(file));

        verify(turnFileRepository).save(any(TurnFile.class));
    }

    @Test
    void deleteTurnFile_CompletedTurn_ShouldThrowException() {
        TurnAssigned completedTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("COMPLETED")
                .build();

        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(completedTurn));

        // Act & Assert
        StepVerifier.create(turnFileService.deleteTurnFile(turnId))
                .expectErrorMatches(error -> {
                    return error instanceof IllegalStateException && 
                           error.getMessage().contains("turno completado");
                })
                .verify();

        verify(turnAssignedRepository).findById(turnId);
        verify(turnFileRepository, never()).findByTurnId(any());
        verify(supabaseStorageService, never()).deleteFile(any(), any());
    }

    @Test
    void deleteTurnFile_NonCompletedTurn_ShouldDeleteSuccessfully() {
        TurnAssigned scheduledTurn = TurnAssigned.builder()
                .id(turnId)
                .doctor(doctor)
                .patient(patient)
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .status("SCHEDULED")
                .build();

        TurnFile existingFile = TurnFile.builder()
                .turnId(turnId)
                .fileName("test-file.pdf")
                .fileUrl("https://example.com/test-file.pdf")
                .build();

        when(turnAssignedRepository.findById(turnId)).thenReturn(Optional.of(scheduledTurn));
        when(turnFileRepository.findByTurnId(turnId)).thenReturn(Optional.of(existingFile));
        when(supabaseStorageService.deleteFile("archivosTurnos", "test-file.pdf"))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(turnFileService.deleteTurnFile(turnId))
                .verifyComplete();

        verify(turnAssignedRepository).findById(turnId);
        verify(turnFileRepository).findByTurnId(turnId);
        verify(supabaseStorageService).deleteFile("archivosTurnos", "test-file.pdf");
        verify(turnFileRepository).deleteByTurnId(turnId);
        verify(auditLogService).record(any(), eq(AuditAction.DELETE), eq(AuditOutcome.ALLOW),
                eq(patient.getId()), eq("TURN_FILE"), eq(turnId.toString()));
    }
}