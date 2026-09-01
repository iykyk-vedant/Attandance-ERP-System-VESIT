package com.vesit.openattend.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.dto.admin.*;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.entity.enums.SyncRunStatus;
import com.vesit.openattend.repository.*;
import com.vesit.openattend.service.sync.SheetsClient;
import com.vesit.openattend.service.sync.SyncResult;
import com.vesit.openattend.service.sync.UpsertEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SheetsClient sheetsClient;
    private final UpsertEngine upsertEngine;
    private final SubjectRepository subjectRepository;
    private final WorksheetMappingRepository worksheetMappingRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SyncLogRepository syncLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openattend.allowed-email-domain:ves.ac.in}")
    private String allowedEmailDomain;

    @Value("${openattend.sync.cooldown-seconds:300}")
    private int cooldownSeconds;

    // Cooldown tracker per mappingId: mappingId -> Instant of last trigger
    private final Map<String, Instant> cooldownTracker = new ConcurrentHashMap<>();

    public SheetVerifyResponse verifySheetConnection(String sheetId) {
        if (sheetId == null || sheetId.trim().isEmpty()) {
            return SheetVerifyResponse.builder()
                    .success(false)
                    .verified(false)
                    .error("Sheet ID cannot be blank.")
                    .build();
        }

        // Mock verification for local / mock mode
        return SheetVerifyResponse.builder()
                .success(true)
                .verified(true)
                .title("VESIT Attendance 2026")
                .tabs(List.of("Student List", "CS401", "CS402", "CS403", "Defaulters"))
                .build();
    }

    @Transactional
    public WorksheetMapping saveWorksheetMapping(WorksheetMappingRequest request) {
        Subject subject = subjectRepository.findByCode(request.getSubjectCode())
                .orElseGet(() -> subjectRepository.save(Subject.builder()
                        .id(UUID.randomUUID().toString())
                        .code(request.getSubjectCode())
                        .name(request.getSubjectName() != null ? request.getSubjectName() : request.getSubjectCode())
                        .build()));

        String columnRolesJson = "{}";
        if (request.getColumnRoles() != null) {
            try {
                columnRolesJson = objectMapper.writeValueAsString(request.getColumnRoles());
            } catch (Exception e) {
                log.warn("Error serializing columnRoles: {}", e.getMessage());
            }
        }

        WorksheetMapping mapping = worksheetMappingRepository.findBySubjectId(subject.getId())
                .orElseGet(() -> WorksheetMapping.builder()
                        .id(UUID.randomUUID().toString())
                        .subject(subject)
                        .build());

        mapping.setSheetId(request.getSheetId());
        mapping.setWorksheetName(request.getWorksheetName());
        mapping.setRange(request.getRange());
        mapping.setColumnRoles(columnRolesJson);
        mapping.setIsActive(true);

        return worksheetMappingRepository.save(mapping);
    }

    public RosterPreviewResponse previewRoster(List<RosterRowDto> inputRows) {
        if (inputRows == null || inputRows.isEmpty()) {
            return RosterPreviewResponse.builder()
                    .success(true)
                    .summary(Map.of("total", 0, "create", 0, "update", 0, "error", 0))
                    .rows(Collections.emptyList())
                    .build();
        }

        List<RosterRowDto> evaluatedRows = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;
        int errorCount = 0;

        for (RosterRowDto row : inputRows) {
            String rollNo = row.getRollNo() != null ? row.getRollNo().trim().toUpperCase() : "";
            String email = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
            String name = row.getName() != null ? row.getName().trim() : "";

            if (rollNo.isEmpty() || name.isEmpty()) {
                evaluatedRows.add(RosterRowDto.builder()
                        .rollNo(rollNo)
                        .name(name)
                        .email(email)
                        .division(row.getDivision())
                        .batch(row.getBatch())
                        .status("ERROR")
                        .reason("Missing roll number or name")
                        .build());
                errorCount++;
                continue;
            }

            if (!email.endsWith("@" + allowedEmailDomain)) {
                evaluatedRows.add(RosterRowDto.builder()
                        .rollNo(rollNo)
                        .name(name)
                        .email(email)
                        .division(row.getDivision())
                        .batch(row.getBatch())
                        .status("ERROR")
                        .reason("Invalid email domain (must be @" + allowedEmailDomain + ")")
                        .build());
                errorCount++;
                continue;
            }

            boolean exists = studentRepository.existsByRollNo(rollNo);
            if (exists) {
                evaluatedRows.add(RosterRowDto.builder()
                        .rollNo(rollNo)
                        .name(name)
                        .email(email)
                        .division(row.getDivision())
                        .batch(row.getBatch())
                        .status("UPDATE")
                        .build());
                updateCount++;
            } else {
                evaluatedRows.add(RosterRowDto.builder()
                        .rollNo(rollNo)
                        .name(name)
                        .email(email)
                        .division(row.getDivision())
                        .batch(row.getBatch())
                        .status("CREATE")
                        .build());
                createCount++;
            }
        }

        return RosterPreviewResponse.builder()
                .success(true)
                .summary(Map.of(
                        "total", inputRows.size(),
                        "create", createCount,
                        "update", updateCount,
                        "error", errorCount
                ))
                .rows(evaluatedRows)
                .build();
    }

    @Transactional
    public int commitRoster(List<RosterRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        int committed = 0;
        for (RosterRowDto row : rows) {
            if ("ERROR".equals(row.getStatus())) {
                continue;
            }

            String rollNo = row.getRollNo().trim().toUpperCase();
            String email = row.getEmail().trim().toLowerCase();

            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .id(UUID.randomUUID().toString())
                            .email(email)
                            .role(Role.STUDENT)
                            .isActive(true)
                            .build()));

            Student student = studentRepository.findByRollNo(rollNo)
                    .orElseGet(() -> Student.builder()
                            .id(UUID.randomUUID().toString())
                            .user(user)
                            .rollNo(rollNo)
                            .build());

            student.setName(row.getName());
            student.setDivision(row.getDivision());
            student.setBatch(row.getBatch());
            studentRepository.save(student);

            committed++;
        }
        return committed;
    }

    public SyncResult triggerManualSync(String mappingId) {
        String key = mappingId != null ? mappingId : "ALL";
        Instant now = Instant.now();
        Instant lastTrigger = cooldownTracker.get(key);

        if (lastTrigger != null) {
            long elapsedSeconds = Duration.between(lastTrigger, now).getSeconds();
            if (elapsedSeconds < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsedSeconds;
                throw new RateLimitedException("Sync cooldown active. Please wait " + remaining + " second(s) before re-triggering.", (int) remaining);
            }
        }

        cooldownTracker.put(key, now);

        if (mappingId != null) {
            WorksheetMapping mapping = worksheetMappingRepository.findById(mappingId)
                    .orElseThrow(() -> new IllegalArgumentException("WorksheetMapping not found with ID: " + mappingId));
            return upsertEngine.executeSyncRun(mapping, null, null);
        } else {
            List<WorksheetMapping> activeMappings = worksheetMappingRepository.findByIsActiveTrue();
            if (activeMappings.isEmpty()) {
                return SyncResult.builder().status(SyncRunStatus.SKIPPED_NO_CHANGE).rowsRead(0).rowsUpserted(0).build();
            }
            return upsertEngine.executeSyncRun(activeMappings.get(0), null, null);
        }
    }

    public List<SyncLogResponse> getSyncLogs(Pageable pageable) {
        List<SyncLog> logs = syncLogRepository.findAllByOrderByStartedAtDesc(pageable != null ? pageable : PageRequest.of(0, 20));
        List<SyncLogResponse> result = new ArrayList<>();

        for (SyncLog l : logs) {
            String sheetName = l.getWorksheetMapping() != null && l.getWorksheetMapping().getSubject() != null
                    ? l.getWorksheetMapping().getSubject().getCode() + " • " + l.getWorksheetMapping().getSubject().getName()
                    : "Worksheet";

            result.add(SyncLogResponse.builder()
                    .id(l.getId())
                    .timestamp(l.getStartedAt() != null ? l.getStartedAt().toString() : "")
                    .sheet(sheetName)
                    .status(l.getStatus().name())
                    .rowsRead(l.getRowsRead())
                    .rowsUpserted(l.getRowsUpserted())
                    .durationMs(l.getDurationMs())
                    .detail(l.getErrorMessage())
                    .build());
        }

        return result;
    }

    public static class RateLimitedException extends RuntimeException {
        private final int retryAfterSeconds;

        public RateLimitedException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
