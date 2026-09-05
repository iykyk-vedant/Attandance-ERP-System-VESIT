package com.vesit.openattend;

import com.vesit.openattend.dto.notification.NotificationListResponse;
import com.vesit.openattend.entity.Notification;
import com.vesit.openattend.entity.Student;
import com.vesit.openattend.entity.Subject;
import com.vesit.openattend.entity.User;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.NotificationRepository;
import com.vesit.openattend.repository.StudentRepository;
import com.vesit.openattend.repository.SubjectRepository;
import com.vesit.openattend.repository.UserRepository;
import com.vesit.openattend.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private UserRepository userRepository;

    private Student student;
    private Subject subject;

    @BeforeEach
    void setup() {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email("notif_student@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        student = studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .rollNo("2024CS99")
                .name("Notif Test Student")
                .build());

        subject = subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("NOT403")
                .name("Database Systems")
                .build());
    }

    @Test
    void testThresholdBreachNotificationAndDeduplication() {
        String syncLogId = "sync_log_test_1";

        // First notification generation
        Notification n1 = notificationService.createThresholdBreachNotification(student, subject, 72.5, 4, syncLogId);
        assertNotNull(n1);
        assertFalse(n1.getIsRead());
        assertTrue(n1.getMessage().contains("72.5%"));

        // Second notification generation with identical syncLogId -> must be skipped (dedup constraint)
        Notification n2 = notificationService.createThresholdBreachNotification(student, subject, 72.5, 4, syncLogId);
        assertNull(n2);

        NotificationListResponse list = notificationService.getNotifications("2024CS99", false);
        assertEquals(1, list.getNotifications().size());
    }

    @Test
    void testMarkAsRead() {
        Notification n = notificationService.createThresholdBreachNotification(student, subject, 70.0, 5, "log_2");
        assertNotNull(n);

        notificationService.markAsRead(n.getId());

        Notification updated = notificationRepository.findById(n.getId()).orElseThrow();
        assertTrue(updated.getIsRead());
    }
}
