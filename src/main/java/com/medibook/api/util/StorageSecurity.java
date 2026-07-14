package com.medibook.api.util;

import java.util.Set;

/**
 * Single source of truth for storage hardening:
 * <ul>
 *   <li>fixed bucket allowlist — no arbitrary cross-tenant buckets;</li>
 *   <li>filename sanitization — reject path traversal ({@code ../}, {@code ..\}),
 *       path separators, encoded traversal and control characters.</li>
 * </ul>
 */
public final class StorageSecurity {

    /** The only bucket the application is allowed to touch. */
    public static final String TURN_FILES_BUCKET = "archivosTurnos";

    private static final Set<String> ALLOWED_BUCKETS = Set.of(TURN_FILES_BUCKET);

    private StorageSecurity() {
    }

    public static boolean isAllowedBucket(String bucket) {
        return bucket != null && ALLOWED_BUCKETS.contains(bucket);
    }

    /**
     * Validates a storage object name against path-traversal / injection.
     *
     * @throws IllegalArgumentException when the name is blank, contains path
     *                                  separators, traversal sequences, encoded
     *                                  traversal, or control characters.
     */
    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }

        String lower = fileName.toLowerCase();

        // Encoded traversal / encoded separators.
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }

        // Literal separators and traversal.
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }

        // Control characters (incl. NUL, newlines, tabs, etc.).
        for (int i = 0; i < fileName.length(); i++) {
            if (Character.isISOControl(fileName.charAt(i))) {
                throw new IllegalArgumentException("Nombre de archivo inválido");
            }
        }
    }

    public static void requireAllowedBucket(String bucket) {
        if (!isAllowedBucket(bucket)) {
            throw new IllegalArgumentException("Bucket no permitido");
        }
    }
}
