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

export class WorksheetMapper {
  /**
   * Coerce roll number into clean uppercase format (e.g., '2024cs01' -> '2024CS01')
   */
  static coerceRollNo(raw?: string): string | null {
    if (!raw) return null;
    const clean = String(raw).trim().toUpperCase();
    return clean.length > 0 ? clean : null;
  }

  /**
   * Coerce date string or timestamp into valid Date object
   */
  static coerceDate(raw?: string): Date | null {
    if (!raw) return null;
    const cleanStr = String(raw).trim();
    if (!cleanStr) return null;

    const parsed = new Date(cleanStr);
    if (isNaN(parsed.getTime())) {
      return null;
    }
    return parsed;
  }

  /**
   * Coerce attendance status value
   * 'P', 'PRESENT', '1', 'YES' -> PRESENT
   * 'A', 'ABSENT', '0', 'NO' -> ABSENT
   * Otherwise -> NA
   */
  static coerceStatus(raw?: string): 'PRESENT' | 'ABSENT' | 'NA' {
    if (!raw) return 'NA';
    const clean = String(raw).trim().toLowerCase();
    
    if (['present', 'p', '1', 'yes'].includes(clean)) {
      return 'PRESENT';
    }
    if (['absent', 'a', '0', 'no'].includes(clean)) {
      return 'ABSENT';
    }
    return 'NA';
  }

  /**
   * Map raw row array to structured attendance entity
   */
  static mapRowToEntity(row: string[], config: RowMappingConfig, sourceRowHash: string): ParsedAttendanceRow | null {
    if (!row || row.length === 0) return null;

    const studentRollNo = this.coerceRollNo(row[config.rollNoCol]);
    const lectureDate = this.coerceDate(row[config.dateCol]);
    const rawStatus = row[config.statusCol];

    if (!studentRollNo || !lectureDate || !rawStatus) {
      return null;
    }

    const status = this.coerceStatus(rawStatus);

    return {
      studentRollNo,
      lectureDate,
      status,
      faculty: config.facultyCol !== undefined && row[config.facultyCol] ? String(row[config.facultyCol]).trim() : undefined,
      remarks: config.remarksCol !== undefined && row[config.remarksCol] ? String(row[config.remarksCol]).trim() : undefined,
      sourceRowHash
    };
  }
}
