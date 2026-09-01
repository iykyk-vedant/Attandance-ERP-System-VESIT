package com.vesit.openattend.service.sync;

import com.vesit.openattend.entity.SyncLog;
import com.vesit.openattend.entity.WorksheetMapping;
import com.vesit.openattend.repository.SyncLogRepository;
import com.vesit.openattend.repository.WorksheetMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledSyncWorker {

    private final WorksheetMappingRepository worksheetMappingRepository;
    private final SyncLogRepository syncLogRepository;
    private final UpsertEngine upsertEngine;

    @Scheduled(cron = "${openattend.sync.cron:0 */15 * * * *}")
    public void runScheduledSync() {
        log.info("ScheduledSyncWorker: Starting periodic worksheet sync cycle.");

        List<WorksheetMapping> activeMappings = worksheetMappingRepository.findByIsActiveTrue();
        if (activeMappings.isEmpty()) {
            log.info("ScheduledSyncWorker: No active worksheet mappings found to sync.");
            return;
        }

        for (WorksheetMapping mapping : activeMappings) {
            try {
                Optional<SyncLog> lastLog = syncLogRepository.findFirstByWorksheetMappingIdOrderByStartedAtDesc(mapping.getId());
                String lastHash = lastLog.map(SyncLog::getContentHash).orElse(null);

                SyncResult result = upsertEngine.executeSyncRun(mapping, null, lastHash);
                log.info("ScheduledSyncWorker: Synced mapping {} [{}]: status={}, upserted={}",
                        mapping.getId(), mapping.getWorksheetName(), result.getStatus(), result.getRowsUpserted());
            } catch (Exception e) {
                log.error("ScheduledSyncWorker: Error syncing mapping {}: {}", mapping.getId(), e.getMessage());
            }
        }
    }
}
