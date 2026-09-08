package com.vesit.openattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.dto.faculty.AttendanceCorrectionRequest;
import com.vesit.openattend.dto.faculty.AttendanceSubmissionRequest;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class FacultyAttendanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private FacultySubjectAllocationRepository allocationRepository;

    @Autowired
    private LectureSessionRepository lectureSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceHistoryEventRepository historyEventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User facultyUser;
    private Subject subject;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setup() {
        facultyUser = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email("prof.sharma@ves.ac.in")
                .role(Role.FACULTY)
                .build());

        subject = subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("FAC401")
                .name("Advanced Algorithms")
                .totalPlanned(40)
                .build());

        User u1 = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email("std1@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        student1 = studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(u1)
                .rollNo("2024FAC01")
                .name("Alice Smith")
                .division("D12A")
                .batch("B1")
                .build());

        User u2 = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email("std2@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        student2 = studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(u2)
                .rollNo("2024FAC02")
                .name("Bob Jones")
                .division("D12A")
                .batch("B1")
                .build());

        allocationRepository.save(FacultySubjectAllocation.builder()
                .id(UUID.randomUUID().toString())
                .faculty(facultyUser)
                .subject(subject)
                .division("D12A")
                .batch(null)
                .academicYear("2025-26")
                .semester(6)
                .build());
    }

    @Test
    @WithMockUser(username = "prof.sharma@ves.ac.in", roles = "FACULTY")
    void testGetMySubjectsAndRoster() throws Exception {
        mockMvc.perform(get("/api/v1/faculty/my-subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subjects", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.subjects[0].subjectCode").value("FAC401"));

        mockMvc.perform(get("/api/v1/faculty/roster")
                .param("subjectId", subject.getId())
                .param("division", "D12A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.roster", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @WithMockUser(username = "prof.sharma@ves.ac.in", roles = "FACULTY")
    void testAbsenteesAttendanceSubmissionAndHistory() throws Exception {
        // Fast marking: student1 is absent, student2 is present (default)
        AttendanceSubmissionRequest req = AttendanceSubmissionRequest.builder()
                .subjectId(subject.getId())
                .division("D12A")
                .batch(null)
                .lectureDate(LocalDate.now())
                .sessionIndex(1)
                .sessionType("LECTURE")
                .topicCovered("Dynamic Programming Foundations")
                .absentStudentIds(List.of(student1.getId()))
                .build();

        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session.presentCount").value(1))
                .andExpect(jsonPath("$.session.absentCount").value(1));

        // Verify records in DB
        AttendanceRecord rec1 = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(student1.getId(), subject.getId(), LocalDate.now(), 1)
                .orElseThrow();
        assertEquals(AttendanceStatus.ABSENT, rec1.getStatus());

        AttendanceRecord rec2 = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(student2.getId(), subject.getId(), LocalDate.now(), 1)
                .orElseThrow();
        assertEquals(AttendanceStatus.PRESENT, rec2.getStatus());

        // Verify history events created
        List<AttendanceHistoryEvent> history1 = historyEventRepository.findByAttendanceRecordId(rec1.getId());
        assertFalse(history1.isEmpty());
        assertEquals(AttendanceStatus.ABSENT, history1.get(0).getNewStatus());
    }

    @Test
    @WithMockUser(username = "prof.sharma@ves.ac.in", roles = "FACULTY")
    void testCorrectionWindowEnforcesReasonAndUpdatesHistory() throws Exception {
        // Initial submission
        AttendanceSubmissionRequest subReq = AttendanceSubmissionRequest.builder()
                .subjectId(subject.getId())
                .division("D12A")
                .batch(null)
                .lectureDate(LocalDate.now())
                .sessionIndex(2)
                .sessionType("LECTURE")
                .topicCovered("Graph Algorithms")
                .absentStudentIds(List.of(student1.getId(), student2.getId()))
                .build();

        String responseStr = mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(responseStr).get("session").get("id").asText();

        // Attempt correction with too short reason (< 10 chars) -> 400 Bad Request
        AttendanceCorrectionRequest shortReasonReq = AttendanceCorrectionRequest.builder()
                .correctionReason("Wrong")
                .updatedAbsentStudentIds(List.of(student1.getId()))
                .build();

        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortReasonReq)))
                .andExpect(status().isBadRequest());

        // Valid correction: mark student2 as present with valid justification
        AttendanceCorrectionRequest validReq = AttendanceCorrectionRequest.builder()
                .correctionReason("Student arrived late with approved medical note from Dean")
                .updatedAbsentStudentIds(List.of(student1.getId())) // student2 no longer absent
                .topicCovered("Graph Algorithms - Bellman Ford")
                .build();

        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.presentCount").value(1))
                .andExpect(jsonPath("$.session.absentCount").value(1));

        // Verify student2 status updated to PRESENT
        AttendanceRecord rec2 = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(student2.getId(), subject.getId(), LocalDate.now(), 2)
                .orElseThrow();
        assertEquals(AttendanceStatus.PRESENT, rec2.getStatus());

        // Verify history has audit trail
        List<AttendanceHistoryEvent> history2 = historyEventRepository.findByAttendanceRecordIdOrderByChangedAtAsc(rec2.getId());
        assertEquals(2, history2.size());

        AttendanceHistoryEvent correctionEvent = correctionEventFor(rec2.getId());
        assertEquals(AttendanceStatus.ABSENT, correctionEvent.getPreviousStatus());
        assertEquals(AttendanceStatus.PRESENT, correctionEvent.getNewStatus());
        assertEquals("prof.sharma@ves.ac.in", correctionEvent.getModifiedBy());
        assertEquals("Student arrived late with approved medical note from Dean", correctionEvent.getMandatoryReason());
        assertNotNull(correctionEvent.getChangedAt());
    }

    @Test
    @WithMockUser(username = "prof.sharma@ves.ac.in", roles = "FACULTY")
    void testStandardFacultyBlockedAfterCorrectionWindow() throws Exception {
        String sessionId = createBackdatedSession(3, 49);

        AttendanceCorrectionRequest req = AttendanceCorrectionRequest.builder()
                .correctionReason("Marked the wrong batch during the practical slot")
                .updatedAbsentStudentIds(List.of())
                .build();

        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("48-Hour correction window has expired")));

        // Locked session must remain untouched
        AttendanceRecord rec1 = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(student1.getId(), subject.getId(), LocalDate.now(), 3)
                .orElseThrow();
        assertEquals(AttendanceStatus.ABSENT, rec1.getStatus());
    }

    @Test
    @WithMockUser(username = "hod.cmpn@ves.ac.in", roles = "CLASS_TEACHER")
    void testClassTeacherCanUnlockExpiredSession() throws Exception {
        String sessionId = createBackdatedSession(4, 72);

        AttendanceCorrectionRequest req = AttendanceCorrectionRequest.builder()
                .correctionReason("Verified medical certificate submitted to the department office")
                .updatedAbsentStudentIds(List.of())
                .build();

        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.absentCount").value(0))
                .andExpect(jsonPath("$.session.presentCount").value(2));

        AttendanceRecord rec1 = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(student1.getId(), subject.getId(), LocalDate.now(), 4)
                .orElseThrow();
        assertEquals(AttendanceStatus.PRESENT, rec1.getStatus());

        // Audit row records the unlocking caller, the reason and the timestamp
        AttendanceHistoryEvent unlockEvent = correctionEventFor(rec1.getId());
        assertEquals("hod.cmpn@ves.ac.in", unlockEvent.getModifiedBy());
        assertEquals("Verified medical certificate submitted to the department office", unlockEvent.getMandatoryReason());
        assertEquals(AttendanceStatus.ABSENT, unlockEvent.getPreviousStatus());
        assertEquals(AttendanceStatus.PRESENT, unlockEvent.getNewStatus());
        assertNotNull(unlockEvent.getChangedAt());
    }

    /** The single audit row produced by a correction — corrections always carry a previous status. */
    private AttendanceHistoryEvent correctionEventFor(String attendanceRecordId) {
        List<AttendanceHistoryEvent> corrections = historyEventRepository
                .findByAttendanceRecordIdOrderByChangedAtAsc(attendanceRecordId).stream()
                .filter(e -> e.getPreviousStatus() != null)
                .toList();
        assertEquals(1, corrections.size(), "expected exactly one correction audit row");
        return corrections.get(0);
    }

    @Test
    @WithMockUser(username = "admin@ves.ac.in", roles = "ADMIN")
    void testAdminCanUnlockExpiredSession() throws Exception {
        String sessionId = createBackdatedSession(5, 200);

        AttendanceCorrectionRequest req = AttendanceCorrectionRequest.builder()
                .correctionReason("Registrar approved retrospective attendance amendment")
                .updatedAbsentStudentIds(List.of(student2.getId()))
                .build();

        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.absentCount").value(1));
    }

    @Test
    @WithMockUser(username = "admin@ves.ac.in", roles = "ADMIN")
    void testUnlockStillRequiresMandatoryReason() throws Exception {
        String sessionId = createBackdatedSession(6, 96);

        AttendanceCorrectionRequest req = AttendanceCorrectionRequest.builder()
                .correctionReason("typo")
                .updatedAbsentStudentIds(List.of())
                .build();

        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Persists a session (student1 ABSENT, student2 PRESENT) and rewinds its submitted_at
     * by {@code hoursAgo} so the 48-hour window can be exercised without waiting.
     */
    private String createBackdatedSession(int sessionIndex, long hoursAgo) {
        LectureSession session = lectureSessionRepository.save(LectureSession.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .faculty(facultyUser)
                .division("D12A")
                .batch(null)
                .sessionDate(LocalDate.now())
                .sessionIndex(sessionIndex)
                .sessionType("LECTURE")
                .topicCovered("Backdated session")
                .totalStudents(2)
                .presentCount(1)
                .absentCount(1)
                .build());

        persistRecord(session, student1, AttendanceStatus.ABSENT, sessionIndex);
        persistRecord(session, student2, AttendanceStatus.PRESENT, sessionIndex);

        entityManager.flush();
        entityManager.createNativeQuery("UPDATE lecture_sessions SET submitted_at = ? WHERE id = ?")
                .setParameter(1, LocalDateTime.now().minusHours(hoursAgo))
                .setParameter(2, session.getId())
                .executeUpdate();
        entityManager.clear();

        return session.getId();
    }

    private void persistRecord(LectureSession session, Student student, AttendanceStatus status, int sessionIndex) {
        AttendanceRecord record = attendanceRecordRepository.save(AttendanceRecord.builder()
                .id(UUID.randomUUID().toString())
                .student(student)
                .subject(subject)
                .lectureSession(session)
                .lectureDate(LocalDate.now())
                .sessionIndex(sessionIndex)
                .status(status)
                .faculty(facultyUser.getEmail())
                .build());

        historyEventRepository.save(AttendanceHistoryEvent.builder()
                .id(UUID.randomUUID().toString())
                .attendanceRecord(record)
                .previousStatus(null)
                .newStatus(status)
                .modifiedBy(facultyUser.getEmail())
                .build());
    }

    @Test
    @WithMockUser(username = "student@ves.ac.in", roles = "STUDENT")
    void testStudentRoleForbiddenOnFacultyEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/faculty/my-subjects"))
                .andExpect(status().isForbidden());
    }
}
