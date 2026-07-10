package com.medibook.api.util;

/**
 * BSEC-L-4: masking helpers so operational logs never leak PII/PHI.
 * <ul>
 *   <li>{@link #maskEmail(String)} keeps the first local-part char + full domain
 *       (e.g. {@code alice@domain.com} → {@code a***@domain.com}).</li>
 *   <li>{@link #maskId(Object)} keeps only the last 4 chars of an identifier
 *       (enough to correlate log lines without exposing the full id).</li>
 * </ul>
 * All methods are null-safe: a {@code null} input returns {@code null}.
 *
 * <p>Output is also stripped of CR/LF and other ISO control characters so a crafted
 * value (e.g. {@code a@x.com\nFAKE INFO ...}) cannot inject newlines into the log and
 * forge additional log lines (log forging / CWE-117).
 */
public final class LogMaskingUtil {

    private static final String GENERIC_MASK = "***";

    private LogMaskingUtil() {
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = stripControlChars(email.trim());
        int at = trimmed.indexOf('@');
        // Reject blank, missing '@', or empty local part — nothing safe to keep.
        if (at <= 0) {
            return GENERIC_MASK;
        }
        char firstChar = trimmed.charAt(0);
        String domain = trimmed.substring(at); // includes the '@'
        return firstChar + GENERIC_MASK + domain;
    }

    public static String maskId(Object id) {
        if (id == null) {
            return null;
        }
        String value = stripControlChars(id.toString());
        if (value.length() <= 4) {
            return "****";
        }
        return GENERIC_MASK + value.substring(value.length() - 4);
    }

    /**
     * Removes CR, LF and any other ISO control characters to prevent log forging
     * (CWE-117) when the masked value is written to a log line.
     */
    private static String stripControlChars(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
