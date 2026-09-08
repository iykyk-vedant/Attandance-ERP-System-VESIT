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

    /** Lectures the student was actually accountable for (PRESENT + ABSENT; NA excluded). */
    private Long conducted;

    /** Lectures marked PRESENT. */
    private Long attended;
}
