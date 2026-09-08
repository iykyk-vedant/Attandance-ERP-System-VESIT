package com.vesit.openattend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.dto.report.DefaulterReportResponse;
import com.vesit.openattend.dto.report.DefaulterRowDto;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.*;
import com.vesit.openattend.service.admin.DefaulterExportService;
import com.vesit.openattend.service.admin.DefaulterReportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DefaulterReportIntegrationTest {

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 8, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DefaulterReportService defaulterReportService;

    @Autowired
    private DefaulterExportService defaulterExportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    private Subject maths;
    private Subject physics;
    private Subject elective;

    @BeforeEach
    void setup() {
        maths = saveSubject("RPT101", "Applied Mathematics");
        physics = saveSubject("RPT102", "Engineering Physics");
        elective = saveSubject("RPT201", "Elective - Quantum Computing");
    }

    @Test
    void testBoundaryMathFlagsBelowThresholdOnly() {
        // 749/1000 = 74.90% -> defaulter; 3/4 = 75.00% -> safe; 751/1000 = 75.10% -> safe
        Student justUnder = saveStudent("RPT_BOUND_A", "Just Under", "D15B");
        Student exactlyAt = saveStudent("RPT_BOUND_B", "Exactly At", "D15B");
        Student justOver = saveStudent("RPT_BOUND_C", "Just Over", "D15B");

        saveRecords(justUnder, maths, 749, 251);
        saveRecords(exactlyAt, maths, 3, 1);
        saveRecords(justOver, maths, 751, 249);

        DefaulterReportResponse report =
                defaulterReportService.generate("D15B", WINDOW_START, WINDOW_END, new BigDecimal("75.0"));

        assertEquals(3, report.getStudentsEvaluated());
        assertEquals(1, report.getDefaulterCount(), "75.00% is safe, only 74.90% is a defaulter");
        assertEquals("RPT_BOUND_A", report.getDefaulters().get(0).getRollNo());
        assertEquals(new BigDecimal("74.90"), report.getDefaulters().get(0).getOverallPercentage());
    }

    @Test
    void testElectiveDenominatorIsPerStudent() {
        // Both students sit 10 maths lectures. Only the enrolled student is accountable for the
        // elective; the other has NA rows, which must stay out of their denominator.
        Student enrolled = saveStudent("RPT_ELEC_A", "Elective Taker", "D15B");
        Student notEnrolled = saveStudent("RPT_ELEC_B", "Elective Skipper", "D15B");

        saveRecords(enrolled, maths, 8, 2);
        saveRecords(enrolled, elective, 2, 8);
        saveRecords(notEnrolled, maths, 8, 2);
        saveNaRecords(notEnrolled, elective, 10);

        DefaulterReportResponse report =
                defaulterReportService.generate("D15B", WINDOW_START, WINDOW_END, new BigDecimal("75.0"));

        assertEquals(2, report.getStudentsEvaluated());
        assertEquals(1, report.getDefaulterCount(), "only the enrolled student is dragged below 75%");

        DefaulterRowDto defaulter = report.getDefaulters().get(0);
        assertEquals("RPT_ELEC_A", defaulter.getRollNo());
        assertEquals(20, defaulter.getTotalConducted());
        assertEquals(10, defaulter.getTotalAttended());
        assertEquals(new BigDecimal("50.00"), defaulter.getOverallPercentage());

        // The non-enrolled student keeps a 10-lecture denominator, not 20.
        DefaulterReportResponse everyone =
                defaulterReportService.generate("D15B", WINDOW_START, WINDOW_END, new BigDecimal("100.0"));
        DefaulterRowDto skipper = everyone.getDefaulters().stream()
                .filter(r -> "RPT_ELEC_B".equals(r.getRollNo()))
                .findFirst()
                .orElseThrow();
        assertEquals(10, skipper.getTotalConducted());
        assertEquals(new BigDecimal("80.00"), skipper.getOverallPercentage());
        assertNull(skipper.getSubjects().get(elective.getCode()),
                "an NA-only subject must not appear as a column value for that student");
    }

    @Test
    void testDateWindowAndDivisionFilters() {
        Student inDivision = saveStudent("RPT_WIN_A", "In Division", "D15B");
        Student otherDivision = saveStudent("RPT_WIN_B", "Other Division", "D14A");

        // Inside the window: 1/4 = 25%. Outside it: perfect attendance that must be ignored.
        saveRecords(inDivision, maths, 1, 3);
        saveRecordsOn(inDivision, maths, LocalDate.of(2026, 7, 15), 20, 0);
        saveRecords(otherDivision, maths, 0, 4);

        DefaulterReportResponse report =
                defaulterReportService.generate("d15b", WINDOW_START, WINDOW_END, null);

        assertEquals(1, report.getStudentsEvaluated(), "other divisions must be excluded");
        assertEquals(1, report.getDefaulterCount());
        assertEquals(4, report.getDefaulters().get(0).getTotalConducted(), "July lectures are outside the window");
        assertEquals(new BigDecimal("25.00"), report.getDefaulters().get(0).getOverallPercentage());
        assertEquals(new BigDecimal("75.00"), report.getThreshold(), "threshold defaults to 75");
    }

    @Test
    @WithMockUser(username = "ct@ves.ac.in", roles = "CLASS_TEACHER")
    void testJsonEndpointReturnsSubjectBreakdown() throws Exception {
        Student student = saveStudent("RPT_JSON_A", "Json Student", "D15B");
        saveRecords(student, maths, 5, 5);      // 50%
        saveRecords(student, physics, 9, 1);    // 90%

        String body = mockMvc.perform(get("/api/v1/reports/defaulters")
                .param("division", "D15B")
                .param("startDate", WINDOW_START.toString())
                .param("endDate", WINDOW_END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.defaulterCount").value(1))
                .andExpect(jsonPath("$.defaulters[0].rollNo").value("RPT_JSON_A"))
                .andExpect(jsonPath("$.defaulters[0].totalConducted").value(20))
                .andExpect(jsonPath("$.defaulters[0].totalAttended").value(14))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals(70.00, json.at("/defaulters/0/overallPercentage").asDouble(), 0.001);
        assertEquals(50.00, json.at("/defaulters/0/subjects/RPT101/percentage").asDouble(), 0.001);
        assertEquals(90.00, json.at("/defaulters/0/subjects/RPT102/percentage").asDouble(), 0.001);
        assertEquals(2, json.get("subjects").size());
    }

    @Test
    @WithMockUser(username = "ct@ves.ac.in", roles = "CLASS_TEACHER")
    void testCsvDownloadHasHeadersAndSubjectColumns() throws Exception {
        Student student = saveStudent("RPT_CSV_A", "Comma, Student", "D15B");
        saveRecords(student, maths, 1, 9);

        byte[] body = mockMvc.perform(get("/api/v1/reports/defaulters")
                .param("division", "D15B")
                .param("startDate", WINDOW_START.toString())
                .param("endDate", WINDOW_END.toString())
                .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"defaulters_D15B_20260801_20260831.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsByteArray();

        String[] lines = new String(body, StandardCharsets.UTF_8).split("\r\n");

        assertEquals("Roll No,Name,Total Conducted,Total Attended,Overall %,RPT101 %", lines[0]);
        assertEquals("RPT_CSV_A,\"Comma, Student\",10,1,10.00,10.00", lines[1],
                "a name containing a comma must stay inside one quoted field");
    }

    @Test
    @WithMockUser(username = "ct@ves.ac.in", roles = "CLASS_TEACHER")
    void testXlsxDownloadIsAReadableWorkbook() throws Exception {
        Student defaulter = saveStudent("RPT_XLSX_A", "Xlsx Student", "D15B");
        Student safe = saveStudent("RPT_XLSX_B", "Safe Student", "D15B");
        saveRecords(defaulter, maths, 6, 4);   // 60%
        saveRecords(safe, maths, 10, 0);       // 100%

        byte[] body = mockMvc.perform(get("/api/v1/reports/defaulters")
                .param("division", "D15B")
                .param("startDate", WINDOW_START.toString())
                .param("endDate", WINDOW_END.toString())
                .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"defaulters_D15B_20260801_20260831.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Defaulters", sheet.getSheetName());

            Row header = sheet.getRow(0);
            assertEquals("Roll No", header.getCell(0).getStringCellValue());
            assertEquals("Overall %", header.getCell(4).getStringCellValue());
            assertEquals("RPT101 %", header.getCell(5).getStringCellValue());

            Row first = sheet.getRow(1);
            assertEquals("RPT_XLSX_A", first.getCell(0).getStringCellValue());
            assertEquals(10, (int) first.getCell(2).getNumericCellValue());
            assertEquals(6, (int) first.getCell(3).getNumericCellValue());
            assertEquals(60.0, first.getCell(4).getNumericCellValue(), 0.001);

            assertNull(sheet.getRow(2), "only defaulters belong in the export");
        }
    }

    @Test
    @WithMockUser(username = "ct@ves.ac.in", roles = "CLASS_TEACHER")
    void testUnsupportedFormatIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/reports/defaulters")
                .param("division", "D15B")
                .param("format", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "student@ves.ac.in", roles = "STUDENT")
    void testStudentsCannotReadTheDefaulterList() throws Exception {
        mockMvc.perform(get("/api/v1/reports/defaulters").param("division", "D15B"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testInvertedDateRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> defaulterReportService.generate("D15B", WINDOW_END, WINDOW_START, null));
    }

    @Test
    void testEmptyCohortStillRendersHeaderRow() {
        DefaulterReportResponse report =
                defaulterReportService.generate("D99Z", WINDOW_START, WINDOW_END, null);

        assertEquals(0, report.getStudentsEvaluated());
        assertEquals(0, report.getDefaulterCount());
        assertTrue(report.getSubjects().isEmpty());

        String csv = new String(defaulterExportService.toCsv(report), StandardCharsets.UTF_8);
        assertEquals("Roll No,Name,Total Conducted,Total Attended,Overall %\r\n", csv);
    }

    // --- fixtures -------------------------------------------------------------------------

    private Subject saveSubject(String code, String name) {
        return subjectRepository.save(Subject.builder()
                .id(UUID.randomUUID().toString())
                .code(code)
                .name(name)
                .totalPlanned(40)
                .build());
    }

    private Student saveStudent(String rollNo, String name, String division) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID().toString())
                .email(rollNo.toLowerCase() + "@ves.ac.in")
                .role(Role.STUDENT)
                .build());

        return studentRepository.save(Student.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .rollNo(rollNo)
                .name(name)
                .division(division)
                .batch("B1")
                .build());
    }

    private void saveRecords(Student student, Subject subject, int present, int absent) {
        saveRecordsOn(student, subject, WINDOW_START, present, absent);
    }

    /** Records are spread across session_index to respect the (student, subject, date, index) key. */
    private void saveRecordsOn(Student student, Subject subject, LocalDate date, int present, int absent) {
        int index = 0;
        for (int i = 0; i < present; i++) {
            saveRecord(student, subject, date, index++, AttendanceStatus.PRESENT);
        }
        for (int i = 0; i < absent; i++) {
            saveRecord(student, subject, date, index++, AttendanceStatus.ABSENT);
        }
    }

    private void saveNaRecords(Student student, Subject subject, int count) {
        for (int i = 0; i < count; i++) {
            saveRecord(student, subject, WINDOW_START, i, AttendanceStatus.NA);
        }
    }

    private void saveRecord(Student student, Subject subject, LocalDate date, int sessionIndex, AttendanceStatus status) {
        attendanceRecordRepository.save(AttendanceRecord.builder()
                .id(UUID.randomUUID().toString())
                .student(student)
                .subject(subject)
                .lectureDate(date)
                .sessionIndex(sessionIndex)
                .status(status)
                .faculty("reporting@ves.ac.in")
                .build());
    }
}
