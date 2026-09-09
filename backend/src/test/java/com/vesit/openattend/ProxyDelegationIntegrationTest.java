package com.vesit.openattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.dto.faculty.AttendanceSubmissionRequest;
import com.vesit.openattend.dto.faculty.ProxyAssignRequest;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.ProxyAssignmentStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proxy / substitute lecture delegation: authorisation for one slot only, and an audit trail
 * that names both the original subject teacher and the substitute who took the lecture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ProxyDelegationIntegrationTest {

    private static final String OWNER = "prof.iyer@ves.ac.in";
    private static final String SUBSTITUTE = "prof.kulkarni@ves.ac.in";
    private static final String OUTSIDER = "prof.nair@ves.ac.in";
    private static final String DIVISION = "D12P";

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
    private ProxyAssignmentRepository proxyAssignmentRepository;

    @Autowired
    private LectureSessionRepository lectureSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceHistoryEventRepository historyEventRepository;

    private Subject subject;
    private User owner;
    private User substitute;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setup() {
        owner = saveFaculty(OWNER, Role.FACULTY);
        substitute = saveFaculty(SUBSTITUTE, Role.FACULTY);
        saveFaculty(OUTSIDER, Role.FACULTY);

        subject = subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("PXY501")
                .name("Compiler Design")
                .totalPlanned(40)
                .build());

        student1 = saveStudent("2024PXY01", "Riya Deshpande");
        student2 = saveStudent("2024PXY02", "Omkar Patil");

        allocationRepository.save(FacultySubjectAllocation.builder()
                .id(UUID.randomUUID().toString())
                .faculty(owner)
                .subject(subject)
                .division(DIVISION)
                .batch(null)
                .academicYear("2025-26")
                .semester(6)
                .build());
    }

    @Test
    @WithMockUser(username = OWNER, roles = "FACULTY")
    void testSubjectTeacherDelegatesLectureAndAuditTrailNamesBothTeachers() throws Exception {
        String responseStr = mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(SUBSTITUTE, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.assignment.originalFacultyEmail").value(OWNER))
                .andExpect(jsonPath("$.assignment.proxyFacultyEmail").value(SUBSTITUTE))
                .andExpect(jsonPath("$.assignment.assignedBy").value(OWNER))
                .andExpect(jsonPath("$.assignment.status").value("ACTIVE"))
                .andExpect(jsonPath("$.assignment.sessionIndex").value(1))
                .andReturn().getResponse().getContentAsString();

        String assignmentId = objectMapper.readTree(responseStr).get("assignment").get("id").asText();
        ProxyAssignment stored = proxyAssignmentRepository.findById(assignmentId).orElseThrow();

        assertEquals(owner.getId(), stored.getOriginalFaculty().getId());
        assertEquals(substitute.getId(), stored.getProxyFaculty().getId());
        assertEquals(ProxyAssignmentStatus.ACTIVE, stored.getStatus());
        assertEquals("Subject teacher is presenting at an IEEE conference", stored.getReason());
        assertNotNull(stored.getCreatedAt());
    }

    @Test
    @WithMockUser(username = SUBSTITUTE, roles = "FACULTY")
    void testProxyTeacherSeesDelegatedLectureOnTheirDashboard() throws Exception {
        delegate(SUBSTITUTE, 1);

        mockMvc.perform(get("/api/v1/faculty/proxy/my-assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.assignments[0].subjectCode").value("PXY501"))
                .andExpect(jsonPath("$.assignments[0].originalFacultyEmail").value(OWNER))
                .andExpect(jsonPath("$.assignments[0].lectureDate").value(LocalDate.now().toString()));

        // The delegated slot is appended to the substitute own subjects for that date.
        mockMvc.perform(get("/api/v1/faculty/my-subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects[?(@.proxy == true)]", hasSize(1)))
                .andExpect(jsonPath("$.subjects[?(@.proxy == true)].subjectCode", contains("PXY501")))
                .andExpect(jsonPath("$.subjects[?(@.proxy == true)].proxyForFacultyEmail", contains(OWNER)));
    }

    @Test
    @WithMockUser(username = SUBSTITUTE, roles = "FACULTY")
    void testProxyTeacherCanMarkDelegatedSlotAndSessionRecordsBothTeachers() throws Exception {
        delegate(SUBSTITUTE, 1);

        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest(1, List.of(student1.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.proxy").value(true))
                .andExpect(jsonPath("$.session.takenByFacultyEmail").value(SUBSTITUTE))
                .andExpect(jsonPath("$.session.originalFacultyEmail").value(OWNER))
                .andExpect(jsonPath("$.session.absentCount").value(1))
                .andExpect(jsonPath("$.session.presentCount").value(1));

        LectureSession session = lectureSessionRepository
                .findBySubjectIdAndDivisionAndSessionDateAndSessionIndexAndBatchIsNull(
                        subject.getId(), DIVISION, LocalDate.now(), 1)
                .orElseThrow();

        // taken_by_faculty_id is the substitute; the original teacher stays on the record.
        assertEquals(substitute.getId(), session.getFaculty().getId());
        assertTrue(session.getIsProxy());
        assertEquals(owner.getId(), session.getOriginalFaculty().getId());
        assertNotNull(session.getProxyAssignment());

        // Every attendance row and its audit event names the substitute who marked it.
        AttendanceRecord record = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(
                        student1.getId(), subject.getId(), LocalDate.now(), 1)
                .orElseThrow();
        assertEquals(SUBSTITUTE, record.getFaculty());

        List<AttendanceHistoryEvent> history = historyEventRepository.findByAttendanceRecordId(record.getId());
        assertFalse(history.isEmpty());
        assertEquals(SUBSTITUTE, history.get(0).getModifiedBy());
    }

    @Test
    @WithMockUser(username = SUBSTITUTE, roles = "FACULTY")
    void testDelegationAuthorisesOnlyTheDelegatedSlot() throws Exception {
        delegate(SUBSTITUTE, 1);

        // Slot 2 was never delegated — the substitute has no authority over it.
        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest(2, List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("no proxy delegation")));

        assertTrue(lectureSessionRepository
                .findBySubjectIdAndDivisionAndSessionDateAndSessionIndexAndBatchIsNull(
                        subject.getId(), DIVISION, LocalDate.now(), 2)
                .isEmpty());
    }

    @Test
    @WithMockUser(username = OUTSIDER, roles = "FACULTY")
    void testFacultyWithoutAllocationOrDelegationCannotMark() throws Exception {
        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest(1, List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = OWNER, roles = "FACULTY")
    void testAllocatedTeacherStillMarksTheirOwnSlotWithoutProxyFlag() throws Exception {
        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest(1, List.of(student2.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.proxy").value(false))
                .andExpect(jsonPath("$.session.takenByFacultyEmail").value(OWNER))
                .andExpect(jsonPath("$.session.originalFacultyEmail").doesNotExist());
    }

    @Test
    @WithMockUser(username = OWNER, roles = "FACULTY")
    void testRevokedDelegationWithdrawsMarkingAuthorisation() throws Exception {
        String assignmentId = delegate(SUBSTITUTE, 1);

        mockMvc.perform(delete("/api/v1/faculty/proxy/" + assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.status").value("REVOKED"))
                .andExpect(jsonPath("$.assignment.revokedBy").value(OWNER));

        ProxyAssignment revoked = proxyAssignmentRepository.findById(assignmentId).orElseThrow();
        assertEquals(ProxyAssignmentStatus.REVOKED, revoked.getStatus());
        assertNotNull(revoked.getRevokedAt());
    }

    @Test
    @WithMockUser(username = SUBSTITUTE, roles = "FACULTY")
    void testSubstituteCannotMarkOnceDelegationIsRevoked() throws Exception {
        String assignmentId = delegate(SUBSTITUTE, 1);
        ProxyAssignment assignment = proxyAssignmentRepository.findById(assignmentId).orElseThrow();
        assignment.setStatus(ProxyAssignmentStatus.REVOKED);
        proxyAssignmentRepository.save(assignment);

        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest(1, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = OUTSIDER, roles = "FACULTY")
    void testUnrelatedFacultyCannotDelegateSomeoneElseLecture() throws Exception {
        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(SUBSTITUTE, 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only the allocated subject teacher")));
    }

    @Test
    @WithMockUser(username = "hod.cmpn@ves.ac.in", roles = "CLASS_TEACHER")
    void testClassTeacherCanDelegateOnBehalfOfTheSubjectTeacher() throws Exception {
        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(SUBSTITUTE, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.originalFacultyEmail").value(OWNER))
                .andExpect(jsonPath("$.assignment.assignedBy").value("hod.cmpn@ves.ac.in"));
    }

    @Test
    @WithMockUser(username = OWNER, roles = "FACULTY")
    void testRepeatingTheSameDelegationIsIdempotent() throws Exception {
        String first = delegate(SUBSTITUTE, 1);
        String second = delegate(SUBSTITUTE, 1);

        assertEquals(first, second);
        assertEquals(1, proxyAssignmentRepository.findAll().size());
    }

    @Test
    @WithMockUser(username = OWNER, roles = "FACULTY")
    void testSecondSubstituteForTheSameSlotIsRejected() throws Exception {
        delegate(SUBSTITUTE, 1);

        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(OUTSIDER, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @WithMockUser(username = OWNER, roles = "FACULTY")
    void testDelegationRejectsStudentsSelfAssignmentPastDatesAndShortReasons() throws Exception {
        User studentUser = student1.getUser();

        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(studentUser.getEmail(), 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Only teaching staff")));

        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(OWNER, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be their own substitute")));

        ProxyAssignRequest backdated = assignRequest(SUBSTITUTE, 1);
        backdated.setLectureDate(LocalDate.now().minusDays(1));
        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(backdated)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("in the past")));

        ProxyAssignRequest thinReason = assignRequest(SUBSTITUTE, 1);
        thinReason.setReason("sick");
        mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(thinReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least 10 characters")));

        assertTrue(proxyAssignmentRepository.findAll().isEmpty());
    }

    /** Posts a delegation as the allocated subject teacher and returns its id. */
    private String delegate(String proxyEmail, Integer sessionIndex) throws Exception {
        String responseStr = mockMvc.perform(post("/api/v1/faculty/proxy/assign")
                .with(user(OWNER).roles("FACULTY"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignRequest(proxyEmail, sessionIndex))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseStr).get("assignment").get("id").asText();
    }

    private ProxyAssignRequest assignRequest(String proxyEmail, Integer sessionIndex) {
        return ProxyAssignRequest.builder()
                .subjectId(subject.getId())
                .division(DIVISION)
                .batch(null)
                .lectureDate(LocalDate.now())
                .sessionIndex(sessionIndex)
                .proxyFacultyEmail(proxyEmail)
                .reason("Subject teacher is presenting at an IEEE conference")
                .build();
    }

    private AttendanceSubmissionRequest submitRequest(int sessionIndex, List<String> absentStudentIds) {
        return AttendanceSubmissionRequest.builder()
                .subjectId(subject.getId())
                .division(DIVISION)
                .batch(null)
                .lectureDate(LocalDate.now())
                .sessionIndex(sessionIndex)
                .sessionType("LECTURE")
                .topicCovered("Syntax directed translation")
                .absentStudentIds(absentStudentIds)
                .build();
    }

    private User saveFaculty(String email, Role role) {
        return userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .role(role)
                .build());
    }

    private Student saveStudent(String rollNo, String name) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(rollNo.toLowerCase() + "@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        return studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .rollNo(rollNo)
                .name(name)
                .division(DIVISION)
                .batch("B1")
                .build());
    }
}
