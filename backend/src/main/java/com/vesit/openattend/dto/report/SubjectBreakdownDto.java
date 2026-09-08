package com.vesit.openattend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Per-subject attendance for a single student within the reporting window. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectBreakdownDto {
    private String subjectCode;
    private String subjectName;
    private int conducted;
    private int attended;
    private BigDecimal percentage;
}
