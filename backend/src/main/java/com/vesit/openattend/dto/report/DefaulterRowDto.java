package com.vesit.openattend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** One student's row in the defaulter list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaulterRowDto {

    private String studentId;
    private String rollNo;
    private String name;
    private String division;
    private int totalConducted;
    private int totalAttended;
    private BigDecimal overallPercentage;

    /** Subject code -> breakdown. Absent key means the student is not enrolled in that subject. */
    @Builder.Default
    private Map<String, SubjectBreakdownDto> subjects = new LinkedHashMap<>();
}
