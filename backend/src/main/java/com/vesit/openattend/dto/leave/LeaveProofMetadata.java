package com.vesit.openattend.dto.leave;

/**
 * Proof details without the file bytes, so the approval queue can show what is attached to each
 * request without loading every attachment.
 */
public record LeaveProofMetadata(
        String leaveRequestId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String sha256
) {
}
