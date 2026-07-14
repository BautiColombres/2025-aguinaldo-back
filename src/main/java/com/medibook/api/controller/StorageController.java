package com.medibook.api.controller;

import com.medibook.api.dto.ErrorResponseDTO;
import com.medibook.api.dto.Storage.StorageMessageDTO;
import com.medibook.api.service.SupabaseStorageService;
import com.medibook.api.service.TurnFileService;
import com.medibook.api.util.StorageSecurity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Slf4j
public class StorageController {

    private static final String BASE_PATH = "/api/storage";

    private final SupabaseStorageService supabaseStorageService;
    private final TurnFileService turnFileService;

    @PostMapping(value = "/upload-turn-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PATIENT') and @storageAuthz.canManageTurnFile(authentication, #turnId)")
    public ResponseEntity<?> uploadTurnFile(
            @RequestParam("turnId") UUID turnId,
            @RequestParam("file") MultipartFile file) {

        try {
            String result = turnFileService.uploadTurnFile(turnId, file).block();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (Exception error) {
            log.error("Error uploading turn file for turnId {}: {}", turnId, error.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST,
                    "No se pudo subir el archivo", BASE_PATH + "/upload-turn-file");
        }
    }

    @DeleteMapping("/delete-turn-file/{turnId}")
    @PreAuthorize("hasRole('PATIENT') and @storageAuthz.canManageTurnFile(authentication, #turnId)")
    public ResponseEntity<?> deleteTurnFile(@PathVariable UUID turnId) {
        String path = BASE_PATH + "/delete-turn-file/" + turnId;
        try {
            turnFileService.deleteTurnFile(turnId).block();
            return ResponseEntity.ok(new StorageMessageDTO("Archivo eliminado exitosamente"));
        } catch (Exception error) {
            String message = error.getMessage();
            log.error("Error deleting turn file for turnId {}: {}", turnId, message);
            if (message != null && message.contains("no encontrado")) {
                return errorResponse(HttpStatus.NOT_FOUND, "Archivo no encontrado", path);
            }
            if (message != null && message.contains("turno completado")) {
                return errorResponse(HttpStatus.BAD_REQUEST,
                        "No se puede eliminar el archivo de un turno completado", path);
            }
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo eliminar el archivo", path);
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadFile(
            @RequestParam("bucket") String bucketName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileName", required = false) String fileName) {

        String path = BASE_PATH + "/upload";
        String finalFileName = fileName != null ? fileName : file.getOriginalFilename();

        try {
            StorageSecurity.requireAllowedBucket(bucketName);
            StorageSecurity.validateFileName(finalFileName);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), path);
        }

        try {
            String publicUrl = supabaseStorageService.uploadFile(bucketName, finalFileName, file).block();
            return ResponseEntity.ok(new UrlResponse(publicUrl));
        } catch (Exception error) {
            log.error("Error uploading file: {}", error.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST, "No se pudo subir el archivo", path);
        }
    }

    @DeleteMapping("/delete/{bucketName}/{fileName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteFile(
            @PathVariable String bucketName,
            @PathVariable String fileName) {

        String path = BASE_PATH + "/delete/" + bucketName + "/" + fileName;
        try {
            StorageSecurity.requireAllowedBucket(bucketName);
            StorageSecurity.validateFileName(fileName);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), path);
        }

        try {
            supabaseStorageService.deleteFile(bucketName, fileName).block();
            return ResponseEntity.ok(new StorageMessageDTO("Archivo eliminado exitosamente"));
        } catch (Exception error) {
            log.error("Error deleting file: {}", error.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST, "No se pudo eliminar el archivo", path);
        }
    }

    @GetMapping("/url/{bucketName}/{fileName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPublicUrl(
            @PathVariable String bucketName,
            @PathVariable String fileName) {

        String path = BASE_PATH + "/url/" + bucketName + "/" + fileName;
        try {
            StorageSecurity.requireAllowedBucket(bucketName);
            StorageSecurity.validateFileName(fileName);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), path);
        }

        String publicUrl = supabaseStorageService.getPublicUrl(bucketName, fileName);
        return ResponseEntity.ok(new UrlResponse(publicUrl));
    }

    private static ResponseEntity<Object> errorResponse(HttpStatus status, String message, String path) {
        ErrorResponseDTO body = ErrorResponseDTO.of(
                status.getReasonPhrase().toUpperCase().replace(' ', '_'),
                message, status.value(), path);
        return ResponseEntity.status(status).body(body);
    }

    public record UrlResponse(String url) {
    }
}
