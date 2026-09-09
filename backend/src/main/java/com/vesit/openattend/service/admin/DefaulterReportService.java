package com.vesit.openattend.service.admin;

import com.vesit.openattend.dto.report.*;
import com.vesit.openattend.repository.AttendanceRecordRepository;
import com.vesit.openattend.service.attendance.AttendancePercentage;
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
 * <p>Counting and rounding both come from {@link AttendancePercentage}, so an approved OD or
 * medical leave lifts a student off this list by exactly the arithmetic the student dashboard
 * shows them — and re-running the report after an approval is all the "recalculation" the
 * defaulter list needs, because it is derived, never stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaulterReportService {

    /** Percentages are rounded — and compared — at two decimal places. */
    public static final int PERCENTAGE_SCALE = AttendancePercentage.SCALE;

    public static final BigDecimal DEFAULT_THRESHOLD = AttendancePercentage.DEFAULT_THRESHOLD;

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
            int duty = toInt(row.getDutyPresent());

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
            student.setTotalDutyPresent(student.getTotalDutyPresent() + duty);
            student.getSubjects().put(row.getSubjectCode(), SubjectBreakdownDto.builder()
                    .subjectCode(row.getSubjectCode())
                    .subjectName(row.getSubjectName())
                    .conducted(conducted)
                    .attended(attended)
                    .dutyPresent(duty)
                    .percentage(AttendancePercentage.of(attended, conducted))
                    .build());
        }

        List<DefaulterRowDto> defaulters = new ArrayList<>();
        for (DefaulterRowDto student : byStudent.values()) {
            BigDecimal overall = AttendancePercentage.of(student.getTotalAttended(), student.getTotalConducted());
            student.setOverallPercentage(overall);

            if (AttendancePercentage.isDefaulter(overall, effectiveThreshold)) {
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
