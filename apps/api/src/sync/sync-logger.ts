export type SyncStatus = 'SUCCESS' | 'SKIPPED_NO_CHANGE' | 'PARTIAL_FAILURE' | 'FAILED';

export interface SyncLogEntry {
  id: string;
  worksheetMappingId?: string;
  status: SyncStatus;
  rowsRead: number;
  rowsUpserted: number;
  contentHash?: string;
  errorMessage?: string;
  startedAt: Date;
  finishedAt?: Date;
  durationMs?: number;
}

export class SyncLogger {
  private memoryLogs: SyncLogEntry[] = [];
  private prismaClient: any = null;

  constructor(prismaClient?: any) {
    this.prismaClient = prismaClient || null;
  }

  /**
   * Start a new sync log entry
   */
  startRun(worksheetMappingId?: string): SyncLogEntry {
    const entry: SyncLogEntry = {
      id: `sync_log_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`,
      worksheetMappingId,
      status: 'SUCCESS',
      rowsRead: 0,
      rowsUpserted: 0,
      startedAt: new Date()
    };

    this.memoryLogs.push(entry);
    return entry;
  }

  /**
   * Complete a sync run and update log entry
   */
  async finishRun(
    logEntry: SyncLogEntry,
    details: {
      status: SyncStatus;
      rowsRead: number;
      rowsUpserted: number;
      contentHash?: string;
      errorMessage?: string;
    }
  ): Promise<SyncLogEntry> {
    const finishedAt = new Date();
    const durationMs = finishedAt.getTime() - logEntry.startedAt.getTime();

    logEntry.status = details.status;
    logEntry.rowsRead = details.rowsRead;
    logEntry.rowsUpserted = details.rowsUpserted;
    logEntry.contentHash = details.contentHash;
    logEntry.errorMessage = details.errorMessage;
    logEntry.finishedAt = finishedAt;
    logEntry.durationMs = durationMs;

    if (this.prismaClient?.syncLog) {
      try {
        await this.prismaClient.syncLog.create({
          data: {
            id: logEntry.id,
            worksheetMappingId: logEntry.worksheetMappingId || 'default_mapping',
            status: logEntry.status,
            rowsRead: logEntry.rowsRead,
            rowsUpserted: logEntry.rowsUpserted,
            contentHash: logEntry.contentHash,
            errorMessage: logEntry.errorMessage,
            startedAt: logEntry.startedAt,
            finishedAt: logEntry.finishedAt,
            durationMs: logEntry.durationMs
          }
        });
      } catch (err) {
        console.warn('SyncLogger: Database write skipped or failed, using memory log fallback:', err);
      }
    }

    return logEntry;
  }

  /**
   * Helper to format standardized failure error messages (PRD §11.4)
   */
  static formatErrorReason(type: '429_RATE_LIMITED' | '403_FORBIDDEN' | 'WORKSHEET_NOT_FOUND' | 'MALFORMED_ROWS' | 'DB_ERROR', details?: string): string {
    switch (type) {
      case '429_RATE_LIMITED':
        return `Google Sheets API quota exceeded (429 Rate Limited). ${details || ''}`.trim();
      case '403_FORBIDDEN':
        return `Google Sheets API access revoked (403 Forbidden). ${details || ''}`.trim();
      case 'WORKSHEET_NOT_FOUND':
        return `Target worksheet not found or renamed. ${details || ''}`.trim();
      case 'MALFORMED_ROWS':
        return `Sync completed with malformed/skipped rows. ${details || ''}`.trim();
      case 'DB_ERROR':
        return `Database transaction error during natural key upsert. ${details || ''}`.trim();
      default:
        return details || 'Unknown sync error';
    }
  }

  getLogs(): SyncLogEntry[] {
    return this.memoryLogs;
  }
}
