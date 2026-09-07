package com.vesit.openattend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Full defaulter report; also the source object for the CSV and XLSX renderers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaulterReportResponse {

    @Builder.Default
    private boolean success = true;

    private String division;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal threshold;
    private LocalDateTime generatedAt;

    /** Students with at least one accountable lecture in the window. */
    private int studentsEvaluated;

    private int defaulterCount;

    /** Subject columns spanning the whole cohort, in export order. */
    @Builder.Default
    private List<SubjectColumnDto> subjects = new ArrayList<>();

    /** Defaulters only, worst percentage first. */
    @Builder.Default
    private List<DefaulterRowDto> defaulters = new ArrayList<>();
}
