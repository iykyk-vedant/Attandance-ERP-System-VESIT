package com.vesit.openattend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CorrectionWindowExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleCorrectionWindowExpired(CorrectionWindowExpiredException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ProxyDelegationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleProxyDelegationDenied(ProxyDelegationDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MarkingNotAuthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleMarkingNotAuthorized(MarkingNotAuthorizedException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LeaveRequestDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleLeaveRequestDenied(LeaveRequestDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** A proof scanned at full resolution is the usual cause, so the message says what to do about it. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE,
                "Attachment is too large. Scan the proof at a lower resolution and try again.");
    }

    /**
     * Covers both JSON bodies and the multipart leave form: {@code MethodArgumentNotValidException}
     * is a {@link BindException}, so one handler reports either the same way.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Request validation failed" : message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("status", status.value());
        payload.put("message", message != null ? message : status.getReasonPhrase());
        return ResponseEntity.status(status).body(payload);
    }
}
