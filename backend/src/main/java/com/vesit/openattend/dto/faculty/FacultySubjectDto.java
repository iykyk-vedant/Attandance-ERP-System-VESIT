package com.vesit.openattend.dto.faculty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultySubjectDto {
    private String subjectId;
    private String subjectCode;
    private String subjectName;
    private String division;
    private String batch;
    private int totalStudents;
    private String lastSessionDate;

    /** Set when the entry is a lecture delegated to this faculty as a substitute. */
    private boolean proxy;
    private String proxyAssignmentId;
    private String proxyForFacultyEmail;
    private Integer proxySessionIndex;
    private String proxyReason;
}
