package com.vesit.openattend.dto.notification;

/**
 * Conducted / attended counts for one student in one subject, counted by the rule on
 * {@link com.vesit.openattend.entity.enums.AttendanceStatus}. Summed across subjects it gives the
 * student's aggregate attendance.
 */
public record StudentSubjectTally(String studentId, String subjectId, Long conducted, Long attended) {
}
