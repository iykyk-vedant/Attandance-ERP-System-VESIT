import { UpsertEngine, type RowMappingConfig } from '../sync/upsert-engine.ts';

export interface WorksheetMappingConfig {
  id: string;
  subjectCode: string;
  sheetId: string;
  worksheetName: string;
  range: string;
  columnRoles: RowMappingConfig;
  isActive: boolean;
}

export class AdminSyncService {
  private upsertEngine: UpsertEngine;
  private lastSyncTimestamp: number = 0;
  private syncCooldownMs: number = 5 * 60 * 1000; // 5 minutes cooldown
  private mappings: Map<string, WorksheetMappingConfig> = new Map();
  private syncLogs: Array<{
    id: string;
    worksheetName: string;
    status: string;
    rowsRead: number;
    rowsUpserted: number;
    timestamp: Date;
    errorMessage?: string;
  }> = [];

  constructor(upsertEngine?: UpsertEngine) {
    this.upsertEngine = upsertEngine || new UpsertEngine();

    // Default seed mapping
    this.mappings.set('m1', {
      id: 'm1',
      subjectCode: 'CS401',
      sheetId: '1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms',
      worksheetName: 'CS401',
      range: 'CS401!A1:E50',
      columnRoles: { dateCol: 0, rollNoCol: 1, statusCol: 2, facultyCol: 3, remarksCol: 4 },
      isActive: true
    });
  }

  getMappings(): WorksheetMappingConfig[] {
    return Array.from(this.mappings.values());
  }

  addMapping(mapping: Omit<WorksheetMappingConfig, 'id' | 'isActive'>): WorksheetMappingConfig {
    const newMapping: WorksheetMappingConfig = {
      ...mapping,
      id: `map_${Date.now()}`,
      isActive: true
    };
    this.mappings.set(newMapping.id, newMapping);
    return newMapping;
  }

  canTriggerSync(): { allowed: boolean; cooldownRemainingSeconds: number } {
    const now = Date.now();
    const elapsed = now - this.lastSyncTimestamp;
    if (elapsed < this.syncCooldownMs) {
      return {
        allowed: false,
        cooldownRemainingSeconds: Math.ceil((this.syncCooldownMs - elapsed) / 1000)
      };
    }
    return { allowed: true, cooldownRemainingSeconds: 0 };
  }

  async triggerManualSync(mappingId?: string) {
    const cooldownCheck = this.canTriggerSync();
    if (!cooldownCheck.allowed) {
      throw new Error(`RATE_LIMITED: Cooldown active. Wait ${cooldownCheck.cooldownRemainingSeconds}s.`);
    }

    this.lastSyncTimestamp = Date.now();
    const targetMappings = mappingId
      ? [this.mappings.get(mappingId)].filter(Boolean) as WorksheetMappingConfig[]
      : Array.from(this.mappings.values());

    const results = [];
    for (const map of targetMappings) {
      const run = await this.upsertEngine.executeSyncRun(map.sheetId, map.range, map.columnRoles);
      const logEntry = {
        id: `log_${Date.now()}_${Math.random().toString(36).substr(2, 4)}`,
        worksheetName: map.worksheetName,
        status: run.status,
        rowsRead: run.rowsRead,
        rowsUpserted: run.rowsUpserted,
        timestamp: new Date()
      };
      this.syncLogs.unshift(logEntry);
      results.push(logEntry);
    }

    return {
      status: 'COMPLETED',
      runs: results
    };
  }

  getSyncLogs() {
    return this.syncLogs;
  }
}
