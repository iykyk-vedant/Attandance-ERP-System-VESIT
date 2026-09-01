package com.vesit.openattend.service.sync;

import com.vesit.openattend.entity.enums.AttendanceStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class WorksheetMapper {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    public static LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }
        String clean = rawDate.trim();

        // Support Excel / Google Sheets numeric serial dates (e.g., 46237 or 46237.0)
        try {
            double serial = Double.parseDouble(clean);
            if (serial > 30000 && serial < 80000) {
                return LocalDate.of(1899, 12, 30).plusDays((long) serial);
            }
        } catch (NumberFormatException ignored) {
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(clean, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    public static AttendanceStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return AttendanceStatus.NA;
        }
        String clean = rawStatus.trim().toUpperCase();
        if (clean.equals("PRESENT") || clean.equals("P") || clean.equals("1") || clean.equals("TRUE") || clean.equals("YES")) {
            return AttendanceStatus.PRESENT;
        }
        if (clean.equals("ABSENT") || clean.equals("A") || clean.equals("0") || clean.equals("FALSE") || clean.equals("NO")) {
            return AttendanceStatus.ABSENT;
        }
        return AttendanceStatus.NA;
    }

    public static ParsedAttendanceRow mapRowToEntity(List<Object> row, RowMappingConfig config, String sourceRowHash) {
        if (row == null || row.isEmpty() || config == null || config.getColumnRoles() == null) {
            return null;
        }

        Map<String, String> roles = config.getColumnRoles();
        int dateIdx = RowMappingConfig.columnLetterToIndex(roles.get("date"));
        int rollNoIdx = RowMappingConfig.columnLetterToIndex(roles.get("rollNo"));
        int statusIdx = RowMappingConfig.columnLetterToIndex(roles.get("status"));
        int facultyIdx = RowMappingConfig.columnLetterToIndex(roles.get("faculty"));
        int remarksIdx = RowMappingConfig.columnLetterToIndex(roles.get("remarks"));

        if (rollNoIdx < 0 || rollNoIdx >= row.size() || dateIdx < 0 || dateIdx >= row.size()) {
            return null;
        }

        String rawRollNo = row.get(rollNoIdx) != null ? row.get(rollNoIdx).toString().trim().toUpperCase() : null;
        String rawDate = row.get(dateIdx) != null ? row.get(dateIdx).toString().trim() : null;

        if (rawRollNo == null || rawRollNo.isEmpty() || rawDate == null || rawDate.isEmpty()) {
            return null;
        }

        LocalDate parsedDate = parseDate(rawDate);
        if (parsedDate == null) {
            return null;
        }

        String rawStatus = statusIdx >= 0 && statusIdx < row.size() && row.get(statusIdx) != null
                ? row.get(statusIdx).toString().trim()
                : null;
        AttendanceStatus status = parseStatus(rawStatus);

        String faculty = facultyIdx >= 0 && facultyIdx < row.size() && row.get(facultyIdx) != null
                ? row.get(facultyIdx).toString().trim()
                : null;

        String remarks = remarksIdx >= 0 && remarksIdx < row.size() && row.get(remarksIdx) != null
                ? row.get(remarksIdx).toString().trim()
                : null;

        return ParsedAttendanceRow.builder()
                .studentRollNo(rawRollNo)
                .lectureDate(parsedDate)
                .sessionIndex(0)
                .status(status)
                .faculty(faculty)
                .remarks(remarks)
                .sourceRowHash(sourceRowHash)
                .build();
    }

    /**
     * Unpivots VESIT professor matrix attendance grids (dates across columns, students in rows)
     */
    public static List<ParsedAttendanceRow> mapMatrixGridSheet(
            List<List<Object>> rows,
            int dateRowIndex,
            int startDataRowIndex,
            int rollNoColIndex,
            int startDateColIndex
    ) {
        List<ParsedAttendanceRow> results = new ArrayList<>();
        if (rows == null || rows.size() <= Math.max(dateRowIndex, startDataRowIndex)) {
            return results;
        }

        List<Object> dateRow = rows.get(dateRowIndex);
        List<LocalDate> columnDates = new ArrayList<>();
        for (int c = 0; c < dateRow.size(); c++) {
            if (c >= startDateColIndex && dateRow.get(c) != null) {
                LocalDate d = parseDate(dateRow.get(c).toString());
                columnDates.add(d);
            } else {
                columnDates.add(null);
            }
        }

        for (int r = startDataRowIndex; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.size() <= rollNoColIndex || row.get(rollNoColIndex) == null) {
                continue;
            }

            String roll = row.get(rollNoColIndex).toString().trim();
            if (roll.isEmpty()) continue;

            for (int c = startDateColIndex; c < Math.min(row.size(), columnDates.size()); c++) {
                LocalDate lectureDate = columnDates.get(c);
                if (lectureDate == null) continue;

                Object val = row.get(c);
                if (val == null) continue;
                String rawVal = val.toString().trim();
                if (rawVal.isEmpty()) continue;

                AttendanceStatus status = parseStatus(rawVal);
                if (status != AttendanceStatus.NA) {
                    results.add(ParsedAttendanceRow.builder()
                            .studentRollNo(roll)
                            .lectureDate(lectureDate)
                            .sessionIndex(0)
                            .status(status)
                            .build());
                }
            }
        }

        return results;
    }
}
