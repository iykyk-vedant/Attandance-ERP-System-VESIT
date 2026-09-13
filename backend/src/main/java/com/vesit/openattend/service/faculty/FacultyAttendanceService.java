package com.vesit.openattend.service.faculty;

import com.vesit.openattend.dto.faculty.*;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.exception.CorrectionWindowExpiredException;
import com.vesit.openattend.exception.MarkingNotAuthorizedException;
import com.vesit.openattend.repository.*;
import com.vesit.openattend.service.attendance.ProxyDelegationService;
import com.vesit.openattend.service.leave.DutyAttendanceAdjuster;
import com.vesit.openattend.service.notification.DefaulterAlertService;
import com.vesit.openattend.service.notification.DefaulterAlertService.MarkedAbsence;
import com.vesit.openattend.service.sync.SyncDiffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacultyAttendanceService {

    /** Sessions stay editable by the submitting faculty for this many hours after submission. */
    public static final long CORRECTION_WINDOW_HOURS = 48;

    /** Roles allowed to unlock and correct a session after the window has closed. */
    private static final Set<String> UNLOCK_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_CLASS_TEACHER"
    );

    /** Roles allowed to mark any cohort without an allocation or a proxy delegation. */
    private static final Set<String> MARK_ANY_ROLES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final LectureSessionRepository lectureSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceHistoryEventRepository attendanceHistoryEventRepository;
    private final FacultySubjectAllocationRepository allocationRepository;
    private final ProxyDelegationService proxyDelegationService;
    private final DutyAttendanceAdjuster dutyAttendanceAdjuster;
    private final DefaulterAlertService defaulterAlertService;

    @Transactional(readOnly = true)
    public List<FacultySubjectDto> getMySubjects(String facultyEmail) {
        User user = userRepository.findByEmail(facultyEmail).orElse(null);
        List<FacultySubjectAllocation> allocations = Collections.emptyList();

        if (user != null) {
            allocations = allocationRepository.findByFacultyId(user.getId());
        }

        // If no allocations found for this user, fallback to division D12B allocations or all subjects
        if (allocations.isEmpty()) {
            allocations = allocationRepository.findByDivision("D12B");
        }

        List<FacultySubjectDto> subjects;
        if (allocations.isEmpty()) {
            // Fallback to all registered subjects
            subjects = subjectRepository.findAll().stream().map(sub -> {
                int count = (int) studentRepository.count();
                return FacultySubjectDto.builder()
                        .subjectId(sub.getId())
                        .subjectCode(sub.getCode())
                        .subjectName(sub.getName())
                        .division("D12B")
                        .batch(null)
                        .totalStudents(count > 0 ? count : 40)
                        .lastSessionDate(LocalDate.now().toString())
                        .build();
            }).collect(Collectors.toList());
        } else {
            subjects = allocations.stream().map(alloc -> {
                Subject sub = alloc.getSubject();
                List<Student> students = getRosterStudents(alloc.getDivision(), alloc.getBatch());
                return FacultySubjectDto.builder()
                        .subjectId(sub.getId())
                        .subjectCode(sub.getCode())
                        .subjectName(sub.getName())
                        .division(alloc.getDivision())
                        .batch(alloc.getBatch())
                        .totalStudents(students.size())
                        .lastSessionDate(LocalDate.now().toString())
                        .build();
            }).collect(Collectors.toList());
        }

        subjects.addAll(delegatedSubjectsToday(user));
        return subjects;
    }

    /**
     * Lectures handed to this faculty as a substitute for today, appended to their own
     * allocations so the delegated slot shows up on the dashboard on the day it is taken.
     */
    private List<FacultySubjectDto> delegatedSubjectsToday(User user) {
        if (user == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now();

        return proxyDelegationService.activeDelegationsOn(user.getId(), today).stream().map(delegation -> {
            Subject sub = delegation.getSubject();
            List<Student> students = getRosterStudents(delegation.getDivision(), delegation.getBatch());
            return FacultySubjectDto.builder()
                    .subjectId(sub.getId())
                    .subjectCode(sub.getCode())
                    .subjectName(sub.getName())
                    .division(delegation.getDivision())
                    .batch(delegation.getBatch())
                    .totalStudents(students.size())
                    .lastSessionDate(today.toString())
                    .proxy(true)
                    .proxyAssignmentId(delegation.getId())
                    .proxyForFacultyEmail(delegation.getOriginalFaculty().getEmail())
                    .proxySessionIndex(delegation.getSessionIndex())
                    .proxyReason(delegation.getReason())
                    .build();
        }).collect(Collectors.toList());
    }

    public List<RosterStudentDto> getRoster(String subjectId, String division, String batch) {
        List<Student> students = getRosterStudents(division, batch);
        return students.stream().map(s -> RosterStudentDto.builder()
                .studentId(s.getId())
                .rollNo(s.getRollNo())
                .name(s.getName())
                .division(s.getDivision())
                .batch(s.getBatch())
                .build()
        ).collect(Collectors.toList());
    }

    private List<Student> getRosterStudents(String division, String batch) {
        if (division != null && !division.trim().isEmpty()) {
            if (batch != null && !batch.trim().isEmpty()) {
                List<Student> list = studentRepository.findByDivisionAndBatchOrderByRollNoAsc(division.trim(), batch.trim());
                if (!list.isEmpty()) return list;
            }
            List<Student> list = studentRepository.findByDivisionOrderByRollNoAsc(division.trim());
            if (!list.isEmpty()) return list;
        }
        return studentRepository.findAllByOrderByRollNoAsc();
    }

    @Transactional
    public LectureSessionResponse submitAttendance(String facultyEmail, Collection<String> callerRoles,
                                                   AttendanceSubmissionRequest request) {
        User faculty = userRepository.findByEmail(facultyEmail)
                .orElseThrow(() -> new IllegalArgumentException("Faculty user not found: " + facultyEmail));

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + request.getSubjectId()));

        LocalDate date = request.getLectureDate() != null ? request.getLectureDate() : LocalDate.now();
        int sessionIndex = request.getSessionIndex() != null ? request.getSessionIndex() : 1;
        String division = request.getDivision().trim();
        String batch = (request.getBatch() != null && !request.getBatch().trim().isEmpty()) ? request.getBatch().trim() : null;

        // Either the allocated teacher marks their own slot, or a substitute marks it under an
        // active delegation covering exactly this slot. Checked before anything is disclosed
        // about the slot itself.
        MarkingAuthorization authorization =
                resolveMarkingAuthorization(faculty, callerRoles, subject, division, batch, date, sessionIndex);

        // Idempotency check: verify if session already submitted
        Optional<LectureSession> existingSession;
        if (batch != null) {
            existingSession = lectureSessionRepository.findBySubjectIdAndDivisionAndBatchAndSessionDateAndSessionIndex(
                    subject.getId(), division, batch, date, sessionIndex
            );
        } else {
            existingSession = lectureSessionRepository.findBySubjectIdAndDivisionAndSessionDateAndSessionIndexAndBatchIsNull(
                    subject.getId(), division, date, sessionIndex
            );
        }

        if (existingSession.isPresent()) {
            throw new IllegalStateException("Attendance for " + subject.getCode() + " (" + division + ") on " + date + " [Slot " + sessionIndex + "] has already been submitted. Use the 48-Hour correction feature to make adjustments.");
        }

        List<Student> roster = getRosterStudents(division, batch);
        Set<String> absentSet = new HashSet<>(request.getAbsentStudentIds() != null ? request.getAbsentStudentIds() : Collections.emptyList());

        // Students whose OD / medical leave was approved before this lecture was marked. They are
        // recorded DUTY_PRESENT instead of ABSENT, so an OD cleared ahead of a hackathon applies to
        // the lectures marked while the student is away without anyone revisiting the session.
        Set<String> onApprovedLeave = dutyAttendanceAdjuster.studentsOnApprovedLeave(
                roster.stream().map(Student::getId).toList(), date);

        int total = roster.size();
        int absentCount = 0;
        for (Student s : roster) {
            if (absentSet.contains(s.getId()) && !onApprovedLeave.contains(s.getId())) {
                absentCount++;
            }
        }
        // Duty-present students sit on the present side of the session summary, matching the
        // records written below and keeping present + absent equal to the roster size.
        int presentCount = total - absentCount;

        String payloadHash = SyncDiffer.computeRowHash(List.of(
                subject.getId(), division, batch != null ? batch : "", date.toString(), sessionIndex, absentSet.toString()
        ));

        LectureSession session = LectureSession.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .faculty(faculty)
                .division(division)
                .batch(batch)
                .sessionDate(date)
                .sessionIndex(sessionIndex)
                .sessionType(request.getSessionType() != null ? request.getSessionType() : "LECTURE")
                .topicCovered(request.getTopicCovered())
                .totalStudents(total)
                .presentCount(presentCount)
                .absentCount(absentCount)
                .payloadHash(payloadHash)
                .submittedAt(LocalDateTime.now())
                .lastModifiedAt(LocalDateTime.now())
                // faculty above is taken_by; these two record that a substitute took the slot.
                .isProxy(authorization.proxy())
                .originalFaculty(authorization.originalFaculty())
                .proxyAssignment(authorization.delegation())
                .build();

        lectureSessionRepository.save(session);

        if (authorization.proxy()) {
            log.info("Proxy session {}: {} ({}) on {} [Slot {}] taken by {} for {}",
                    session.getId(), subject.getCode(), division, date, sessionIndex, faculty.getEmail(),
                    authorization.originalFaculty() != null ? authorization.originalFaculty().getEmail() : "unknown");
        }

        List<MarkedAbsence> newAbsences = new ArrayList<>();

        // Upsert AttendanceRecord for every student in the cohort
        for (Student student : roster) {
            AttendanceStatus status = resolveMarkedStatus(student.getId(), absentSet, onApprovedLeave);

            Optional<AttendanceRecord> existingRec = attendanceRecordRepository
                    .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(
                            student.getId(), subject.getId(), date, sessionIndex
                    );

            AttendanceRecord record;
            AttendanceStatus previousStatus = null;
            if (existingRec.isPresent()) {
                record = existingRec.get();
                previousStatus = record.getStatus();
                record.setStatus(status);
                record.setLectureSession(session);
                record.setFaculty(faculty.getEmail());
                record.setRemarks(request.getTopicCovered());
            } else {
                record = AttendanceRecord.builder()
                        .id(UUID.randomUUID().toString())
                        .student(student)
                        .subject(subject)
                        .lectureSession(session)
                        .lectureDate(date)
                        .sessionIndex(sessionIndex)
                        .status(status)
                        .faculty(faculty.getEmail())
                        .remarks(request.getTopicCovered())
                        .build();
            }
            attendanceRecordRepository.save(record);

            // Audit history — no reason required on the initial submission
            AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .attendanceRecord(record)
                    .previousStatus(null)
                    .newStatus(status)
                    .modifiedBy(faculty.getEmail())
                    .build();
            attendanceHistoryEventRepository.save(event);

            if (status == AttendanceStatus.ABSENT && previousStatus != AttendanceStatus.ABSENT) {
                newAbsences.add(new MarkedAbsence(student, previousStatus));
            }
        }

        // Warns anyone this roll call took below 75% / 65%; the email follows once this commits.
        defaulterAlertService.onAbsencesMarked(session, newAbsences);

        // Only the genuinely absent are reported back: a duty-present student was marked absent by
        // the teacher but is not absent for attendance purposes, and the correction modal prefills
        // itself from this list.
        List<String> reportedAbsentees = absentSet.stream()
                .filter(id -> !onApprovedLeave.contains(id))
                .toList();

        return mapToResponse(session, reportedAbsentees);
    }

    /**
     * The status a roll call produces for one student.
     *
     * <p>Absent plus an approved OD / medical leave becomes {@code DUTY_PRESENT}, which counts
     * towards the percentage without pretending the student was in the room. A student the teacher
     * marked present keeps {@code PRESENT} even while on leave — they turned up, and the honest
     * status is worth more than a uniform one.
     */
    private AttendanceStatus resolveMarkedStatus(String studentId, Set<String> absentIds, Set<String> onApprovedLeave) {
        if (!absentIds.contains(studentId)) {
            return AttendanceStatus.PRESENT;
        }
        return onApprovedLeave.contains(studentId) ? AttendanceStatus.DUTY_PRESENT : AttendanceStatus.ABSENT;
    }

    /**
     * Who this session is recorded against.
     *
     * @param proxy           true when the marking faculty is not the allocated subject teacher
     * @param originalFaculty the allocated subject teacher, set only on a proxy session
     * @param delegation      the delegation that granted the authorisation, null when the
     *                        session is marked by an admin without one
     */
    private record MarkingAuthorization(boolean proxy, User originalFaculty, ProxyAssignment delegation) {

        static MarkingAuthorization allocated() {
            return new MarkingAuthorization(false, null, null);
        }
    }

    /**
     * Decides whether {@code faculty} may mark this slot and how the session is attributed.
     *
     * <p>Authorised when the faculty is allocated to the cohort, holds an active proxy
     * delegation covering exactly this slot, or is an admin. A cohort with no allocation at all
     * stays open — this mirrors the unallocated fallback in {@link #getMySubjects(String)} so a
     * subject the dashboard offers is never rejected here.
     */
    private MarkingAuthorization resolveMarkingAuthorization(User faculty, Collection<String> callerRoles,
                                                             Subject subject, String division, String batch,
                                                             LocalDate date, Integer sessionIndex) {
        List<FacultySubjectAllocation> owners = allocationRepository
                .findBySubjectIdAndDivision(subject.getId(), division).stream()
                .filter(alloc -> alloc.getBatch() == null || alloc.getBatch().equals(batch))
                .toList();

        boolean allocatedToCaller = owners.stream()
                .anyMatch(alloc -> alloc.getFaculty().getId().equals(faculty.getId()));
        if (allocatedToCaller) {
            return MarkingAuthorization.allocated();
        }

        Optional<ProxyAssignment> delegation = proxyDelegationService.findActiveDelegation(
                faculty.getId(), subject.getId(), division, batch, date, sessionIndex);
        if (delegation.isPresent()) {
            return new MarkingAuthorization(true, delegation.get().getOriginalFaculty(), delegation.get());
        }

        if (owners.isEmpty()) {
            log.warn("Unallocated cohort marked by {}: {} ({}) — no faculty allocation exists for this slot",
                    faculty.getEmail(), subject.getCode(), division);
            return MarkingAuthorization.allocated();
        }

        if (hasRole(callerRoles, MARK_ANY_ROLES)) {
            // Admins may always step in, but the session is still recorded as a stand-in.
            return new MarkingAuthorization(true, owners.get(0).getFaculty(), null);
        }

        throw new MarkingNotAuthorizedException(
                "You are not allocated to " + subject.getCode() + " (" + division
                        + ") and hold no proxy delegation for this slot on " + date
                        + " [Slot " + sessionIndex + "]. Ask the subject teacher to raise a proxy assignment.");
    }

    @Transactional
    public LectureSessionResponse correctAttendance(String callerEmail, Collection<String> callerRoles, String sessionId,
                                                    AttendanceCorrectionRequest request) {
        LectureSession session = lectureSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Justification reason validation (>= 10 characters) — mirrors the bean validation on the DTO
        // so the rule also holds for internal callers that bypass the controller.
        if (request.getCorrectionReason() == null || request.getCorrectionReason().trim().length() < 10) {
            throw new IllegalArgumentException("Correction reason must be at least 10 characters explaining the modification.");
        }
        String reason = request.getCorrectionReason().trim();

        // 48-Hour correction window. Past the window the session is locked and only an
        // admin or the class teacher may unlock it.
        long hoursElapsed = ChronoUnit.HOURS.between(session.getSubmittedAt(), LocalDateTime.now());
        boolean withinWindow = hoursElapsed <= CORRECTION_WINDOW_HOURS;
        boolean unlockOverride = !withinWindow && hasUnlockPrivilege(callerRoles);

        if (!withinWindow && !unlockOverride) {
            throw new CorrectionWindowExpiredException(
                    "48-Hour correction window has expired for this lecture session (" + hoursElapsed
                            + "h elapsed). Only an administrator or the class teacher can unlock it.");
        }

        if (unlockOverride) {
            log.warn("Locked session {} corrected after {}h by privileged user {}", sessionId, hoursElapsed, callerEmail);
        }

        Set<String> newAbsentSet = new HashSet<>(request.getUpdatedAbsentStudentIds() != null ? request.getUpdatedAbsentStudentIds() : Collections.emptyList());
        List<AttendanceRecord> records = attendanceRecordRepository.findByLectureSessionId(session.getId());

        // A correction re-applies approved leave too: a certificate cleared after the lecture was
        // marked must not be undone by a teacher later fixing an unrelated name on the same roll call.
        Set<String> onApprovedLeave = dutyAttendanceAdjuster.studentsOnApprovedLeave(
                records.stream().map(rec -> rec.getStudent().getId()).toList(), session.getSessionDate());

        int absentCount = 0;
        List<MarkedAbsence> newAbsences = new ArrayList<>();
        for (AttendanceRecord rec : records) {
            AttendanceStatus oldStatus = rec.getStatus();
            AttendanceStatus newStatus = resolveMarkedStatus(rec.getStudent().getId(), newAbsentSet, onApprovedLeave);

            if (newStatus == AttendanceStatus.ABSENT) {
                absentCount++;
            }

            if (oldStatus != newStatus) {
                rec.setStatus(newStatus);
                if (request.getTopicCovered() != null && !request.getTopicCovered().trim().isEmpty()) {
                    rec.setRemarks(request.getTopicCovered());
                }
                attendanceRecordRepository.save(rec);

                // Immutable audit trail: who changed what, when, and why
                AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .attendanceRecord(rec)
                        .previousStatus(oldStatus)
                        .newStatus(newStatus)
                        .modifiedBy(callerEmail)
                        .mandatoryReason(reason)
                        .build();
                attendanceHistoryEventRepository.save(event);

                if (newStatus == AttendanceStatus.ABSENT) {
                    newAbsences.add(new MarkedAbsence(rec.getStudent(), oldStatus));
                }
            }
        }

        // A correction that marks someone absent can take them below the line just as a submission can.
        defaulterAlertService.onAbsencesMarked(session, newAbsences);

        session.setAbsentCount(absentCount);
        session.setPresentCount(session.getTotalStudents() - absentCount);
        if (request.getTopicCovered() != null && !request.getTopicCovered().trim().isEmpty()) {
            session.setTopicCovered(request.getTopicCovered());
        }

        String auditNote = (session.getCorrectionReason() != null ? session.getCorrectionReason() + " | " : "")
                + "[" + LocalDateTime.now() + " by " + callerEmail + (unlockOverride ? " (ADMIN UNLOCK)" : "") + "]: " + reason;
        session.setCorrectionReason(auditNote);
        session.setLastModifiedAt(LocalDateTime.now());

        lectureSessionRepository.save(session);

        return mapToResponse(session, new ArrayList<>(newAbsentSet));
    }

    /**
     * True when the caller holds a role that may unlock a session past the 48-hour window.
     * Roles are compared with the {@code ROLE_} prefix used by the JWT filter's authorities.
     */
    private boolean hasUnlockPrivilege(Collection<String> callerRoles) {
        return hasRole(callerRoles, UNLOCK_ROLES);
    }

    /** True when any caller authority is in {@code allowed}, with or without the {@code ROLE_} prefix. */
    private boolean hasRole(Collection<String> callerRoles, Set<String> allowed) {
        if (callerRoles == null) {
            return false;
        }
        return callerRoles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .anyMatch(allowed::contains);
    }

    @Transactional(readOnly = true)
    public List<LectureSessionResponse> getSessionHistory(String facultyEmail, String subjectId) {
        List<LectureSession> sessions;
        if (subjectId != null && !subjectId.trim().isEmpty()) {
            sessions = lectureSessionRepository.findBySubjectIdOrderBySessionDateDescSessionIndexDesc(subjectId.trim());
        } else {
            User faculty = userRepository.findByEmail(facultyEmail).orElse(null);
            if (faculty != null) {
                sessions = lectureSessionRepository.findByFacultyIdOrderBySessionDateDescSessionIndexDesc(faculty.getId());
            } else {
                sessions = lectureSessionRepository.findAll();
            }
        }

        return sessions.stream().map(s -> {
            List<AttendanceRecord> records = attendanceRecordRepository.findByLectureSessionId(s.getId());
            List<String> absentees = records.stream()
                    .filter(r -> r.getStatus() == AttendanceStatus.ABSENT)
                    .map(r -> r.getStudent().getId())
                    .collect(Collectors.toList());
            return mapToResponse(s, absentees);
        }).collect(Collectors.toList());
    }

    private LectureSessionResponse mapToResponse(LectureSession session, List<String> absentees) {
        boolean canCorrect = ChronoUnit.HOURS.between(session.getSubmittedAt(), LocalDateTime.now()) <= CORRECTION_WINDOW_HOURS;

        return LectureSessionResponse.builder()
                .id(session.getId())
                .subjectId(session.getSubject().getId())
                .subjectCode(session.getSubject().getCode())
                .subjectName(session.getSubject().getName())
                .division(session.getDivision())
                .batch(session.getBatch())
                .sessionDate(session.getSessionDate().toString())
                .sessionIndex(session.getSessionIndex())
                .sessionType(session.getSessionType())
                .topicCovered(session.getTopicCovered())
                .totalStudents(session.getTotalStudents())
                .presentCount(session.getPresentCount())
                .absentCount(session.getAbsentCount())
                .absentStudentIds(absentees)
                .submittedAt(session.getSubmittedAt().toString())
                .lastModifiedAt(session.getLastModifiedAt().toString())
                .canCorrect(canCorrect)
                .correctionReason(session.getCorrectionReason())
                // Audit trail: who actually took the lecture, and for whom.
                .takenByFacultyEmail(session.getFaculty() != null ? session.getFaculty().getEmail() : null)
                .proxy(Boolean.TRUE.equals(session.getIsProxy()))
                .originalFacultyEmail(session.getOriginalFaculty() != null
                        ? session.getOriginalFaculty().getEmail() : null)
                .proxyAssignmentId(session.getProxyAssignment() != null
                        ? session.getProxyAssignment().getId() : null)
                .proxyReason(session.getProxyAssignment() != null
                        ? session.getProxyAssignment().getReason() : null)
                .build();
    }
}
