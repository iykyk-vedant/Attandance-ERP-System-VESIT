package com.vesit.openattend.dto.faculty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureSessionResponse {
    private String id;
    private String subjectId;
    private String subjectCode;
    private String subjectName;
    private String division;
    private String batch;
    private String sessionDate;
    private Integer sessionIndex;
    private String sessionType;
    private String topicCovered;
    private Integer totalStudents;
    private Integer presentCount;
    private Integer absentCount;
    @Builder.Default
    private List<String> absentStudentIds = new ArrayList<>();
    private String submittedAt;
    private String lastModifiedAt;
    private boolean canCorrect;
    private String correctionReason;

    /** Proxy audit trail: who took the lecture, whether as a substitute, and for whom. */
    private String takenByFacultyEmail;
    private boolean proxy;
    private String originalFacultyEmail;
    private String proxyAssignmentId;
    private String proxyReason;
}
