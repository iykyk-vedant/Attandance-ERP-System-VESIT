package com.vesit.openattend;

import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.entity.enums.SyncRunStatus;
import com.vesit.openattend.repository.*;
import com.vesit.openattend.service.sync.SyncResult;
import com.vesit.openattend.service.sync.UpsertEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class UpsertEngineTest {

    @Autowired
    private UpsertEngine upsertEngine;

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

    private WorksheetMapping mapping;
    private Student student1;

    @BeforeEach
    void setup() {
        User user1 = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email("student1@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        student1 = studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user1)
                .rollNo("2024CS01")
                .name("Student One")
                .build());

        User user2 = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email("student2@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user2)
                .rollNo("2024CS02")
                .name("Student Two")
                .build());

        Subject subject = subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code("UPS401")
                .name("Data Structures")
                .build());

        mapping = worksheetMappingRepository.save(WorksheetMapping.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .sheetId("sheet_test_123")
                .worksheetName("UPS401")
                .range("A1:E100")
                .columnRoles("{\"date\":\"A\",\"rollNo\":\"B\",\"status\":\"C\",\"faculty\":\"D\",\"remarks\":\"E\"}")
                .build());
    }

    @Test
    void testInitialSyncAndIdempotentSecondRun() {
        List<List<Object>> sheetValues = List.of(
                List.of("Date", "RollNo", "Status", "Faculty", "Remarks"),
                List.of("2026-08-05", "2024CS01", "Present", "Dr. Rao", "Trees"),
                List.of("2026-08-05", "2024CS02", "Absent", "Dr. Rao", "Trees")
        );

        // First Run
        SyncResult result1 = upsertEngine.executeSyncRun(mapping, sheetValues, null);
        assertEquals(SyncRunStatus.SUCCESS, result1.getStatus());
        assertEquals(2, result1.getRowsUpserted());
        assertEquals(2, result1.getHistoryEventsCreated());

        List<AttendanceRecord> records = attendanceRecordRepository.findBySubjectId(mapping.getSubject().getId());
        assertEquals(2, records.size());

        // Second Run with same contentHash -> SKIPPED_NO_CHANGE
        SyncResult result2 = upsertEngine.executeSyncRun(mapping, sheetValues, result1.getContentHash());
        assertEquals(SyncRunStatus.SKIPPED_NO_CHANGE, result2.getStatus());
        assertEquals(0, result2.getRowsUpserted());
        assertEquals(0, result2.getHistoryEventsCreated());
    }

    @Test
    void testCellMutationCreatesAuditHistoryEvent() {
        List<List<Object>> sheetValuesOriginal = List.of(
                List.of("Date", "RollNo", "Status", "Faculty", "Remarks"),
                List.of("2026-08-05", "2024CS01", "Present", "Dr. Rao", "Lecture 1")
        );

        SyncResult run1 = upsertEngine.executeSyncRun(mapping, sheetValuesOriginal, null);
        assertEquals(1, run1.getRowsUpserted());
        assertEquals(1, run1.getHistoryEventsCreated());

        // Mutate status from Present -> Absent
        List<List<Object>> sheetValuesMutated = List.of(
                List.of("Date", "RollNo", "Status", "Faculty", "Remarks"),
                List.of("2026-08-05", "2024CS01", "Absent", "Dr. Rao", "Correction")
        );

        SyncResult run2 = upsertEngine.executeSyncRun(mapping, sheetValuesMutated, null);
        assertEquals(SyncRunStatus.SUCCESS, run2.getStatus());
        assertEquals(1, run2.getRowsUpserted());
        assertEquals(1, run2.getHistoryEventsCreated());

        List<AttendanceRecord> records = attendanceRecordRepository.findByStudentId(student1.getId());
        assertEquals(1, records.size());
        assertEquals(AttendanceStatus.ABSENT, records.get(0).getStatus());

        List<AttendanceHistoryEvent> history = attendanceHistoryEventRepository.findByAttendanceRecordId(records.get(0).getId());
        assertEquals(2, history.size());
        assertEquals(AttendanceStatus.PRESENT, history.get(1).getPreviousStatus());
        assertEquals(AttendanceStatus.ABSENT, history.get(1).getNewStatus());
    }

    @Test
    void testMalformedRowsAndUnknownStudentTriggerPartialFailure() {
        List<List<Object>> sheetValuesWithErrors = List.of(
                List.of("Date", "RollNo", "Status"),
                List.of("2026-08-05", "2024CS01", "Present"),
                List.of("invalid-date", "2024CS02", "Present"),   // Malformed date
                List.of("2026-08-05", "UNKNOWN_ROLL", "Present")  // Unknown roll no
        );

        SyncResult result = upsertEngine.executeSyncRun(mapping, sheetValuesWithErrors, null);
        assertEquals(SyncRunStatus.PARTIAL_FAILURE, result.getStatus());
        assertEquals(1, result.getRowsUpserted());
        assertEquals(2, result.getSkippedRows());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("MALFORMED_ROWS"));
    }
}
