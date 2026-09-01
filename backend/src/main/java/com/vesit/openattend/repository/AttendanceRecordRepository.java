package com.vesit.openattend.repository;

import com.vesit.openattend.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
