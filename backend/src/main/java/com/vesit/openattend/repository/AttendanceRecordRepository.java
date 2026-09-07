package com.vesit.openattend.repository;

import com.vesit.openattend.dto.report.DefaulterAggregateRow;
import com.vesit.openattend.entity.AttendanceRecord;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {
    Optional<AttendanceRecord> findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(
        String studentId,
        String subjectId,
        LocalDate lectureDate,
        Integer sessionIndex
    );

    List<AttendanceRecord> findByStudentId(String studentId);
    List<AttendanceRecord> findByStudentIdAndSubjectId(String studentId, String subjectId);
    List<AttendanceRecord> findBySubjectId(String subjectId);
    List<AttendanceRecord> findByLectureSessionId(String lectureSessionId);

    /**
     * Single grouped pass producing conducted/attended counts per (student, subject) for the
     * defaulter report. Denominators are per-student by construction: a student only has rows for
     * the lectures they were on the roster for, so electives never inherit a division-wide
     * denominator. {@code NA} rows are excluded from conducted, which is how a student who is not
     * accountable for a given lecture is kept out of their own denominator.
     *
     * <p>Prefer {@link #aggregateDefaulterRows(String, LocalDate, LocalDate)} — it supplies the
     * status literals so callers do not repeat the counting rule.
     */
    @Query("""
            SELECT new com.vesit.openattend.dto.report.DefaulterAggregateRow(
                       s.id, s.rollNo, s.name, s.division, sub.code, sub.name,
                       SUM(CASE WHEN ar.status <> :excludedStatus THEN 1L ELSE 0L END),
                       SUM(CASE WHEN ar.status = :attendedStatus THEN 1L ELSE 0L END))
              FROM AttendanceRecord ar
              JOIN ar.student s
              JOIN ar.subject sub
             WHERE (:division IS NULL OR s.division = :division)
               AND (:startDate IS NULL OR ar.lectureDate >= :startDate)
               AND (:endDate IS NULL OR ar.lectureDate <= :endDate)
             GROUP BY s.id, s.rollNo, s.name, s.division, sub.code, sub.name
            HAVING SUM(CASE WHEN ar.status <> :excludedStatus THEN 1L ELSE 0L END) > 0
             ORDER BY s.rollNo ASC, sub.code ASC
            """)
    List<DefaulterAggregateRow> aggregateDefaulterRows(
            @Param("division") String division,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("attendedStatus") AttendanceStatus attendedStatus,
            @Param("excludedStatus") AttendanceStatus excludedStatus
    );

    /** Conducted counts PRESENT + ABSENT; NA is never accountable. */
    default List<DefaulterAggregateRow> aggregateDefaulterRows(String division, LocalDate startDate, LocalDate endDate) {
        return aggregateDefaulterRows(division, startDate, endDate, AttendanceStatus.PRESENT, AttendanceStatus.NA);
    }
}
