package com.vesit.openattend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetOverallAttendance() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/overall")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.overallPct").exists())
                .andExpect(jsonPath("$.predictor").exists())
                .andExpect(jsonPath("$.predictor.status").exists());
    }

    @Test
    void testGetSubjectsAttendance() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/subjects")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.subjects").isArray());
    }

    @Test
    void testGetAttendanceHistory() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void testGetAttendanceAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/analytics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.weeklyTrends").isArray());
    }
}
