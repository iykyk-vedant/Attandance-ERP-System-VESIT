package com.vesit.openattend.service.notification;

import com.vesit.openattend.dto.notification.StudentSubjectTally;
import com.vesit.openattend.entity.LectureSession;
import com.vesit.openattend.entity.Notification;
import com.vesit.openattend.entity.Student;
import com.vesit.openattend.entity.Subject;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.NotificationEmailStatus;
import com.vesit.openattend.repository.AttendanceRecordRepository;
import com.vesit.openattend.repository.NotificationRepository;
import com.vesit.openattend.service.attendance.AttendancePercentage;
import com.vesit.openattend.service.predictor.PredictorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Warns a student the moment a roll call takes them below 75% — per subject and overall — so they
 * can recover before the exam cut-off rather than finding out from the defaulter notice.
 *
 * <p>An alert fires on the <em>crossing</em>, not on being below: a student at 70% who misses
 * another lecture is not warned again. On top of that, alerts are throttled per student and scope:
 *
 * <ul>
 *   <li>a {@link DefaulterAlertLevel#WARNING} is not repeated within {@link #COOLDOWN} of any alert
 *       for the same subject (or for aggregate attendance);</li>
 *   <li>a {@link DefaulterAlertLevel#CRITICAL} alert, below 65%, ignores recent warnings and is
 *       only held back by another critical alert in the same window.</li>
 * </ul>
 *
 * <p>The in-app notification is written inside the attendance transaction, so it exists exactly
 * when the attendance does. The email copy goes out after commit via {@link DefaulterAlertMailer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaulterAlertService {

    public static final Duration COOLDOWN = Duration.ofDays(7);

    /** A student with no accountable lectures yet has not fallen from anywhere; they start in good standing. */
    private static final BigDecimal GOOD_STANDING = new BigDecimal("100.00");

    private static final List<String> ALERT_TYPES = Arrays.stream(DefaulterAlertLevel.values())
            .map(DefaulterAlertLevel::notificationType)
            .toList();

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final NotificationRepository notificationRepository;
    private final PredictorService predictorService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * A student this roll call has just recorded ABSENT.
     *
     * @param previousStatus the record's status before this roll call, null when the record is new
     */
    public record MarkedAbsence(Student student, AttendanceStatus previousStatus) {
    }

    /**
     * Checks everyone {@code session} newly marked absent and stores an alert for each boundary
     * crossed. Only an absence can lower a percentage, so the rest of the roster is never read.
     *
     * <p>Must run after the session's records are saved, in the same transaction.
     *
     * @return the notifications stored, empty when nobody crossed a boundary or all were throttled
     */
    @Transactional
    public List<Notification> onAbsencesMarked(LectureSession session, Collection<MarkedAbsence> absences) {
        if (absences == null || absences.isEmpty()) {
            return List.of();
        }

        Map<String, MarkedAbsence> byStudent = new LinkedHashMap<>();
        for (MarkedAbsence absence : absences) {
            byStudent.putIfAbsent(absence.student().getId(), absence);
        }

        Subject subject = session.getSubject();
        Map<String, AttendancePercentage.Tally> subjectAfter = new HashMap<>();
        Map<String, AttendancePercentage.Tally> overallAfter = new HashMap<>();

        for (StudentSubjectTally row : attendanceRecordRepository.tallyByStudentAndSubject(byStudent.keySet())) {
            AttendancePercentage.Tally tally = new AttendancePercentage.Tally(
                    Math.toIntExact(row.attended()), Math.toIntExact(row.conducted()), 0);
            overallAfter.merge(row.studentId(), tally, DefaulterAlertService::sum);
            if (subject.getId().equals(row.subjectId())) {
                subjectAfter.put(row.studentId(), tally);
            }
        }

        Set<String> recentAlerts = recentAlertKeys(byStudent.keySet());
        List<DefaulterAlert> raised = new ArrayList<>();
        List<Notification> stored = new ArrayList<>();

        for (MarkedAbsence absence : byStudent.values()) {
            String studentId = absence.student().getId();
            raiseIfCrossed(absence, subject, subjectAfter.get(studentId), session, recentAlerts, raised, stored);
            raiseIfCrossed(absence, null, overallAfter.get(studentId), session, recentAlerts, raised, stored);
        }

        if (!raised.isEmpty()) {
            log.info("Session {} ({} on {} [Slot {}]): {} defaulter alert(s) raised for {} absentee(s)",
                    session.getId(), subject.getCode(), session.getSessionDate(), session.getSessionIndex(),
                    raised.size(), byStudent.size());
            eventPublisher.publishEvent(new DefaulterAlertRaisedEvent(List.copyOf(raised)));
        }
        return stored;
    }

    /**
     * @param scope null for the student's aggregate attendance across every subject
     */
    private void raiseIfCrossed(MarkedAbsence absence, Subject scope, AttendancePercentage.Tally after,
                                LectureSession session, Set<String> recentAlerts,
                                List<DefaulterAlert> raised, List<Notification> stored) {
        if (after == null || after.conducted() == 0) {
            return;
        }

        AttendancePercentage.Tally before = beforeThisAbsence(after, absence.previousStatus());
        BigDecimal pctBefore = before.conducted() > 0 ? before.percentage() : GOOD_STANDING;
        BigDecimal pctAfter = after.percentage();

        DefaulterAlertLevel level = DefaulterAlertLevel.crossed(pctBefore, pctAfter);
        if (level == null) {
            return;
        }

        Student student = absence.student();
        String scopeId = scope != null ? scope.getId() : null;

        if (onCooldown(recentAlerts, student.getId(), scopeId, level)) {
            log.debug("Defaulter {} alert for {} ({}) held back by the {}-day cooldown",
                    level, student.getRollNo(), scope != null ? scope.getCode() : "overall", COOLDOWN.toDays());
            return;
        }

        // Only reached below 75%, where at least one lecture is always needed; the floor guards the
        // predictor's one-decimal rounding of a percentage just under the line.
        int lecturesToRecover = Math.max(1, predictorService
                .calculate(after.attended(), after.conducted(), PredictorService.DEFAULT_THRESHOLD, null)
                .getMustAttend());

        Subject missed = session.getSubject();
        Notification notification = notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID().toString())
                .student(student)
                .subject(scope)
                .type(level.notificationType())
                .message(message(level, scope, after, pctAfter, lecturesToRecover, session))
                .isRead(false)
                .emailStatus(NotificationEmailStatus.PENDING)
                .build());

        recentAlerts.add(alertKey(student.getId(), scopeId, level));
        stored.add(notification);
        raised.add(new DefaulterAlert(
                notification.getId(),
                student.getUser() != null ? student.getUser().getEmail() : null,
                student.getName(),
                student.getRollNo(),
                level,
                scope != null ? scope.getCode() : null,
                scope != null ? scope.getName() : null,
                pctAfter,
                after.attended(),
                after.conducted(),
                lecturesToRecover,
                missed.getCode(),
                missed.getName(),
                session.getSessionDate(),
                session.getSessionIndex() != null ? session.getSessionIndex() : 1));
    }

    /**
     * The tally as it stood before this roll call: take back the ABSENT just recorded, and put back
     * whatever the record said before (nothing, for a new record).
     */
    private static AttendancePercentage.Tally beforeThisAbsence(AttendancePercentage.Tally after, AttendanceStatus previous) {
        int attended = after.attended();
        int conducted = after.conducted() - 1;
        if (previous != null) {
            if (previous.isConducted()) {
                conducted++;
            }
            if (previous.isAttended()) {
                attended++;
            }
        }
        return new AttendancePercentage.Tally(attended, Math.max(0, conducted), 0);
    }

    /** A critical alert this week silences both levels; a warning only silences another warning. */
    private static boolean onCooldown(Set<String> recentAlerts, String studentId, String scopeId, DefaulterAlertLevel level) {
        if (recentAlerts.contains(alertKey(studentId, scopeId, DefaulterAlertLevel.CRITICAL))) {
            return true;
        }
        return level == DefaulterAlertLevel.WARNING
                && recentAlerts.contains(alertKey(studentId, scopeId, DefaulterAlertLevel.WARNING));
    }

    private Set<String> recentAlertKeys(Collection<String> studentIds) {
        LocalDateTime since = LocalDateTime.now().minus(COOLDOWN);
        Set<String> keys = new HashSet<>();
        for (Notification n : notificationRepository.findRecentAlerts(studentIds, ALERT_TYPES, since)) {
            String scopeId = n.getSubject() != null ? n.getSubject().getId() : null;
            for (DefaulterAlertLevel level : DefaulterAlertLevel.values()) {
                if (level.notificationType().equals(n.getType())) {
                    keys.add(alertKey(n.getStudent().getId(), scopeId, level));
                }
            }
        }
        return keys;
    }

    private static String alertKey(String studentId, String scopeId, DefaulterAlertLevel level) {
        return studentId + "|" + (scopeId != null ? scopeId : "*") + "|" + level.name();
    }

    private static AttendancePercentage.Tally sum(AttendancePercentage.Tally a, AttendancePercentage.Tally b) {
        return new AttendancePercentage.Tally(a.attended() + b.attended(), a.conducted() + b.conducted(), 0);
    }

    private static String message(DefaulterAlertLevel level, Subject scope, AttendancePercentage.Tally after,
                                  BigDecimal percentage, int lecturesToRecover, LectureSession session) {
        String prefix = level == DefaulterAlertLevel.CRITICAL ? "Critical: " : "";
        String what = scope != null
                ? scope.getCode() + " (" + scope.getName() + ") attendance"
                : "Overall attendance";
        String cause = scope != null
                ? "after the lecture on " + session.getSessionDate()
                : "after missing " + session.getSubject().getCode() + " on " + session.getSessionDate();

        return prefix + what + " has fallen to " + percentage.toPlainString() + "% ("
                + after.attended() + " of " + after.conducted() + " lectures) " + cause
                + " — attend the next " + lecturesToRecover + " lecture(s) in a row to get back to 75%.";
    }
}
