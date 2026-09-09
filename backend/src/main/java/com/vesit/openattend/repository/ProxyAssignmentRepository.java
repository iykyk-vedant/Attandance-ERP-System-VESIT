package com.vesit.openattend.repository;

import com.vesit.openattend.entity.ProxyAssignment;
import com.vesit.openattend.entity.enums.ProxyAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProxyAssignmentRepository extends JpaRepository<ProxyAssignment, String> {

    /**
     * Candidate delegations for one cohort slot. The nullable batch and session index are
     * matched in the service so a null (whole-division / whole-day) delegation still applies.
     */
    List<ProxyAssignment> findBySubjectIdAndDivisionAndLectureDateAndStatus(
            String subjectId, String division, LocalDate lectureDate, ProxyAssignmentStatus status
    );

    List<ProxyAssignment> findByProxyFacultyIdAndLectureDateAndStatus(
            String proxyFacultyId, LocalDate lectureDate, ProxyAssignmentStatus status
    );

    List<ProxyAssignment> findByProxyFacultyIdAndLectureDateBetweenOrderByLectureDateAscCreatedAtAsc(
            String proxyFacultyId, LocalDate from, LocalDate to
    );

    List<ProxyAssignment> findByOriginalFacultyIdOrderByLectureDateDescCreatedAtDesc(String originalFacultyId);
}
