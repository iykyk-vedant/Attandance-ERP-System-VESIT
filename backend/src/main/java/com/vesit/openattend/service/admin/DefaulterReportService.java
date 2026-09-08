package com.vesit.openattend.service.admin;

import com.vesit.openattend.dto.report.*;
import com.vesit.openattend.repository.AttendanceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Builds the &lt; 75% defaulter list for a division over a date window.
 *
 * <p>All percentages are computed with {@link BigDecimal} at {@link #PERCENTAGE_SCALE} decimal
 * places and compared at that same scale, so the number a class teacher reads on the sheet is
 * exactly the number that decided defaulter status — 74.9% is flagged, 75.0% is not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaulterReportService {

    /** Percentages are rounded — and compared — at two decimal places. */
    public static final int PERCENTAGE_SCALE = 2;

    public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("75.00");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional(readOnly = true)
    public DefaulterReportResponse generate(String division, LocalDate startDate, LocalDate endDate, BigDecimal threshold) {
        BigDecimal effectiveThreshold = normaliseThreshold(threshold);
        String effectiveDivision = normaliseDivision(division);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate (" + startDate + ") must not be after endDate (" + endDate + ")");
        }

        List<DefaulterAggregateRow> rows =
                attendanceRecordRepository.aggregateDefaulterRows(effectiveDivision, startDate, endDate);

        // Query is ordered by roll number then subject code, so both maps stay in a stable order.
        Map<String, DefaulterRowDto> byStudent = new LinkedHashMap<>();
        Map<String, SubjectColumnDto> subjectColumns = new TreeMap<>();

        for (DefaulterAggregateRow row : rows) {
            int conducted = toInt(row.getConducted());
            int attended = toInt(row.getAttended());

            subjectColumns.putIfAbsent(row.getSubjectCode(),
                    SubjectColumnDto.builder().code(row.getSubjectCode()).name(row.getSubjectName()).build());

            DefaulterRowDto student = byStudent.computeIfAbsent(row.getStudentId(), id -> DefaulterRowDto.builder()
                    .studentId(id)
                    .rollNo(row.getRollNo())
                    .name(row.getStudentName())
                    .division(row.getDivision())
                    .subjects(new LinkedHashMap<>())
                    .build());

            student.setTotalConducted(student.getTotalConducted() + conducted);
            student.setTotalAttended(student.getTotalAttended() + attended);
            student.getSubjects().put(row.getSubjectCode(), SubjectBreakdownDto.builder()
                    .subjectCode(row.getSubjectCode())
                    .subjectName(row.getSubjectName())
                    .conducted(conducted)
                    .attended(attended)
                    .percentage(percentage(attended, conducted))
                    .build());
        }

        List<DefaulterRowDto> defaulters = new ArrayList<>();
        for (DefaulterRowDto student : byStudent.values()) {
            BigDecimal overall = percentage(student.getTotalAttended(), student.getTotalConducted());
            student.setOverallPercentage(overall);

            if (overall.compareTo(effectiveThreshold) < 0) {
                defaulters.add(student);
            }
        }

        // Worst first — a defaulter list is a triage list. Roll number breaks ties.
        defaulters.sort(Comparator
                .comparing(DefaulterRowDto::getOverallPercentage)
                .thenComparing(DefaulterRowDto::getRollNo, Comparator.nullsLast(String::compareTo)));

        log.info("Defaulter report: division={} window={}..{} threshold={} evaluated={} defaulters={}",
                effectiveDivision, startDate, endDate, effectiveThreshold, byStudent.size(), defaulters.size());

        return DefaulterReportResponse.builder()
                .success(true)
                .division(effectiveDivision)
                .startDate(startDate)
                .endDate(endDate)
                .threshold(effectiveThreshold)
                .generatedAt(LocalDateTime.now())
                .studentsEvaluated(byStudent.size())
                .defaulterCount(defaulters.size())
                .subjects(new ArrayList<>(subjectColumns.values()))
                .defaulters(defaulters)
                .build();
    }

    /**
     * Attendance percentage rounded HALF_UP. A student with no accountable lectures in the window
     * scores 0 conducted; they are reported as 0% but are filtered out upstream by the query's
     * HAVING clause, so they never appear as defaulters on an empty denominator.
     */
    private BigDecimal percentage(int attended, int conducted) {
        if (conducted <= 0) {
            return BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(attended)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(conducted), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normaliseThreshold(BigDecimal threshold) {
        BigDecimal value = threshold != null ? threshold : DEFAULT_THRESHOLD;
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("threshold must be between 0 and 100, got: " + value);
        }
        return value.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private String normaliseDivision(String division) {
        if (division == null || division.trim().isEmpty()) {
            return null; // all divisions
        }
        return division.trim().toUpperCase();
    }

    private int toInt(Long value) {
        return value != null ? value.intValue() : 0;
    }
}
