package com.vesit.openattend.controller;

import com.vesit.openattend.dto.report.DefaulterReportResponse;
import com.vesit.openattend.service.admin.DefaulterExportService;
import com.vesit.openattend.service.admin.DefaulterReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final DefaulterReportService defaulterReportService;
    private final DefaulterExportService defaulterExportService;

    /**
     * 1-click defaulter list. {@code format} selects the representation:
     * {@code json} for the dashboard, {@code csv} / {@code xlsx} for a file download.
     */
    @GetMapping("/defaulters")
    public ResponseEntity<?> getDefaulters(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "75.0") BigDecimal threshold,
            @RequestParam(required = false, defaultValue = "json") String format
    ) {
        DefaulterReportResponse report = defaulterReportService.generate(division, startDate, endDate, threshold);
        String requestedFormat = format != null ? format.trim().toLowerCase() : "json";

        return switch (requestedFormat) {
            case "json" -> ResponseEntity.ok(report);
            case "csv" -> download(
                    defaulterExportService.toCsv(report),
                    defaulterExportService.fileName(report, "csv"),
                    DefaulterExportService.CSV_CONTENT_TYPE);
            case "xlsx" -> download(
                    defaulterExportService.toXlsx(report),
                    defaulterExportService.fileName(report, "xlsx"),
                    DefaulterExportService.XLSX_CONTENT_TYPE);
            default -> throw new IllegalArgumentException(
                    "Unsupported format '" + format + "'. Supported formats: json, csv, xlsx.");
        };
    }

    private ResponseEntity<byte[]> download(byte[] body, String fileName, String contentType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentLength(body.length)
                .body(body);
    }
}
