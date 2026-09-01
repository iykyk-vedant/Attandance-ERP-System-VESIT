package com.vesit.openattend.repository;

import com.vesit.openattend.entity.AttendanceHistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceHistoryEventRepository extends JpaRepository<AttendanceHistoryEvent, String> {
    List<AttendanceHistoryEvent> findByAttendanceRecordId(String attendanceRecordId);
    List<AttendanceHistoryEvent> findBySyncLogId(String syncLogId);
}
