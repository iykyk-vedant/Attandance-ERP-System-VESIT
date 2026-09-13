package com.vesit.openattend.dto.faculty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Audit view of a delegation — carries both the original subject teacher and the substitute. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyAssignmentDto {
    private String id;
    private String subjectId;
    private String subjectCode;
    private String subjectName;
    private String division;
    private String batch;
    private String lectureDate;
    private Integer sessionIndex;
    private String originalFacultyEmail;
    private String proxyFacultyEmail;
    private String assignedBy;
    private String reason;
    private String status;
    private String revokedBy;
    private String revokedAt;
    private String createdAt;
}
