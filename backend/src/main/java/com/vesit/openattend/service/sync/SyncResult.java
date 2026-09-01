package com.vesit.openattend.service.sync;

import com.vesit.openattend.entity.enums.SyncRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResult {
    private SyncRunStatus status;
    private int rowsRead;
    private int rowsUpserted;
    private int skippedRows;
    private String contentHash;
    private int historyEventsCreated;
    private String errorMessage;
}
