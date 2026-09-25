package com.vesit.openattend.service.faculty;

import com.vesit.openattend.dto.faculty.*;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import com.vesit.openattend.service.sync.SyncDiffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacultyAttendanceService {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final LectureSessionRepository lectureSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceHistoryEventRepository attendanceHistoryEventRepository;
    private final FacultySubjectAllocationRepository allocationRepository;

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

        if (allocations.isEmpty()) {
            // Fallback to all registered subjects
            return subjectRepository.findAll().stream().map(sub -> {
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
        }

        return allocations.stream().map(alloc -> {
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
    public LectureSessionResponse submitAttendance(String facultyEmail, AttendanceSubmissionRequest request) {
        User faculty = userRepository.findByEmail(facultyEmail)
                .orElseThrow(() -> new IllegalArgumentException("Faculty user not found: " + facultyEmail));

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + request.getSubjectId()));

        LocalDate date = request.getLectureDate() != null ? request.getLectureDate() : LocalDate.now();
        int sessionIndex = request.getSessionIndex() != null ? request.getSessionIndex() : 1;
        String division = request.getDivision().trim();
        String batch = (request.getBatch() != null && !request.getBatch().trim().isEmpty()) ? request.getBatch().trim() : null;

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

        int total = roster.size();
        int absentCount = 0;
        for (Student s : roster) {
            if (absentSet.contains(s.getId())) {
                absentCount++;
            }
        }
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
                .build();

        lectureSessionRepository.save(session);

        // Upsert AttendanceRecord for every student in the cohort
        for (Student student : roster) {
            AttendanceStatus status = absentSet.contains(student.getId()) ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;

            Optional<AttendanceRecord> existingRec = attendanceRecordRepository
                    .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(
                            student.getId(), subject.getId(), date, sessionIndex
                    );

            AttendanceRecord record;
            if (existingRec.isPresent()) {
                record = existingRec.get();
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

            // Audit history
            AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .attendanceRecord(record)
                    .previousStatus(null)
                    .newStatus(status)
                    .modifiedBy(facultyEmail)
                    .mandatoryReason("Initial attendance submission")
                    .build();
            attendanceHistoryEventRepository.save(event);
        }

        return mapToResponse(session, new ArrayList<>(absentSet));
    }

    @Transactional
    public LectureSessionResponse correctAttendance(String facultyEmail, String sessionId, AttendanceCorrectionRequest request) {
        LectureSession session = lectureSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        User actor = userRepository.findByEmail(facultyEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated faculty user not found"));

        // Faculty may correct only within 48 hours. Class teachers and admins can unlock
        // the session after the window has expired.
        LocalDateTime deadline = session.getSubmittedAt().plusHours(48);
        boolean withinCorrectionWindow = !LocalDateTime.now().isAfter(deadline);
        boolean canUnlock = actor.getRole() == Role.CLASS_TEACHER
                || actor.getRole() == Role.ADMIN
                || actor.getRole() == Role.SUPER_ADMIN;

        if (!withinCorrectionWindow && !canUnlock) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "48-Hour correction window has expired. Only class teachers or administrators can unlock this session."
            );
        }

        // Justification reason validation (>= 10 characters)
        if (request.getCorrectionReason() == null || request.getCorrectionReason().trim().length() < 10) {
            throw new IllegalArgumentException("Correction reason must be at least 10 characters explaining the modification.");
        }

        Set<String> newAbsentSet = new HashSet<>(request.getUpdatedAbsentStudentIds() != null ? request.getUpdatedAbsentStudentIds() : Collections.emptyList());
        List<AttendanceRecord> records = attendanceRecordRepository.findByLectureSessionId(session.getId());

        int absentCount = 0;
        for (AttendanceRecord rec : records) {
            AttendanceStatus oldStatus = rec.getStatus();
            AttendanceStatus newStatus = newAbsentSet.contains(rec.getStudent().getId()) ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;

            if (newStatus == AttendanceStatus.ABSENT) {
                absentCount++;
            }

            if (oldStatus != newStatus) {
                rec.setStatus(newStatus);
                if (request.getTopicCovered() != null && !request.getTopicCovered().trim().isEmpty()) {
                    rec.setRemarks(request.getTopicCovered());
                }
                attendanceRecordRepository.save(rec);

                // Audit history trail
                AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .attendanceRecord(rec)
                        .previousStatus(oldStatus)
                        .newStatus(newStatus)
                        .modifiedBy(facultyEmail)
                        .mandatoryReason(request.getCorrectionReason().trim())
                        .build();
                attendanceHistoryEventRepository.save(event);
            }
        }

        session.setAbsentCount(absentCount);
        session.setPresentCount(session.getTotalStudents() - absentCount);
        if (request.getTopicCovered() != null && !request.getTopicCovered().trim().isEmpty()) {
            session.setTopicCovered(request.getTopicCovered());
        }

        String auditNote = (session.getCorrectionReason() != null ? session.getCorrectionReason() + " | " : "")
                + "[" + LocalDateTime.now() + " by " + facultyEmail + "]: " + request.getCorrectionReason().trim();
        session.setCorrectionReason(auditNote);
        session.setLastModifiedAt(LocalDateTime.now());

        lectureSessionRepository.save(session);

        return mapToResponse(session, new ArrayList<>(newAbsentSet));
    }

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
        boolean canCorrect = LocalDateTime.now().isBefore(session.getSubmittedAt().plusHours(48));

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
                .build();
    }
}
