import { SheetsClient } from './sheets-client';
import { SyncDiffer } from './differ';

export interface RowMappingConfig {
  dateCol: number;
  rollNoCol: number;
  statusCol: number;
  facultyCol?: number;
  remarksCol?: number;
}

export interface ParsedAttendanceRow {
  studentRollNo: string;
  lectureDate: Date;
  status: 'PRESENT' | 'ABSENT' | 'NA';
  faculty?: string;
  remarks?: string;
  sourceRowHash: string;
}

export class UpsertEngine {
  private sheetsClient: SheetsClient;

  constructor(sheetsClient?: SheetsClient) {
    this.sheetsClient = sheetsClient || new SheetsClient();
  }

  /**
   * Parse 2D Sheets values into structured attendance entities
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
      const rollNo = row[config.rollNoCol]?.trim()?.toUpperCase();
      const rawDate = row[config.dateCol]?.trim();
      const rawStatus = row[config.statusCol]?.trim()?.toLowerCase();

      if (!rollNo || !rawDate || !rawStatus) {
        skippedCount++;
        continue;
      }

      const parsedDate = new Date(rawDate);
      if (isNaN(parsedDate.getTime())) {
        skippedCount++;
        continue;
      }

      let status: 'PRESENT' | 'ABSENT' | 'NA' = 'NA';
      if (['present', 'p', '1', 'yes'].includes(rawStatus)) {
        status = 'PRESENT';
      } else if (['absent', 'a', '0', 'no'].includes(rawStatus)) {
        status = 'ABSENT';
      }

      const sourceRowHash = SyncDiffer.computeRowHash(row);

      validRows.push({
        studentRollNo: rollNo,
        lectureDate: parsedDate,
        status,
        faculty: config.facultyCol !== undefined ? row[config.facultyCol] : undefined,
        remarks: config.remarksCol !== undefined ? row[config.remarksCol] : undefined,
        sourceRowHash
      });
    }

    return { validRows, skippedCount };
  }

  /**
   * Idempotent batch sync execution
   */
  async executeSyncRun(sheetId: string, range: string, config: RowMappingConfig, lastContentHash?: string) {
    const rawValues = await this.sheetsClient.getRangeValues(sheetId, range);
    const newContentHash = SyncDiffer.computeRangeHash(rawValues);

    // Range-level hash short-circuit
    if (lastContentHash && newContentHash === lastContentHash) {
      return {
        status: 'SKIPPED_NO_CHANGE',
        rowsRead: rawValues.length,
        rowsUpserted: 0,
        contentHash: newContentHash
      };
    }

    const { validRows, skippedCount } = this.parseWorksheetRows(rawValues, config);

    // Simulated transactional natural key upsert: (studentRollNo, lectureDate)
    return {
      status: skippedCount > 0 ? 'PARTIAL_FAILURE' : 'SUCCESS',
      rowsRead: rawValues.length,
      rowsUpserted: validRows.length,
      skippedRows: skippedCount,
      contentHash: newContentHash,
      processedRecords: validRows
    };
  }
}
