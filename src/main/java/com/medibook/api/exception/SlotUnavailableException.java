package com.medibook.api.exception;

/**
 * Thrown when a target appointment slot (doctor + datetime) is already taken.
 * Mapped to HTTP 409 Conflict by the controllers.
 */
public class SlotUnavailableException extends RuntimeException {
    public SlotUnavailableException(String message) {
        super(message);
    }
}
