package com.vesit.openattend.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Metadata for an attached proof. The bytes are fetched separately from {@link #downloadUrl}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveProofDto {
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String downloadUrl;
}
