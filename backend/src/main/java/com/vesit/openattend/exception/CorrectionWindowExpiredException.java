package com.vesit.openattend.exception;

/**
 * Raised when a caller without unlock privileges attempts to correct a lecture
 * session whose 48-hour correction window has already elapsed.
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class CorrectionWindowExpiredException extends RuntimeException {

    public CorrectionWindowExpiredException(String message) {
        super(message);
    }
}
