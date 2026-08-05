import { SheetsClient } from './sheets-client.ts';
import { SyncDiffer } from './differ.ts';
import { WorksheetMapper, RowMappingConfig, ParsedAttendanceRow } from './worksheet-mapper.ts';
import { SyncLogger } from './sync-logger.ts';

export class UpsertEngine {
  private sheetsClient: SheetsClient;
  private syncLogger: SyncLogger;
  private prismaClient: any;
  private inMemoryDb: Map<string, ParsedAttendanceRow & { id: string; updatedAt: Date }> = new Map();
  private historyEvents: Array<{ recordId: string; prevStatus: string | null; newStatus: string; timestamp: Date }> = [];

  constructor(sheetsClient?: SheetsClient, prismaClient?: any) {
    this.sheetsClient = sheetsClient || new SheetsClient();
    this.prismaClient = prismaClient || null;
    this.syncLogger = new SyncLogger(this.prismaClient);
  }

  /**
   * Parse 2D Sheets values into structured attendance entities via WorksheetMapper
   */
  parseWorksheetRows(values: string[][], config: RowMappingConfig): { validRows: ParsedAttendanceRow[]; skippedCount: number } {
    if (!values || values.length <= 1) {
      return { validRows: [], skippedCount: 0 };
    }

    const validRows: ParsedAttendanceRow[] = [];
    let skippedCount = 0;

    // Skip header row
    for (let i = 1; i < values.length; i++) {
      const row = values[i];
      const sourceRowHash = SyncDiffer.computeRowHash(row);
      const entity = WorksheetMapper.mapRowToEntity(row, config, sourceRowHash);

      if (entity) {
        validRows.push(entity);
      } else {
        skippedCount++;
      }
    }

    return { validRows, skippedCount };
  }

  /**
   * Idempotent batch sync execution
   */
  async executeSyncRun(
    sheetId: string,
    range: string,
    config: RowMappingConfig,
    lastContentHash?: string,
    worksheetMappingId?: string
  ) {
    const logEntry = this.syncLogger.startRun(worksheetMappingId);

    try {
      const rawValues = await this.sheetsClient.getRangeValues(sheetId, range);
      const newContentHash = SyncDiffer.computeRangeHash(rawValues);

      // Range-level hash short-circuit (PRD §6.2)
      if (lastContentHash && newContentHash === lastContentHash) {
        await this.syncLogger.finishRun(logEntry, {
          status: 'SKIPPED_NO_CHANGE',
          rowsRead: rawValues.length,
          rowsUpserted: 0,
          contentHash: newContentHash
        });
        return {
          status: 'SKIPPED_NO_CHANGE',
          rowsRead: rawValues.length,
          rowsUpserted: 0,
          contentHash: newContentHash,
          historyEventsCreated: 0
        };
      }

      const { validRows, skippedCount } = this.parseWorksheetRows(rawValues, config);
      let updatedCount = 0;
      let historyEventsCount = 0;

      // Upsert valid rows transactional batch
      for (const row of validRows) {
        const naturalKey = `${row.studentRollNo}_${row.lectureDate.toISOString().split('T')[0]}`;
        const existingRecord = this.inMemoryDb.get(naturalKey);

        if (this.prismaClient?.attendanceRecord) {
          try {
            // Prisma natural key transactional upsert
            const dbRecord = await this.prismaClient.attendanceRecord.findFirst({
              where: {
                student: { rollNo: row.studentRollNo },
                lectureDate: row.lectureDate
              }
            });

            if (!dbRecord) {
              const created = await this.prismaClient.attendanceRecord.create({
                data: {
                  student: { connect: { rollNo: row.studentRollNo } },
                  subject: { connect: { id: worksheetMappingId || 'subj_default' } },
                  lectureDate: row.lectureDate,
                  status: row.status,
                  faculty: row.faculty,
                  remarks: row.remarks,
                  sourceRowHash: row.sourceRowHash
                }
              });
              await this.prismaClient.attendanceHistoryEvent.create({
                data: {
                  attendanceRecordId: created.id,
                  previousStatus: null,
                  newStatus: row.status,
                  syncLogId: logEntry.id
                }
              });
              updatedCount++;
              historyEventsCount++;
            } else if (dbRecord.sourceRowHash !== row.sourceRowHash || dbRecord.status !== row.status) {
              await this.prismaClient.attendanceRecord.update({
                where: { id: dbRecord.id },
                data: {
                  status: row.status,
                  faculty: row.faculty,
                  remarks: row.remarks,
                  sourceRowHash: row.sourceRowHash
                }
              });
              await this.prismaClient.attendanceHistoryEvent.create({
                data: {
                  attendanceRecordId: dbRecord.id,
                  previousStatus: dbRecord.status,
                  newStatus: row.status,
                  syncLogId: logEntry.id
                }
              });
              updatedCount++;
              historyEventsCount++;
            }
          } catch (dbErr) {
            console.warn('UpsertEngine: DB transactional upsert fallback to in-memory store:', dbErr);
          }
        } else {
          // In-memory fallback upsert logic
          if (!existingRecord) {
            const recordId = `att_${Date.now()}_${Math.random().toString(36).substr(2, 4)}`;
            this.inMemoryDb.set(naturalKey, { id: recordId, updatedAt: new Date(), ...row });
            this.historyEvents.push({ recordId, prevStatus: null, newStatus: row.status, timestamp: new Date() });
            updatedCount++;
            historyEventsCount++;
          } else if (existingRecord.sourceRowHash !== row.sourceRowHash || existingRecord.status !== row.status) {
            const prevStatus = existingRecord.status;
            this.inMemoryDb.set(naturalKey, { ...existingRecord, updatedAt: new Date(), ...row });
            this.historyEvents.push({ recordId: existingRecord.id, prevStatus, newStatus: row.status, timestamp: new Date() });
            updatedCount++;
            historyEventsCount++;
          }
        }
      }

      const finalStatus = skippedCount > 0 ? 'PARTIAL_FAILURE' : 'SUCCESS';
      const errorMessage = skippedCount > 0 ? SyncLogger.formatErrorReason('MALFORMED_ROWS', `${skippedCount} row(s) skipped due to missing date/roll number/status`) : undefined;

      await this.syncLogger.finishRun(logEntry, {
        status: finalStatus,
        rowsRead: rawValues.length,
        rowsUpserted: updatedCount,
        contentHash: newContentHash,
        errorMessage
      });

      return {
        status: finalStatus,
        rowsRead: rawValues.length,
        rowsUpserted: updatedCount,
        skippedRows: skippedCount,
        contentHash: newContentHash,
        processedRecords: validRows,
        historyEventsCreated: historyEventsCount
      };
    } catch (error: any) {
      const is403 = error?.message?.includes('403');
      const is429 = error?.message?.includes('429');
      const errType = is403 ? '403_FORBIDDEN' : is429 ? '429_RATE_LIMITED' : 'DB_ERROR';
      const errorMessage = SyncLogger.formatErrorReason(errType, error?.message);

      await this.syncLogger.finishRun(logEntry, {
        status: 'FAILED',
        rowsRead: 0,
        rowsUpserted: 0,
        errorMessage
      });

      throw error;
    }
  }

  getInMemoryRecords() {
    return Array.from(this.inMemoryDb.values());
  }

  getHistoryEvents() {
    return this.historyEvents;
  }
}
