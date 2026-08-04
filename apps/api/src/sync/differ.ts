import crypto from 'node:crypto';

export class SyncDiffer {
  /**
   * Computes SHA-256 hash of a 2D range array for change detection
   */
  static computeRangeHash(values: string[][]): string {
    const serialized = JSON.stringify(values);
    return crypto.createHash('sha256').update(serialized).digest('hex');
  }

  /**
   * Computes SHA-256 hash of a single row
   */
  static computeRowHash(row: string[]): string {
    return crypto.createHash('sha256').update(row.join('|')).digest('hex');
  }
}
