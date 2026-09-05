package com.vesit.openattend;

import com.vesit.openattend.dto.admin.*;
import com.vesit.openattend.entity.WorksheetMapping;
import com.vesit.openattend.repository.StudentRepository;
import com.vesit.openattend.service.admin.AdminService;
import com.vesit.openattend.service.sync.SyncResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AdminServiceTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    void testVerifySheetConnection() {
        SheetVerifyResponse response = adminService.verifySheetConnection("sheet_123");
        assertTrue(response.isSuccess());
        assertTrue(response.isVerified());
        assertNotNull(response.getTabs());
        assertTrue(response.getTabs().contains("CS401"));
    }

    @Test
    void testSaveWorksheetMapping() {
        WorksheetMappingRequest request = WorksheetMappingRequest.builder()
                .subjectCode("CS402")
                .subjectName("Operating Systems")
                .sheetId("sheet_abc")
                .worksheetName("OS")
                .range("A1:F500")
                .columnRoles(Map.of("date", "A", "rollNo", "B", "status", "C"))
                .build();

        WorksheetMapping mapping = adminService.saveWorksheetMapping(request);
        assertNotNull(mapping.getId());
        assertEquals("CS402", mapping.getSubject().getCode());
        assertEquals("A1:F500", mapping.getRange());
    }

    @Test
    void testRosterPreviewAndCommit() {
        List<RosterRowDto> input = List.of(
                RosterRowDto.builder().rollNo("2024CS10").name("Aarav Sharma").email("aarav@ves.ac.in").build(),
                RosterRowDto.builder().rollNo("2024CS11").name("Invalid User").email("invalid@gmail.com").build(), // Invalid domain
                RosterRowDto.builder().rollNo("").name("Missing Roll").email("test@ves.ac.in").build()             // Missing roll
        );

        RosterPreviewResponse preview = adminService.previewRoster(input);
        assertTrue(preview.isSuccess());
        assertEquals(3, preview.getSummary().get("total"));
        assertEquals(1, preview.getSummary().get("create"));
        assertEquals(2, preview.getSummary().get("error"));

        int committed = adminService.commitRoster(preview.getRows());
        assertEquals(1, committed);

        assertTrue(studentRepository.existsByRollNo("2024CS10"));
    }

    @Test
    void testSyncCooldownRateLimiting() {
        WorksheetMappingRequest request = WorksheetMappingRequest.builder()
                .subjectCode("CS403")
                .sheetId("sheet_test")
                .worksheetName("CS403")
                .range("A1:E50")
                .build();
        WorksheetMapping mapping = adminService.saveWorksheetMapping(request);

        // First manual trigger succeeds
        SyncResult result = adminService.triggerManualSync(mapping.getId());
        assertNotNull(result);

        // Immediate second trigger throws RateLimitedException (429 cooldown)
        assertThrows(AdminService.RateLimitedException.class, () -> {
            adminService.triggerManualSync(mapping.getId());
        });
    }
}
