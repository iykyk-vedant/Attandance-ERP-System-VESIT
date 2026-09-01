package com.vesit.openattend;

import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.entity.enums.SyncRunStatus;
import com.vesit.openattend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class RepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private WorksheetMappingRepository worksheetMappingRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AttendanceHistoryEventRepository attendanceHistoryEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Test
    void testEntityLifecycleAndRelationships() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .email("student@ves.ac.in")
                .passwordHash("hashed_password")
                .role(Role.STUDENT)
                .build();
        userRepository.save(user);

        Student student = Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .rollNo("2024CS01")
                .name("Vedant Gharat")
                .division("D12B")
                .batch("B1")
                .build();
        studentRepository.save(student);

        Subject subject = Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("CS401")
                .name("Data Structures & Algorithms")
                .totalPlanned(45)
                .build();
        subjectRepository.save(subject);

        WorksheetMapping mapping = WorksheetMapping.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .sheetId("sheet_123")
                .worksheetName("CS401")
                .range("A1:F100")
                .columnRoles("{\"date\":\"A\",\"rollNo\":\"B\",\"status\":\"C\"}")
                .build();
        worksheetMappingRepository.save(mapping);

        SyncLog syncLog = SyncLog.builder()
                .id(UUID.randomUUID().toString())
                .worksheetMapping(mapping)
                .status(SyncRunStatus.SUCCESS)
                .rowsRead(42)
                .rowsUpserted(1)
                .contentHash("hash_abc123")
                .build();
        syncLogRepository.save(syncLog);

        AttendanceRecord record = AttendanceRecord.builder()
                .id(UUID.randomUUID().toString())
                .student(student)
                .subject(subject)
                .lectureDate(LocalDate.of(2026, 8, 5))
                .sessionIndex(0)
                .status(AttendanceStatus.PRESENT)
                .faculty("Dr. Rao")
                .remarks("Binary Trees")
                .sourceRowHash("row_hash_123")
                .build();
        attendanceRecordRepository.save(record);

        AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                .id(UUID.randomUUID().toString())
                .attendanceRecord(record)
                .previousStatus(null)
                .newStatus(AttendanceStatus.PRESENT)
                .syncLogId(syncLog.getId())
                .build();
        attendanceHistoryEventRepository.save(event);

        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .student(student)
                .subject(subject)
                .type("SYNC_UPDATE")
                .message("Attendance updated successfully.")
                .syncLogId(syncLog.getId())
                .build();
        notificationRepository.save(notification);

        // Assertions
        Optional<User> fetchedUser = userRepository.findByEmail("student@ves.ac.in");
        assertTrue(fetchedUser.isPresent());
        assertEquals(Role.STUDENT, fetchedUser.get().getRole());

        Optional<Student> fetchedStudent = studentRepository.findByRollNo("2024CS01");
        assertTrue(fetchedStudent.isPresent());
        assertEquals("Vedant Gharat", fetchedStudent.get().getName());

        Optional<AttendanceRecord> fetchedRecord = attendanceRecordRepository
                .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(
                        student.getId(),
                        subject.getId(),
                        LocalDate.of(2026, 8, 5),
                        0
                );
        assertTrue(fetchedRecord.isPresent());
        assertEquals(AttendanceStatus.PRESENT, fetchedRecord.get().getStatus());
    }
}
