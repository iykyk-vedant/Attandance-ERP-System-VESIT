package com.vesit.openattend.service.admin;

import com.vesit.openattend.dto.report.DefaulterReportResponse;
import com.vesit.openattend.dto.report.DefaulterRowDto;
import com.vesit.openattend.dto.report.SubjectBreakdownDto;
import com.vesit.openattend.dto.report.SubjectColumnDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Renders a {@link DefaulterReportResponse} as a downloadable CSV or XLSX sheet. */
@Slf4j
@Service
public class DefaulterExportService {

    public static final String CSV_CONTENT_TYPE = "text/csv";
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Excel interprets these leading characters as the start of a formula. */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    public byte[] toCsv(DefaulterReportResponse report) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, headerCells(report));

        for (DefaulterRowDto row : report.getDefaulters()) {
            List<String> cells = new ArrayList<>();
            cells.add(row.getRollNo());
            cells.add(row.getName());
            cells.add(String.valueOf(row.getTotalConducted()));
            cells.add(String.valueOf(row.getTotalAttended()));
            cells.add(plain(row.getOverallPercentage()));

            for (SubjectColumnDto column : report.getSubjects()) {
                SubjectBreakdownDto breakdown = row.getSubjects().get(column.getCode());
                cells.add(breakdown != null ? plain(breakdown.getPercentage()) : "");
            }
            appendCsvRow(csv, cells);
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(DefaulterReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Defaulters");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

            List<String> headers = headerCells(report);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (DefaulterRowDto row : report.getDefaulters()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(nullSafe(row.getRollNo()));
                sheetRow.createCell(1).setCellValue(nullSafe(row.getName()));
                sheetRow.createCell(2).setCellValue(row.getTotalConducted());
                sheetRow.createCell(3).setCellValue(row.getTotalAttended());

                Cell overall = sheetRow.createCell(4);
                overall.setCellValue(toDouble(row.getOverallPercentage()));
                overall.setCellStyle(percentStyle);

                int columnIndex = 5;
                for (SubjectColumnDto column : report.getSubjects()) {
                    SubjectBreakdownDto breakdown = row.getSubjects().get(column.getCode());
                    Cell cell = sheetRow.createCell(columnIndex++);
                    // Leave the cell blank when the student is not enrolled in that elective.
                    if (breakdown != null) {
                        cell.setCellValue(toDouble(breakdown.getPercentage()));
                        cell.setCellStyle(percentStyle);
                    }
                }
            }

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to render defaulter XLSX for division {}", report.getDivision(), e);
            throw new UncheckedIOException("Unable to generate the defaulter spreadsheet", e);
        }
    }

    /** e.g. {@code defaulters_D15B_20260801_20260831.xlsx} */
    public String fileName(DefaulterReportResponse report, String extension) {
        return "defaulters_"
                + (report.getDivision() != null ? report.getDivision() : "ALL")
                + "_" + datePart(report.getStartDate(), "start")
                + "_" + datePart(report.getEndDate(), "end")
                + "." + extension;
    }

    private String datePart(LocalDate date, String openEndedLabel) {
        return date != null ? date.format(FILE_DATE) : openEndedLabel;
    }

    private List<String> headerCells(DefaulterReportResponse report) {
        List<String> headers = new ArrayList<>(
                List.of("Roll No", "Name", "Total Conducted", "Total Attended", "Overall %"));
        for (SubjectColumnDto column : report.getSubjects()) {
            headers.add(column.getCode() + " %");
        }
        return headers;
    }

    private void appendCsvRow(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(cells.get(i)));
        }
        csv.append("\r\n");
    }

    /**
     * RFC 4180 quoting, plus a leading apostrophe on anything Excel would evaluate as a formula —
     * these sheets are opened in Excel by the office staff who receive them.
     */
    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private String plain(BigDecimal value) {
        return value != null ? value.toPlainString() : "";
    }

    private double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0d;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
