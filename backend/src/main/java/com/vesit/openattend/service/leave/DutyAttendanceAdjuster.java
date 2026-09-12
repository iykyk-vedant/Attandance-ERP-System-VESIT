package com.vesit.openattend.service.leave;

import com.vesit.openattend.entity.AttendanceHistoryEvent;
import com.vesit.openattend.entity.AttendanceRecord;
import com.vesit.openattend.entity.LeaveRequest;
import com.vesit.openattend.entity.LectureSession;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.repository.AttendanceHistoryEventRepository;
import com.vesit.openattend.repository.AttendanceRecordRepository;
import com.vesit.openattend.repository.LeaveRequestRepository;
import com.vesit.openattend.repository.LectureSessionRepository;
import com.vesit.openattend.service.attendance.AttendancePercentage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an approved leave into attendance.
 *
 * <p>Approval reaches a student's records from two directions, and both are needed for the
 * workflow to hold together:
 *
 * <ul>
 *   <li><b>Backwards</b> — {@link #applyApprovedLeave} rewrites the ABSENT records already sitting
 *       in the leave window, which is the case for a medical certificate produced after the
 *       illness.</li>
 *   <li><b>Forwards</b> — {@link #studentsOnApprovedLeave} lets the marking path record
 *       {@code DUTY_PRESENT} directly, so an OD approved before a hackathon still applies to the
 *       lectures marked while the student is away.</li>
 * </ul>
 *
 * <p>Nothing is deleted or recounted in place: every flip writes an {@link AttendanceHistoryEvent}
 * naming the approver and the request, and the percentage is re-derived from the records
 * afterwards rather than stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DutyAttendanceAdjuster {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceHistoryEventRepository historyEventRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LectureSessionRepository lectureSessionRepository;

    /**
     * Transitions every ABSENT record inside the leave window to {@code DUTY_PRESENT}.
     *
     * <p>PRESENT records are left alone — a student who turned up anyway keeps the honest status,
     * and both count identically towards the percentage. NA records stay out of the denominator
     * as before.
     *
     * @return how many records were adjusted
     */
    @Transactional
    public int applyApprovedLeave(LeaveRequest request, String approverEmail) {
        List<AttendanceRecord> inWindow = attendanceRecordRepository
                .findByStudentIdAndLectureDateBetweenOrderByLectureDateAscSessionIndexAsc(
                        request.getStudent().getId(), request.getFromDate(), request.getToDate());

        String auditReason = auditReason(request, approverEmail);
        Map<String, LectureSession> touchedSessions = new LinkedHashMap<>();
        int adjusted = 0;

        for (AttendanceRecord record : inWindow) {
            if (record.getStatus() != AttendanceStatus.ABSENT) {
                continue;
            }

            record.setStatus(AttendanceStatus.DUTY_PRESENT);
            attendanceRecordRepository.save(record);

            historyEventRepository.save(AttendanceHistoryEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .attendanceRecord(record)
                    .previousStatus(AttendanceStatus.ABSENT)
                    .newStatus(AttendanceStatus.DUTY_PRESENT)
                    .modifiedBy(approverEmail)
                    .mandatoryReason(auditReason)
                    .build());
            adjusted++;

            if (record.getLectureSession() != null) {
                touchedSessions.putIfAbsent(record.getLectureSession().getId(), record.getLectureSession());
            }
        }

        touchedSessions.values().forEach(this::refreshSessionCounters);

        log.info("Leave {} approved by {}: {} of {} records in {}..{} moved to DUTY_PRESENT for student {}",
                request.getId(), approverEmail, adjusted, inWindow.size(),
                request.getFromDate(), request.getToDate(), request.getStudent().getRollNo());

        return adjusted;
    }

    /**
     * Re-derives a lecture session present/absent summary from its records.
     *
     * <p>Approving a leave rewrites records under a session that was submitted days earlier, so
     * the summary a teacher reads on the sessions list would otherwise still show the student as
     * absent while the record itself says duty-present. Duty-present sits on the present side,
     * matching how the session was counted when it was first submitted.
     */
    private void refreshSessionCounters(LectureSession session) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByLectureSessionId(session.getId());

        int absent = (int) records.stream()
                .filter(record -> record.getStatus() == AttendanceStatus.ABSENT)
                .count();

        session.setAbsentCount(absent);
        session.setPresentCount(Math.max(0, session.getTotalStudents() - absent));
        lectureSessionRepository.save(session);
    }

    /**
     * The student's overall attendance, counted by the rule on {@link AttendanceStatus}. Read
     * either side of an approval to show the approver what their click changed.
     */
    @Transactional(readOnly = true)
    public AttendancePercentage.Tally overallTally(String studentId) {
        return AttendancePercentage.tally(attendanceRecordRepository.findByStudentId(studentId));
    }

    /**
     * Of {@code studentIds}, those whose approved leave covers {@code date} — resolved in one
     * query so marking a full roster stays a single round trip.
     */
    @Transactional(readOnly = true)
    public Set<String> studentsOnApprovedLeave(Collection<String> studentIds, LocalDate date) {
        if (studentIds == null || studentIds.isEmpty() || date == null) {
            return Set.of();
        }
        return new HashSet<>(leaveRequestRepository.findStudentIdsOnApprovedLeave(studentIds, date));
    }

    /** Names the approver, the event and the request id, so a flipped record is always traceable. */
    private String auditReason(LeaveRequest request, String approverEmail) {
        return request.getLeaveType() + " leave approved by " + approverEmail
                + " - " + request.getEventName()
                + " (" + request.getFromDate() + " to " + request.getToDate() + ")"
                + " [leave request " + request.getId() + "]";
    }
}
