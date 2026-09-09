package com.vesit.openattend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One (student × subject) aggregation bucket returned by the grouped defaulter query.
 * Populated directly by a JPQL constructor expression — never persisted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaulterAggregateRow {

    private String studentId;
    private String rollNo;
    private String studentName;
    private String division;
    private String subjectCode;
    private String subjectName;

    /** Lectures the student was accountable for (PRESENT + ABSENT + DUTY_PRESENT; NA excluded). */
    private Long conducted;

    /** Lectures credited to the student: PRESENT plus approved-duty DUTY_PRESENT. */
    private Long attended;

    /** The DUTY_PRESENT share of {@link #attended} — lectures excused by an approved OD / medical leave. */
    private Long dutyPresent;
}
