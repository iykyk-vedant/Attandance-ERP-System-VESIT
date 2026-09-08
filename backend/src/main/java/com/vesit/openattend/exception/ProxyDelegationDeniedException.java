package com.vesit.openattend.exception;

/**
 * Raised when a caller tries to delegate or revoke a lecture they do not own — only the
 * allocated subject teacher, the class teacher or an admin may hand a slot to a substitute.
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class ProxyDelegationDeniedException extends RuntimeException {

    public ProxyDelegationDeniedException(String message) {
        super(message);
    }
}
