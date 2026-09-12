package com.vesit.openattend.repository;

import com.vesit.openattend.dto.notification.StudentSubjectTally;
import com.vesit.openattend.dto.report.DefaulterAggregateRow;
import com.vesit.openattend.entity.AttendanceRecord;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
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

    /** Every record inside an inclusive date window — the slice an approved leave acts on. */
    List<AttendanceRecord> findByStudentIdAndLectureDateBetweenOrderByLectureDateAscSessionIndexAsc(
            String studentId, LocalDate from, LocalDate to
    );

    /**
     * Single grouped pass producing conducted/attended counts per (student, subject) for the
     * defaulter report. Denominators are per-student by construction: a student only has rows for
     * the lectures they were on the roster for, so electives never inherit a division-wide
     * denominator.
     *
     * <p>The counting rule itself lives on {@link AttendanceStatus} — {@code DUTY_PRESENT}
     * (approved OD / medical leave) counts as attended, {@code NA} is excluded from conducted,
     * which is how a student who is not accountable for a lecture stays out of their own
     * denominator.
     *
     * <p>Prefer {@link #aggregateDefaulterRows(String, LocalDate, LocalDate)} — it supplies the
     * status sets so callers do not repeat the rule.
     */
    @Query("""
            SELECT new com.vesit.openattend.dto.report.DefaulterAggregateRow(
                       s.id, s.rollNo, s.name, s.division, sub.code, sub.name,
                       SUM(CASE WHEN ar.status IN :conductedStatuses THEN 1L ELSE 0L END),
                       SUM(CASE WHEN ar.status IN :attendedStatuses THEN 1L ELSE 0L END),
                       SUM(CASE WHEN ar.status = :dutyStatus THEN 1L ELSE 0L END))
              FROM AttendanceRecord ar
              JOIN ar.student s
              JOIN ar.subject sub
             WHERE (:division IS NULL OR s.division = :division)
               AND (:startDate IS NULL OR ar.lectureDate >= :startDate)
               AND (:endDate IS NULL OR ar.lectureDate <= :endDate)
             GROUP BY s.id, s.rollNo, s.name, s.division, sub.code, sub.name
            HAVING SUM(CASE WHEN ar.status IN :conductedStatuses THEN 1L ELSE 0L END) > 0
             ORDER BY s.rollNo ASC, sub.code ASC
            """)
    List<DefaulterAggregateRow> aggregateDefaulterRows(
            @Param("division") String division,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses,
            @Param("conductedStatuses") Collection<AttendanceStatus> conductedStatuses,
            @Param("dutyStatus") AttendanceStatus dutyStatus
    );

    /** Counts by the rule on {@link AttendanceStatus}: OD counts as attended, NA counts as nothing. */
    default List<DefaulterAggregateRow> aggregateDefaulterRows(String division, LocalDate startDate, LocalDate endDate) {
        return aggregateDefaulterRows(division, startDate, endDate,
                AttendanceStatus.attended(), AttendanceStatus.conducted(), AttendanceStatus.DUTY_PRESENT);
    }

    /**
     * Per-subject conducted/attended counts for a set of students in one grouped pass — what the
     * defaulter alert check reads after a roll call, rather than loading every record per absentee.
     *
     * <p>Prefer {@link #tallyByStudentAndSubject(Collection)}, which supplies the status sets.
     */
    @Query("""
            SELECT new com.vesit.openattend.dto.notification.StudentSubjectTally(
                       ar.student.id, ar.subject.id,
                       SUM(CASE WHEN ar.status IN :conductedStatuses THEN 1L ELSE 0L END),
                       SUM(CASE WHEN ar.status IN :attendedStatuses THEN 1L ELSE 0L END))
              FROM AttendanceRecord ar
             WHERE ar.student.id IN :studentIds
             GROUP BY ar.student.id, ar.subject.id
            """)
    List<StudentSubjectTally> tallyByStudentAndSubject(
            @Param("studentIds") Collection<String> studentIds,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses,
            @Param("conductedStatuses") Collection<AttendanceStatus> conductedStatuses
    );

    /** Counts by the rule on {@link AttendanceStatus}: OD counts as attended, NA counts as nothing. */
    default List<StudentSubjectTally> tallyByStudentAndSubject(Collection<String> studentIds) {
        return tallyByStudentAndSubject(studentIds, AttendanceStatus.attended(), AttendanceStatus.conducted());
    }
}
