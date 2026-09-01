package com.vesit.openattend.controller;

import com.vesit.openattend.dto.admin.*;
import com.vesit.openattend.entity.WorksheetMapping;
import com.vesit.openattend.service.admin.AdminService;
import com.vesit.openattend.service.sync.SyncResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/sheet/verify")
    public ResponseEntity<SheetVerifyResponse> verifySheet(@Valid @RequestBody SheetVerifyRequest request) {
        SheetVerifyResponse response = adminService.verifySheetConnection(request.getSheetId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mapping")
    public ResponseEntity<Map<String, Object>> saveMapping(@Valid @RequestBody WorksheetMappingRequest request) {
        WorksheetMapping mapping = adminService.saveWorksheetMapping(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Worksheet mapping saved successfully",
                "mappingId", mapping.getId()
        ));
    }

    @PostMapping("/roster/preview")
    public ResponseEntity<RosterPreviewResponse> previewRoster(@RequestBody List<RosterRowDto> rows) {
        RosterPreviewResponse response = adminService.previewRoster(rows);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/roster/commit")
    public ResponseEntity<Map<String, Object>> commitRoster(@RequestBody List<RosterRowDto> rows) {
        int committed = adminService.commitRoster(rows);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Roster committed successfully",
                "count", committed
        ));
    }

    @PostMapping("/sync/trigger")
    public ResponseEntity<?> triggerSync(@RequestParam(required = false) String mappingId) {
        try {
            SyncResult result = adminService.triggerManualSync(mappingId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", result.getStatus().name(),
                    "log", Map.of(
                            "rowsRead", result.getRowsRead(),
                            "rowsUpserted", result.getRowsUpserted(),
                            "status", result.getStatus().name(),
                            "skippedRows", result.getSkippedRows()
                    )
            ));
        } catch (AdminService.RateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                    .body(Map.of(
                            "success", false,
                            "error", "RATE_LIMITED",
                            "message", e.getMessage(),
                            "retryAfterSeconds", e.getRetryAfterSeconds()
                    ));
        }
    }

    @GetMapping("/sync/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<SyncLogResponse> logs = adminService.getSyncLogs(PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "logs", logs
        ));
    }
}
