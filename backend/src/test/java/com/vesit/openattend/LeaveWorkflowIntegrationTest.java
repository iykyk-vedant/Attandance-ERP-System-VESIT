package com.vesit.openattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.LeaveRequestStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The paperless OD / medical leave workflow end to end: a student submits with proof attached, a
 * teacher approves in one click, and the student attendance percentage and defaulter status move
 * as a consequence of that click rather than of a separate recalculation step.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class LeaveWorkflowIntegrationTest {

    private static final String STUDENT_EMAIL = "riya.od@ves.ac.in";
    private static final String OTHER_STUDENT_EMAIL = "omkar.od@ves.ac.in";
    private static final String TEACHER_EMAIL = "prof.od@ves.ac.in";
    private static final String DIVISION = "D12L";

    /** A minimal but genuine PDF header, so the magic-byte check sees what it expects. */
    private static final byte[] PDF_BYTES = "%PDF-1.7\nOD selection letter\n%%EOF".getBytes(StandardCharsets.US_ASCII);

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
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceHistoryEventRepository historyEventRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveProofDocumentRepository proofRepository;

    private Student student;
    private Subject subject;
    private LocalDate hackathonStart;
    private LocalDate hackathonEnd;

    @BeforeEach
    void setup() {
        student = saveStudent(STUDENT_EMAIL, "2024OD01", "Riya Deshpande");
        saveStudent(OTHER_STUDENT_EMAIL, "2024OD02", "Omkar Patil");
        saveFaculty(TEACHER_EMAIL);

        subject = subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("ODX501")
                .name("Distributed Systems")
                .totalPlanned(40)
                .build());

        hackathonStart = LocalDate.now().minusDays(3);
        hackathonEnd = LocalDate.now().minusDays(1);
    }

    @Test
    void testStudentSubmitsPaperlessRequestWithProofAndTeacherSeesItInTheQueue() throws Exception {
        String requestId = submitLeave("OFFICIAL_DUTY", "Smart India Hackathon Grand Finale", hackathonStart, hackathonEnd);

        LeaveRequest stored = leaveRequestRepository.findById(requestId).orElseThrow();
        assertEquals(LeaveRequestStatus.PENDING, stored.getStatus());
        assertEquals(STUDENT_EMAIL, stored.getSubmittedBy());
        assertEquals(3, stored.getDays());

        LeaveProofDocument proof = proofRepository.findByLeaveRequestId(requestId).orElseThrow();
        assertEquals(MediaType.APPLICATION_PDF_VALUE, proof.getContentType());
        assertArrayEquals(PDF_BYTES, proof.getContent());
        assertEquals(PDF_BYTES.length, proof.getSizeBytes());

        mockMvc.perform(get("/api/v1/faculty/leave/requests")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.requests[0].id").value(requestId))
                .andExpect(jsonPath("$.requests[0].rollNo").value("2024OD01"))
                .andExpect(jsonPath("$.requests[0].eventName").value("Smart India Hackathon Grand Finale"))
                .andExpect(jsonPath("$.requests[0].status").value("PENDING"))
                .andExpect(jsonPath("$.requests[0].proof.fileName").value("od-letter.pdf"))
                .andExpect(jsonPath("$.requests[0].proof.downloadUrl")
                        .value("/api/v1/leave/requests/" + requestId + "/proof"));

        // The proof itself is retrievable by the reviewing teacher - that is what replaces the letter.
        mockMvc.perform(get("/api/v1/leave/requests/" + requestId + "/proof")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    void testApprovalConvertsAbsencesToDutyPresentAndRecalculatesPercentage() throws Exception {
        // 10 lectures, 3 of them missed for the hackathon: 70% - a defaulter.
        markAttendance(hackathonStart.minusDays(10), 7, AttendanceStatus.PRESENT);
        markAttendance(hackathonStart, 3, AttendanceStatus.ABSENT);

        String requestId = submitLeave("OFFICIAL_DUTY", "Smart India Hackathon Grand Finale", hackathonStart, hackathonEnd);

        mockMvc.perform(post("/api/v1/faculty/leave/requests/" + requestId + "/approve")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisionNote\":\"Verified against the official selection letter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.request.decidedBy").value(TEACHER_EMAIL))
                .andExpect(jsonPath("$.request.sessionsAdjusted").value(3))
                .andExpect(jsonPath("$.attendanceImpact.sessionsAdjusted").value(3))
                // The denominator is untouched: OD credits a lecture, it does not remove it.
                .andExpect(jsonPath("$.attendanceImpact.totalConducted").value(10))
                .andExpect(jsonPath("$.attendanceImpact.attendedBefore").value(7))
                .andExpect(jsonPath("$.attendanceImpact.attendedAfter").value(10))
                .andExpect(jsonPath("$.attendanceImpact.percentageBefore").value(70.00))
                .andExpect(jsonPath("$.attendanceImpact.percentageAfter").value(100.00))
                .andExpect(jsonPath("$.attendanceImpact.defaulterBefore").value(true))
                .andExpect(jsonPath("$.attendanceImpact.defaulterAfter").value(false))
                .andExpect(jsonPath("$.attendanceImpact.clearedFromDefaulterList").value(true));

        List<AttendanceRecord> inWindow = attendanceRecordRepository
                .findByStudentIdAndLectureDateBetweenOrderByLectureDateAscSessionIndexAsc(
                        student.getId(), hackathonStart, hackathonEnd);
        assertEquals(3, inWindow.size());
        assertTrue(inWindow.stream().allMatch(r -> r.getStatus() == AttendanceStatus.DUTY_PRESENT),
                "every absence inside the leave window should be credited as duty-present");

        // Every flip leaves an audit event naming the approver, as a correction does.
        for (AttendanceRecord record : inWindow) {
            List<AttendanceHistoryEvent> events = historyEventRepository.findAll().stream()
                    .filter(e -> e.getAttendanceRecord().getId().equals(record.getId()))
                    .filter(e -> e.getNewStatus() == AttendanceStatus.DUTY_PRESENT)
                    .toList();
            assertEquals(1, events.size(), "one audit event per flipped record");
            assertEquals(TEACHER_EMAIL, events.get(0).getModifiedBy());
            assertEquals(AttendanceStatus.ABSENT, events.get(0).getPreviousStatus());
            assertTrue(events.get(0).getMandatoryReason().contains(requestId));
        }
    }

    @Test
    void testApprovalRemovesStudentFromTheDefaulterList() throws Exception {
        markAttendance(hackathonStart.minusDays(10), 7, AttendanceStatus.PRESENT);
        markAttendance(hackathonStart, 3, AttendanceStatus.ABSENT);

        // 70% before the decision: the class teacher sees them on the notice.
        mockMvc.perform(get("/api/v1/reports/defaulters")
                .param("division", DIVISION)
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaulterCount").value(1))
                .andExpect(jsonPath("$.defaulters[0].rollNo").value("2024OD01"))
                .andExpect(jsonPath("$.defaulters[0].overallPercentage").value(70.00));

        String requestId = submitLeave("MEDICAL", "Viral fever - hospitalised", hackathonStart, hackathonStart);
        approve(requestId);

        // Same report, same window, run again: the OD now counts and the list is empty.
        mockMvc.perform(get("/api/v1/reports/defaulters")
                .param("division", DIVISION)
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaulterCount").value(0))
                .andExpect(jsonPath("$.studentsEvaluated").value(1));
    }

    @Test
    void testLecturesMarkedDuringAnApprovedLeaveAreCreditedAutomatically() throws Exception {
        // Approved ahead of the event, before any attendance exists for those days.
        LocalDate fest = LocalDate.now().plusDays(1);
        String requestId = submitLeave("OFFICIAL_DUTY", "UTSAV cultural fest - organising committee", fest, fest);

        mockMvc.perform(post("/api/v1/faculty/leave/requests/" + requestId + "/approve")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.sessionsAdjusted").value(0));

        // The teacher then marks the lecture with the student down as absent.
        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "subjectId", subject.getId(),
                        "division", DIVISION,
                        "lectureDate", fest.toString(),
                        "sessionIndex", 1,
                        "sessionType", "LECTURE",
                        "topicCovered", "Consensus protocols",
                        "absentStudentIds", List.of(student.getId())))))
                .andExpect(status().isOk())
                // The absence is credited as duty, so the session summary counts them present.
                .andExpect(jsonPath("$.session.absentCount").value(0))
                .andExpect(jsonPath("$.session.presentCount").value(2));

        AttendanceRecord record = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(student.getId(), subject.getId(), fest, 1)
                .orElseThrow();
        assertEquals(AttendanceStatus.DUTY_PRESENT, record.getStatus(),
                "an OD approved in advance must apply to lectures marked while the student is away");
    }

    @Test
    void testRejectionRequiresAReasonAndLeavesAttendanceUntouched() throws Exception {
        markAttendance(hackathonStart, 2, AttendanceStatus.ABSENT);
        String requestId = submitLeave("OFFICIAL_DUTY", "Inter-college sports meet", hackathonStart, hackathonStart);

        mockMvc.perform(post("/api/v1/faculty/leave/requests/" + requestId + "/reject")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisionNote\":\"short\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/faculty/leave/requests/" + requestId + "/reject")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisionNote\":\"The attached letter is for a different student\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("REJECTED"))
                .andExpect(jsonPath("$.attendanceImpact").doesNotExist());

        assertTrue(attendanceRecordRepository
                        .findByStudentIdAndLectureDateBetweenOrderByLectureDateAscSessionIndexAsc(
                                student.getId(), hackathonStart, hackathonStart)
                        .stream().allMatch(r -> r.getStatus() == AttendanceStatus.ABSENT),
                "a rejected request must not touch attendance");
    }

    @Test
    void testDoubleTappedApproveDoesNotDoubleCount() throws Exception {
        markAttendance(hackathonStart.minusDays(10), 7, AttendanceStatus.PRESENT);
        markAttendance(hackathonStart, 3, AttendanceStatus.ABSENT);

        String requestId = submitLeave("OFFICIAL_DUTY", "Pravega technical fest", hackathonStart, hackathonEnd);
        approve(requestId);

        mockMvc.perform(post("/api/v1/faculty/leave/requests/" + requestId + "/approve")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.request.sessionsAdjusted").value(3))
                .andExpect(jsonPath("$.attendanceImpact").doesNotExist());

        long dutyEvents = historyEventRepository.findAll().stream()
                .filter(e -> e.getNewStatus() == AttendanceStatus.DUTY_PRESENT)
                .count();
        assertEquals(3, dutyEvents, "a repeated approval must not write a second set of audit events");
    }

    @Test
    void testNonImageNonPdfProofIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/leave/requests")
                .file(new MockMultipartFile("proof", "payload.pdf", MediaType.APPLICATION_PDF_VALUE,
                        "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                .param("leaveType", "OFFICIAL_DUTY")
                .param("eventName", "Hackathon")
                .param("fromDate", hackathonStart.toString())
                .param("toDate", hackathonStart.toString())
                .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isBadRequest());

        assertTrue(leaveRequestRepository.findAll().isEmpty(),
                "a request whose proof was rejected must not reach the approval queue");
    }

    @Test
    void testAnotherStudentCannotOpenSomeoneElsesProof() throws Exception {
        String requestId = submitLeave("MEDICAL", "Dengue - admitted", hackathonStart, hackathonStart);

        mockMvc.perform(get("/api/v1/leave/requests/" + requestId + "/proof")
                .with(user(OTHER_STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isForbidden());

        // Anonymous: the stateless chain has no entry point, so Spring answers 403 rather than 401.
        mockMvc.perform(get("/api/v1/leave/requests/" + requestId + "/proof"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testOverlappingRequestIsRefused() throws Exception {
        submitLeave("OFFICIAL_DUTY", "Sphurti sports meet", hackathonStart, hackathonEnd);

        mockMvc.perform(multipart("/api/v1/leave/requests")
                .file(proofPart())
                .param("leaveType", "MEDICAL")
                .param("eventName", "Fever")
                .param("fromDate", hackathonEnd.toString())
                .param("toDate", hackathonEnd.plusDays(1).toString())
                .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void testStudentSeesTheirOwnRequestsOnly() throws Exception {
        String requestId = submitLeave("OFFICIAL_DUTY", "Sphurti sports meet", hackathonStart, hackathonEnd);

        mockMvc.perform(get("/api/v1/leave/requests/mine")
                .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.requests[0].id").value(requestId));

        mockMvc.perform(get("/api/v1/leave/requests/mine")
                .with(user(OTHER_STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void testApprovalRefreshesTheSessionSummaryTheTeacherReads() throws Exception {
        LocalDate lectureDate = LocalDate.now().minusDays(1);

        // The lecture is marked first, with the student down as absent.
        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "subjectId", subject.getId(),
                        "division", DIVISION,
                        "lectureDate", lectureDate.toString(),
                        "sessionIndex", 1,
                        "sessionType", "LECTURE",
                        "topicCovered", "Vector clocks",
                        "absentStudentIds", List.of(student.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.absentCount").value(1))
                .andExpect(jsonPath("$.session.presentCount").value(1));

        // The certificate arrives afterwards and is approved.
        String requestId = submitLeave("MEDICAL", "Migraine - GP certificate", lectureDate, lectureDate);
        approve(requestId);

        // The session summary must not still say the student was absent.
        mockMvc.perform(get("/api/v1/faculty/sessions")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].absentCount").value(0))
                .andExpect(jsonPath("$.sessions[0].presentCount").value(2));
    }

    // ------------------------------------------------------------------------ fixtures

    private MockMultipartFile proofPart() {
        return new MockMultipartFile("proof", "od-letter.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_BYTES);
    }

    private String submitLeave(String type, String eventName, LocalDate from, LocalDate to) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/leave/requests")
                .file(proofPart())
                .param("leaveType", type)
                .param("eventName", eventName)
                .param("fromDate", from.toString())
                .param("toDate", to.toString())
                .param("reason", "Selected to represent the college")
                .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.request.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("request").get("id").asText();
    }

    private void approve(String requestId) throws Exception {
        mockMvc.perform(post("/api/v1/faculty/leave/requests/" + requestId + "/approve")
                .with(user(TEACHER_EMAIL).roles("CLASS_TEACHER")))
                .andExpect(status().isOk());
    }

    /** Writes {@code count} consecutive daily records for the student, starting at {@code start}. */
    private void markAttendance(LocalDate start, int count, AttendanceStatus status) {
        for (int i = 0; i < count; i++) {
            attendanceRecordRepository.save(AttendanceRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .student(student)
                    .subject(subject)
                    .lectureDate(start.plusDays(i))
                    .sessionIndex(1)
                    .status(status)
                    .faculty(TEACHER_EMAIL)
                    .build());
        }
    }

    private Student saveStudent(String email, String rollNo, String name) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash("{noop}test")
                .role(Role.STUDENT)
                .isActive(true)
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

    private User saveFaculty(String email) {
        return userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash("{noop}test")
                .role(Role.CLASS_TEACHER)
                .isActive(true)
                .build());
    }
}
