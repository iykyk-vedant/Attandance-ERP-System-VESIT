package com.vesit.openattend.repository;

import com.vesit.openattend.entity.LeaveRequest;
import com.vesit.openattend.entity.enums.LeaveRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, String> {

    /** The student's own applications, newest first. */
    @Query("""
            SELECT lr FROM LeaveRequest lr
              JOIN FETCH lr.student s
             WHERE s.id = :studentId
             ORDER BY lr.createdAt DESC
            """)
    List<LeaveRequest> findForStudent(@Param("studentId") String studentId);

    /**
     * The approval queue. A null status returns every request and a null division every cohort,
     * so one query serves both the pending dashboard and the decided-history tab.
     *
     * <p>Ordered oldest first: a student waiting on an OD for tomorrow's hackathon should not be
     * pushed down the queue by later submissions.
     */
    @Query("""
            SELECT lr FROM LeaveRequest lr
              JOIN FETCH lr.student s
             WHERE (:status IS NULL OR lr.status = :status)
               AND (:division IS NULL OR s.division = :division)
             ORDER BY lr.createdAt ASC
            """)
    List<LeaveRequest> findForApprovalQueue(@Param("status") LeaveRequestStatus status,
                                            @Param("division") String division);

    /** Requests that could conflict with a new application for the same student. */
    List<LeaveRequest> findByStudentIdAndStatusIn(String studentId, Collection<LeaveRequestStatus> statuses);

    /**
     * Of {@code studentIds}, those whose approved leave covers {@code date}. Answered in one
     * query so marking a 40-student roster does not fan out into a lookup per student.
     */
    @Query("""
            SELECT DISTINCT lr.student.id FROM LeaveRequest lr
             WHERE lr.status = com.vesit.openattend.entity.enums.LeaveRequestStatus.APPROVED
               AND lr.fromDate <= :date
               AND lr.toDate >= :date
               AND lr.student.id IN :studentIds
            """)
    List<String> findStudentIdsOnApprovedLeave(@Param("studentIds") Collection<String> studentIds,
                                               @Param("date") LocalDate date);
}
