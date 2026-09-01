package com.vesit.openattend;

import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.service.sync.ParsedAttendanceRow;
import com.vesit.openattend.service.sync.RowMappingConfig;
import com.vesit.openattend.service.sync.WorksheetMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WorksheetMapperTest {

    private final RowMappingConfig config = RowMappingConfig.builder()
            .columnRoles(Map.of(
                    "date", "A",
                    "rollNo", "B",
                    "status", "C",
                    "faculty", "D",
                    "remarks", "E"
            ))
            .build();

    @Test
    void testMapValidRowWithVariousDateFormats() {
        List<Object> rowIso = List.of("2026-08-05", "2024cs01", "Present", "Dr. Rao", "Trees");
        ParsedAttendanceRow parsedIso = WorksheetMapper.mapRowToEntity(rowIso, config, "hash1");

        assertNotNull(parsedIso);
        assertEquals("2024CS01", parsedIso.getStudentRollNo());
        assertEquals(LocalDate.of(2026, 8, 5), parsedIso.getLectureDate());
        assertEquals(AttendanceStatus.PRESENT, parsedIso.getStatus());
        assertEquals("Dr. Rao", parsedIso.getFaculty());
        assertEquals("Trees", parsedIso.getRemarks());

        List<Object> rowSlash = List.of("05/08/2026", "2024CS02", "A", "Dr. Rao");
        ParsedAttendanceRow parsedSlash = WorksheetMapper.mapRowToEntity(rowSlash, config, "hash2");
        assertNotNull(parsedSlash);
        assertEquals("2024CS02", parsedSlash.getStudentRollNo());
        assertEquals(LocalDate.of(2026, 8, 5), parsedSlash.getLectureDate());
        assertEquals(AttendanceStatus.ABSENT, parsedSlash.getStatus());
    }

    @Test
    void testMapMalformedRowReturnsNull() {
        List<Object> rowInvalidDate = List.of("not-a-date", "2024CS01", "Present");
        assertNull(WorksheetMapper.mapRowToEntity(rowInvalidDate, config, "hash"));

        List<Object> rowMissingRollNo = List.of("2026-08-05", "", "Present");
        assertNull(WorksheetMapper.mapRowToEntity(rowMissingRollNo, config, "hash"));
    }
}
