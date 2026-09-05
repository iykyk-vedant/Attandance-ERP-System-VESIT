package com.vesit.openattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.dto.admin.RosterRowDto;
import com.vesit.openattend.dto.admin.SheetVerifyRequest;
import com.vesit.openattend.dto.admin.WorksheetMappingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testEndToEndAdminColdStartFlow() throws Exception {
        // Step 1: Verify Google Sheet Connection & Detect Tabs
        SheetVerifyRequest sheetReq = new SheetVerifyRequest("1a2b3c_sheet_id");
        mockMvc.perform(post("/api/v1/admin/sheet/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sheetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tabs").isArray());

        // Step 2: Save Worksheet Column Mapping
        WorksheetMappingRequest mapReq = WorksheetMappingRequest.builder()
                .subjectCode("ADM401")
                .subjectName("Data Structures")
                .sheetId("1a2b3c_sheet_id")
                .worksheetName("CS401")
                .range("A1:F500")
                .columnRoles(Map.of("date", "A", "rollNo", "B", "status", "C"))
                .build();

        mockMvc.perform(post("/api/v1/admin/mapping")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.mappingId").exists());

        // Step 3: Roster Preview & Diffing
        List<RosterRowDto> roster = List.of(
                RosterRowDto.builder().rollNo("2024CS55").name("Admin Test Student").email("student55@ves.ac.in").build()
        );

        mockMvc.perform(post("/api/v1/admin/roster/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roster)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.summary.create").value(1));

        // Step 4: Commit Roster
        mockMvc.perform(post("/api/v1/admin/roster/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roster)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // Step 5: Trigger Manual Sync
        mockMvc.perform(post("/api/v1/admin/sync/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Step 6: View Sync Audit Logs
        mockMvc.perform(get("/api/v1/admin/sync/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.logs").isArray());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void testStudentRoleForbiddenOnAdminEndpoints() throws Exception {
        SheetVerifyRequest sheetReq = new SheetVerifyRequest("1a2b3c_sheet_id");
        mockMvc.perform(post("/api/v1/admin/sheet/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sheetReq)))
                .andExpect(status().isForbidden());
    }
}
