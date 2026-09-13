package com.vesit.openattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.NotificationEmailStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #11 email delivery: the warning email leaves within 10 seconds of the submission committing
 * and its outcome is recorded against the notification.
 *
 * <p>Deliberately not {@code @Transactional} — the email is sent after commit, and a rolled-back
 * test transaction never commits. It runs against its own in-memory database so the committed rows
 * cannot leak into the transactional suites.
 */
@SpringBootTest(properties = {
        "openattend.notifications.email.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:defaulter_alert_mail;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
public class DefaulterAlertEmailDispatchTest {

    private static final String TEACHER_EMAIL = "prof.mail@ves.ac.in";
    private static final String DIVISION = "D12M";
    private static final Duration DELIVERY_SLA = Duration.ofSeconds(10);

    @MockitoBean
    private JavaMailSender mailSender;

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

    @Autowired
    private NotificationRepository notificationRepository;

    private Subject subject;

    @BeforeEach
    void setup() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));

        User teacher = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(TEACHER_EMAIL)
                .role(Role.FACULTY)
                .build());

        subject = subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("MLX401")
                .name("Operating Systems")
                .totalPlanned(45)
                .build());

        allocationRepository.save(FacultySubjectAllocation.builder()
                .id(UUID.randomUUID().toString())
                .faculty(teacher)
                .subject(subject)
                .division(DIVISION)
                .academicYear("2025-26")
                .semester(6)
                .build());
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        historyEventRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        lectureSessionRepository.deleteAll();
        allocationRepository.deleteAll();
        studentRepository.deleteAll();
        userRepository.deleteAll();
        subjectRepository.deleteAll();
    }

    @Test
    void testWarningEmailIsSentWithinTenSecondsOfSubmission() throws Exception {
        Student student = saveStudentAt75("neha.mail@ves.ac.in", "2024ML01", "Neha Joshi");

        long submittedAt = System.nanoTime();
        submitAbsent(student);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(DELIVERY_SLA.toMillis())).send(sent.capture());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - submittedAt);
        assertTrue(elapsed.compareTo(DELIVERY_SLA) < 0, "email took " + elapsed);

        MimeMessage email = sent.getValue();
        assertEquals("neha.mail@ves.ac.in", ((InternetAddress) email.getAllRecipients()[0]).getAddress());
        assertEquals("[OpenAttend] Attendance warning: MLX401 at 66.67%", email.getSubject());

        String body = textOf(email);
        assertTrue(body.contains("Neha Joshi"), "addressed to the student by name");
        assertTrue(body.contains("MLX401"), "names the subject missed");
        assertTrue(body.contains("66.67%"), "shows the current percentage");
        assertTrue(body.contains("6 / 9"), "shows attended out of conducted");
        assertTrue(body.contains("<strong>3</strong> lectures"), "shows the recovery count in the HTML layout");
        assertTrue(body.contains("Attend the next 3 lectures in a row"), "and in the plain-text alternative");
        assertTrue(body.contains("Overall (all subjects)"),
                "MLX401 is the student's only subject, so overall attendance crossed too and gets its own row");

        // Both alerts — subject and overall — share the one email.
        List<Notification> alerts = awaitEmailOutcome(student, NotificationEmailStatus.SENT);
        assertEquals(2, alerts.size());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        for (Notification notification : alerts) {
            assertNotNull(notification.getEmailedAt());
            assertTrue(Duration.between(notification.getCreatedAt(), notification.getEmailedAt()).compareTo(DELIVERY_SLA) < 0,
                    "emailed_at must fall within 10 seconds of the stored alert");
        }
    }

    @Test
    void testSmtpFailureIsRecordedAndTheInAppAlertStands() throws Exception {
        doThrow(new MailSendException("Connection refused: smtp.ves.ac.in:587"))
                .when(mailSender).send(any(MimeMessage.class));
        Student student = saveStudentAt75("rohan.mail@ves.ac.in", "2024ML02", "Rohan Iyer");

        submitAbsent(student);

        List<Notification> alerts = awaitEmailOutcome(student, NotificationEmailStatus.FAILED);
        assertEquals(2, alerts.size(), "the in-app alerts stay even though the email bounced");
        assertTrue(alerts.stream().allMatch(n -> "threshold_breach".equals(n.getType())));
        assertTrue(alerts.stream().allMatch(n -> n.getEmailedAt() == null));
    }

    @Test
    void testNonInstitutionalAddressIsNeverEmailed() throws Exception {
        Student student = saveStudentAt75("personal.address@gmail.com", "2024ML03", "Kabir Shah");

        submitAbsent(student);

        awaitEmailOutcome(student, NotificationEmailStatus.SKIPPED);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ------------------------------------------------------------------------ fixtures

    /**
     * A student sitting exactly on 75% (6 of 8) in their only subject, so one more absence takes
     * both that subject and their overall attendance to a warning.
     */
    private Student saveStudentAt75(String email, String rollNo, String name) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .role(Role.STUDENT)
                .build());

        Student student = studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .rollNo(rollNo)
                .name(name)
                .division(DIVISION)
                .build());

        for (int i = 0; i < 8; i++) {
            attendanceRecordRepository.save(AttendanceRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .student(student)
                    .subject(subject)
                    .lectureDate(LocalDate.now().minusDays(30 - i))
                    .sessionIndex(1)
                    .status(i < 6 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT)
                    .faculty(TEACHER_EMAIL)
                    .build());
        }
        return student;
    }

    private void submitAbsent(Student student) throws Exception {
        mockMvc.perform(post("/api/v1/faculty/attendance/submit")
                .with(user(TEACHER_EMAIL).roles("FACULTY"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subjectId", subject.getId(),
                        "division", DIVISION,
                        "lectureDate", LocalDate.now().toString(),
                        "sessionIndex", 1,
                        "sessionType", "LECTURE",
                        "topicCovered", "Page replacement",
                        "absentStudentIds", List.of(student.getId())))))
                .andExpect(status().isOk());
    }

    /** Polls until the mailer has recorded {@code expected} on every one of the student's alerts, or the SLA runs out. */
    private List<Notification> awaitEmailOutcome(Student student, NotificationEmailStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + DELIVERY_SLA.toNanos();
        while (true) {
            List<Notification> alerts = notificationRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
            if (!alerts.isEmpty() && alerts.stream().allMatch(n -> n.getEmailStatus() == expected)) {
                return alerts;
            }
            if (System.nanoTime() > deadline) {
                fail("expected every alert to reach email status " + expected + " within " + DELIVERY_SLA + ", found "
                        + alerts.stream().map(n -> n.getType() + "/" + n.getEmailStatus()).toList());
            }
            Thread.sleep(50);
        }
    }

    /** Every decoded text part of the message — the HTML layout and the plain-text alternative. */
    private static String textOf(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        StringBuilder all = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                all.append(textOf(multipart.getBodyPart(i))).append('\n');
            }
        }
        return all.toString();
    }
}
