package com.vesit.openattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.dto.notification.NotificationListResponse;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.NotificationEmailStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import com.vesit.openattend.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #11: a roll call that takes a student below 75% stores a warning, below 65% a critical
 * alert, and the 7-day cooldown keeps consecutive lectures from spamming them.
 *
 * <p>Runs inside a rolled-back transaction, so the after-commit email never fires here; delivery
 * is covered by {@link DefaulterAlertEmailDispatchTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DefaulterAlertIntegrationTest {

    private static final String TEACHER_EMAIL = "prof.alerts@ves.ac.in";
    private static final String DIVISION = "D12N";
    private static final Set<String> ALERT_TYPES = Set.of("threshold_breach", "threshold_critical");

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
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    private Student student;
    private Student classmate;
    private Subject dbms;
    private Subject networks;

    /** Seeded history sits on past dates; each seeded or submitted lecture takes the next one. */
    private LocalDate nextLectureDate;

    @BeforeEach
    void setup() {
        User teacher = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(TEACHER_EMAIL)
                .role(Role.FACULTY)
                .build());

        student = saveStudent("aarav.alerts@ves.ac.in", "2024AL01", "Aarav Kulkarni");
        classmate = saveStudent("isha.alerts@ves.ac.in", "2024AL02", "Isha Menon");

        dbms = saveSubject("DAX401", "Database Management Systems");
        networks = saveSubject("DAX402", "Computer Networks");

        for (Subject subject : List.of(dbms, networks)) {
            allocationRepository.save(FacultySubjectAllocation.builder()
                    .id(UUID.randomUUID().toString())
                    .faculty(teacher)
                    .subject(subject)
                    .division(DIVISION)
                    .academicYear("2025-26")
                    .semester(6)
                    .build());
        }

        nextLectureDate = LocalDate.now().minusDays(200);
    }

    @Test
    void testDropBelow75StoresWarningWithPercentageAndRecoveryCount() throws Exception {
        seed(dbms, 6, 2);       // 6 / 8  = 75.00% — exactly on the line, not a defaulter
        seed(networks, 20, 0);  // keeps overall attendance well clear

        submit(dbms, List.of(student.getId()));  // 6 / 9 = 66.67%

        List<Notification> alerts = alertsFor(student);
        assertEquals(1, alerts.size(), "one subject warning; overall stays above 75%");

        Notification warning = alerts.get(0);
        assertEquals("threshold_breach", warning.getType());
        assertEquals(dbms.getId(), warning.getSubject().getId());
        assertFalse(warning.getIsRead());
        assertEquals(NotificationEmailStatus.PENDING, warning.getEmailStatus(),
                "the email is queued for after commit, never sent from inside the transaction");
        assertTrue(warning.getMessage().contains("DAX401"), warning.getMessage());
        assertTrue(warning.getMessage().contains("66.67%"), warning.getMessage());
        assertTrue(warning.getMessage().contains("6 of 9 lectures"), warning.getMessage());
        // ceil(3T - 4A) = 27 - 24 = 3, and (6 + 3) / (9 + 3) is exactly 75%.
        assertTrue(warning.getMessage().contains("next 3 lecture(s)"), warning.getMessage());

        assertTrue(alertsFor(classmate).isEmpty(), "a student marked present is never evaluated");
    }

    @Test
    void testStudentAlreadyBelow75IsNotWarnedOnEveryLecture() throws Exception {
        seed(dbms, 14, 6);      // 70.00%
        seed(networks, 20, 0);

        submit(dbms, List.of(student.getId()));  // 14 / 21 = 66.67%, still above 65%

        assertTrue(alertsFor(student).isEmpty(), "alerts fire on crossing the line, not on staying below it");
    }

    @Test
    void testSevenDayCooldownSuppressesRepeatWarning() throws Exception {
        seed(dbms, 6, 2);
        seed(networks, 20, 0);

        submit(dbms, List.of(student.getId()));  // 6 / 9  — warning
        recoverTo75();                            // 9 / 12 — back on the line
        submit(dbms, List.of(student.getId()));  // 9 / 13 = 69.23% — crossed again

        List<Notification> alerts = alertsFor(student);
        assertEquals(1, alerts.size(), "a second warning inside 7 days must be throttled");
        assertEquals("threshold_breach", alerts.get(0).getType());
    }

    @Test
    void testWarningRepeatsOnceTheCooldownHasExpired() throws Exception {
        seed(dbms, 6, 2);
        seed(networks, 20, 0);

        submit(dbms, List.of(student.getId()));
        backdateAlerts(8);

        recoverTo75();
        submit(dbms, List.of(student.getId()));

        List<Notification> alerts = alertsFor(student);
        assertEquals(2, alerts.size(), "the cooldown is 7 days, so an 8-day-old warning no longer holds one back");
        assertTrue(alerts.stream().allMatch(n -> "threshold_breach".equals(n.getType())));
    }

    @Test
    void testCriticalAlertBelow65BypassesTheWarningCooldown() throws Exception {
        seed(dbms, 13, 4);      // 76.47%
        seed(networks, 60, 0);

        submit(dbms, List.of(student.getId()));  // 13 / 18 = 72.22% — warning
        submit(dbms, List.of(student.getId()));  // 13 / 19 = 68.42% — no new crossing
        submit(dbms, List.of(student.getId()));  // 13 / 20 = 65.00% — on the line
        submit(dbms, List.of(student.getId()));  // 13 / 21 = 61.90% — critical

        List<Notification> alerts = alertsFor(student);
        assertEquals(List.of("threshold_breach", "threshold_critical"),
                alerts.stream().sorted(Comparator.comparing(Notification::getCreatedAt)).map(Notification::getType).toList());

        Notification critical = alerts.stream().filter(n -> "threshold_critical".equals(n.getType())).findFirst().orElseThrow();
        assertTrue(critical.getMessage().startsWith("Critical:"), critical.getMessage());
        assertTrue(critical.getMessage().contains("61.90%"), critical.getMessage());
        // ceil(3 * 21 - 4 * 13) = 11, and (13 + 11) / (21 + 11) is exactly 75%.
        assertTrue(critical.getMessage().contains("next 11 lecture(s)"), critical.getMessage());

        NotificationListResponse feed = notificationService.getNotifications(student.getRollNo(), false);
        assertTrue(feed.getNotifications().stream().anyMatch(n -> "Critical Attendance Alert".equals(n.getTitle())));
        assertTrue(feed.getNotifications().stream().anyMatch(n -> "Attendance Threshold Warning".equals(n.getTitle())));
    }

    @Test
    void testCriticalAlertIsItselfSentAtMostOnceAWeek() throws Exception {
        seed(dbms, 2, 1);       // 66.67%
        seed(networks, 30, 0);

        submit(dbms, List.of(student.getId()));  // 2 / 4 = 50%    — critical
        submit(dbms, List.of());                 // 3 / 5 = 60%
        submit(dbms, List.of());                 // 4 / 6 = 66.67% — back above 65%
        submit(dbms, List.of(student.getId()));  // 4 / 7 = 57.14% — crossed 65% again

        List<Notification> alerts = alertsFor(student);
        assertEquals(1, alerts.size(), "critical alerts bypass the warning cooldown but are throttled among themselves");
        assertEquals("threshold_critical", alerts.get(0).getType());
    }

    @Test
    void testAggregateDropStoresAnAlertWithNoSubject() throws Exception {
        seed(dbms, 10, 0);      // 100%
        seed(networks, 5, 5);   // 50% — already a subject defaulter, overall 15 / 20 = 75.00%

        submit(dbms, List.of(student.getId()));  // DAX401 10 / 11 = 90.91%; overall 15 / 21 = 71.43%

        List<Notification> alerts = alertsFor(student);
        assertEquals(1, alerts.size(), "only the overall percentage crossed the line");

        Notification aggregate = alerts.get(0);
        assertEquals("threshold_breach", aggregate.getType());
        assertNull(aggregate.getSubject(), "an aggregate alert is not tied to one subject");
        assertTrue(aggregate.getMessage().startsWith("Overall attendance has fallen to 71.43%"), aggregate.getMessage());
        assertTrue(aggregate.getMessage().contains("after missing DAX401"), aggregate.getMessage());
        assertTrue(aggregate.getMessage().contains("next 3 lecture(s)"), aggregate.getMessage());
    }

    @Test
    void testCrossingBothBoundariesInOneLectureRaisesOnlyTheCriticalAlert() throws Exception {
        seed(dbms, 3, 1);       // 75.00%, and the student's only subject: overall is the same 75.00%

        submit(dbms, List.of(student.getId()));  // 3 / 5 = 60% — both scopes cross 65% at once

        List<Notification> alerts = alertsFor(student);
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().allMatch(n -> "threshold_critical".equals(n.getType())),
                "crossing both boundaries in one lecture raises the more severe alert only");
        assertEquals(1, alerts.stream().filter(n -> n.getSubject() == null).count());
    }

    @Test
    void testCorrectionThatMarksAStudentAbsentRaisesTheAlert() throws Exception {
        seed(dbms, 6, 2);
        seed(networks, 20, 0);

        // Marked present first: 7 / 9 = 77.78%.
        String sessionId = submit(dbms, List.of());
        assertTrue(alertsFor(student).isEmpty());

        // The teacher then corrects the roll call: 6 / 9 = 66.67%.
        mockMvc.perform(put("/api/v1/faculty/attendance/" + sessionId + "/correct")
                .with(user(TEACHER_EMAIL).roles("FACULTY"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "correctionReason", "Proxy attendance spotted on the sign-in sheet",
                        "updatedAbsentStudentIds", List.of(student.getId())))))
                .andExpect(status().isOk());

        List<Notification> alerts = alertsFor(student);
        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0).getMessage().contains("66.67%"), alerts.get(0).getMessage());
    }

    @Test
    void testFirstRecordedLectureMissedStartsFromGoodStanding() throws Exception {
        submit(dbms, List.of(student.getId()));  // no history: 0 / 1

        List<Notification> alerts = alertsFor(student);
        assertEquals(2, alerts.size(), "a student with no history has not been warned yet, so the first absence counts as a drop");
        assertTrue(alerts.stream().allMatch(n -> "threshold_critical".equals(n.getType())));
    }

    // ------------------------------------------------------------------------ fixtures

    /** Three present lectures take 6 / 9 back up to 9 / 12 = 75.00%. */
    private void recoverTo75() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(dbms, List.of());
        }
    }

    /** Submits the next lecture for the whole division with {@code absentIds} marked absent. */
    private String submit(Subject subject, List<String> absentIds) throws Exception {
        String body = mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .with(user(TEACHER_EMAIL).roles("FACULTY"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subjectId", subject.getId(),
                        "division", DIVISION,
                        "lectureDate", takeLectureDate().toString(),
                        "sessionIndex", 1,
                        "sessionType", "LECTURE",
                        "topicCovered", "Lecture",
                        "absentStudentIds", absentIds))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("session").get("id").asText();
    }

    /** Writes the student's past record directly, without a session or any alert evaluation. */
    private void seed(Subject subject, int present, int absent) {
        for (int i = 0; i < present + absent; i++) {
            attendanceRecordRepository.save(AttendanceRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .student(student)
                    .subject(subject)
                    .lectureDate(takeLectureDate())
                    .sessionIndex(1)
                    .status(i < present ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT)
                    .faculty(TEACHER_EMAIL)
                    .build());
        }
    }

    private LocalDate takeLectureDate() {
        LocalDate date = nextLectureDate;
        nextLectureDate = nextLectureDate.plusDays(1);
        return date;
    }

    private List<Notification> alertsFor(Student s) {
        return notificationRepository.findByStudentIdOrderByCreatedAtDesc(s.getId()).stream()
                .filter(n -> ALERT_TYPES.contains(n.getType()))
                .toList();
    }

    private void backdateAlerts(int days) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE notifications SET created_at = ? WHERE student_id = ?")
                .setParameter(1, LocalDateTime.now().minusDays(days))
                .setParameter(2, student.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private Student saveStudent(String email, String rollNo, String name) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .role(Role.STUDENT)
                .build());

        return studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .rollNo(rollNo)
                .name(name)
                .division(DIVISION)
                .build());
    }

    private Subject saveSubject(String code, String name) {
        return subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code(code)
                .name(name)
                .totalPlanned(45)
                .build());
    }
}
