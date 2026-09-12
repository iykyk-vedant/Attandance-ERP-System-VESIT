package com.vesit.openattend.exception;

/**
 * Raised when a faculty member submits attendance for a slot they are neither allocated to
 * nor delegated for. Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class MarkingNotAuthorizedException extends RuntimeException {

    public MarkingNotAuthorizedException(String message) {
        super(message);
    }
}
