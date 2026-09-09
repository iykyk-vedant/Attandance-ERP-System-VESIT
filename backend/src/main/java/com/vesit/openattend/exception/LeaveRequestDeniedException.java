package com.vesit.openattend.exception;

/** Raised when a caller may not see or decide the leave request they asked for. */
public class LeaveRequestDeniedException extends RuntimeException {
    public LeaveRequestDeniedException(String message) {
        super(message);
    }
}
