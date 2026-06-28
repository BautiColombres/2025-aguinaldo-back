package com.medibook.api.dto.Storage;

/**
 * Generic success/message response for storage operations.
 * Replaces hand-built JSON strings (BSEC-H-2: malformed JSON / error reflection).
 */
public record StorageMessageDTO(String message) {
}
