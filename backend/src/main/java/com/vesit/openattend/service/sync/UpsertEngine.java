package com.vesit.openattend.service.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.entity.*;
import com.vesit.openattend.entity.enums.AttendanceStatus;
import com.vesit.openattend.entity.enums.SyncRunStatus;
import com.vesit.openattend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpsertEngine {

    private final SheetsClient sheetsClient;
    private final SyncLogger syncLogger;
    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceHistoryEventRepository attendanceHistoryEventRepository;
    private final WorksheetMappingRepository worksheetMappingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public SyncResult executeSyncRun(WorksheetMapping mapping, List<List<Object>> overrideValues, String lastContentHash) {
        SyncLog syncLog = syncLogger.startRun(mapping);

        try {
            List<List<Object>> rawValues = overrideValues;
            if (rawValues == null) {
                rawValues = sheetsClient.getRangeValues(mapping.getSheetId(), mapping.getRange());
            }

            if (rawValues == null || rawValues.isEmpty()) {
                syncLogger.finishRun(syncLog, SyncRunStatus.SUCCESS, 0, 0, null, null);
                return SyncResult.builder()
                        .status(SyncRunStatus.SUCCESS)
                        .rowsRead(0)
                        .rowsUpserted(0)
                        .build();
            }

            String newContentHash = SyncDiffer.computeRangeHash(rawValues);

            // Range-level hash short-circuit (PRD §6.2)
            if (lastContentHash != null && lastContentHash.equals(newContentHash)) {
                syncLogger.finishRun(syncLog, SyncRunStatus.SKIPPED_NO_CHANGE, rawValues.size(), 0, newContentHash, null);
                return SyncResult.builder()
                        .status(SyncRunStatus.SKIPPED_NO_CHANGE)
                        .rowsRead(rawValues.size())
                        .rowsUpserted(0)
                        .contentHash(newContentHash)
                        .historyEventsCreated(0)
                        .build();
            }

            // Parse Column Roles from Mapping JSON
            Map<String, String> columnRoles = Collections.emptyMap();
            try {
                if (mapping.getColumnRoles() != null && !mapping.getColumnRoles().trim().isEmpty()) {
                    columnRoles = objectMapper.readValue(mapping.getColumnRoles(), new TypeReference<Map<String, String>>() {});
                }
            } catch (Exception e) {
                log.warn("Failed to parse columnRoles JSON for mapping {}: {}", mapping.getId(), e.getMessage());
            }
            RowMappingConfig config = RowMappingConfig.builder().columnRoles(columnRoles).build();

            int updatedCount = 0;
            int historyEventsCount = 0;
            int skippedCount = 0;
            List<String> skipReasons = new ArrayList<>();

            Subject subject = mapping.getSubject();

            // Skip header row (index 0)
            for (int i = 1; i < rawValues.size(); i++) {
                List<Object> row = rawValues.get(i);
                String sourceRowHash = SyncDiffer.computeRowHash(row);
                ParsedAttendanceRow parsedRow = WorksheetMapper.mapRowToEntity(row, config, sourceRowHash);

                if (parsedRow == null) {
                    skippedCount++;
                    skipReasons.add("Row " + (i + 1) + ": Malformed date or missing roll number");
                    continue;
                }

                Optional<Student> studentOpt = studentRepository.findByRollNo(parsedRow.getStudentRollNo());
                if (studentOpt.isEmpty()) {
                    skippedCount++;
                    skipReasons.add("Row " + (i + 1) + ": Unknown student roll number [" + parsedRow.getStudentRollNo() + "]");
                    continue;
                }

                Student student = studentOpt.get();

                Optional<AttendanceRecord> existingOpt = attendanceRecordRepository
                        .findByStudentIdAndSubjectIdAndLectureDateAndSessionIndex(
                                student.getId(),
                                subject.getId(),
                                parsedRow.getLectureDate(),
                                parsedRow.getSessionIndex()
                        );

                if (existingOpt.isEmpty()) {
                    // Create new attendance record
                    AttendanceRecord newRecord = AttendanceRecord.builder()
                            .id(UUID.randomUUID().toString())
                            .student(student)
                            .subject(subject)
                            .lectureDate(parsedRow.getLectureDate())
                            .sessionIndex(parsedRow.getSessionIndex())
                            .status(parsedRow.getStatus())
                            .faculty(parsedRow.getFaculty())
                            .remarks(parsedRow.getRemarks())
                            .sourceRowHash(parsedRow.getSourceRowHash())
                            .build();
                    attendanceRecordRepository.save(newRecord);

                    // Create initial history event
                    AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                            .id(UUID.randomUUID().toString())
                            .attendanceRecord(newRecord)
                            .previousStatus(null)
                            .newStatus(parsedRow.getStatus())
                            .syncLogId(syncLog.getId())
                            .build();
                    attendanceHistoryEventRepository.save(event);

                    updatedCount++;
                    historyEventsCount++;
                } else {
                    AttendanceRecord existing = existingOpt.get();
                    // Row-level short-circuit: update only if hash or status changed
                    if (!Objects.equals(existing.getSourceRowHash(), parsedRow.getSourceRowHash()) ||
                            existing.getStatus() != parsedRow.getStatus()) {

                        AttendanceStatus prevStatus = existing.getStatus();
                        existing.setStatus(parsedRow.getStatus());
                        existing.setFaculty(parsedRow.getFaculty());
                        existing.setRemarks(parsedRow.getRemarks());
                        existing.setSourceRowHash(parsedRow.getSourceRowHash());
                        attendanceRecordRepository.save(existing);

                        AttendanceHistoryEvent event = AttendanceHistoryEvent.builder()
                                .id(UUID.randomUUID().toString())
                                .attendanceRecord(existing)
                                .previousStatus(prevStatus)
                                .newStatus(parsedRow.getStatus())
                                .syncLogId(syncLog.getId())
                                .build();
                        attendanceHistoryEventRepository.save(event);

                        updatedCount++;
                        historyEventsCount++;
                    }
                }
            }

            SyncRunStatus finalStatus = skippedCount > 0 ? SyncRunStatus.PARTIAL_FAILURE : SyncRunStatus.SUCCESS;
            String errorMessage = skippedCount > 0
                    ? SyncLogger.formatErrorReason("MALFORMED_ROWS", String.join("; ", skipReasons))
                    : null;

            syncLogger.finishRun(syncLog, finalStatus, rawValues.size(), updatedCount, newContentHash, errorMessage);

            return SyncResult.builder()
                    .status(finalStatus)
                    .rowsRead(rawValues.size())
                    .rowsUpserted(updatedCount)
                    .skippedRows(skippedCount)
                    .contentHash(newContentHash)
                    .historyEventsCreated(historyEventsCount)
                    .errorMessage(errorMessage)
                    .build();

        } catch (Exception e) {
            log.error("Sync run failed with error: {}", e.getMessage(), e);
            String errReason = SyncLogger.formatErrorReason("SYNC_ERROR", e.getMessage());
            syncLogger.finishRun(syncLog, SyncRunStatus.FAILED, 0, 0, null, errReason);
            throw new RuntimeException("Sync execution failed: " + e.getMessage(), e);
        }
    }
}
