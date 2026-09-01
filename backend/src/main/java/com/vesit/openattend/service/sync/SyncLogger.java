package com.vesit.openattend.service.sync;

import com.vesit.openattend.entity.SyncLog;
import com.vesit.openattend.entity.WorksheetMapping;
import com.vesit.openattend.entity.enums.SyncRunStatus;
import com.vesit.openattend.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncLogger {

    private final SyncLogRepository syncLogRepository;

    @Transactional
    public SyncLog startRun(WorksheetMapping mapping) {
        SyncLog syncLog = SyncLog.builder()
                .id(UUID.randomUUID().toString())
                .worksheetMapping(mapping)
                .status(SyncRunStatus.SUCCESS)
                .rowsRead(0)
                .rowsUpserted(0)
                .startedAt(LocalDateTime.now())
                .build();
        return syncLogRepository.save(syncLog);
    }

    @Transactional
    public SyncLog finishRun(
            SyncLog syncLog,
            SyncRunStatus status,
            int rowsRead,
            int rowsUpserted,
            String contentHash,
            String errorMessage
    ) {
        LocalDateTime finishedAt = LocalDateTime.now();
        int durationMs = (int) Duration.between(syncLog.getStartedAt(), finishedAt).toMillis();

        syncLog.setStatus(status);
        syncLog.setRowsRead(rowsRead);
        syncLog.setRowsUpserted(rowsUpserted);
        syncLog.setContentHash(contentHash);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setFinishedAt(finishedAt);
        syncLog.setDurationMs(durationMs);

        log.info("SyncRun finished: status={}, rowsRead={}, rowsUpserted={}, duration={}ms",
                status, rowsRead, rowsUpserted, durationMs);

        return syncLogRepository.save(syncLog);
    }

    public static String formatErrorReason(String code, String details) {
        if (details == null || details.trim().isEmpty()) {
            return "[" + code + "]";
        }
        return "[" + code + "] " + details.trim();
    }
}
