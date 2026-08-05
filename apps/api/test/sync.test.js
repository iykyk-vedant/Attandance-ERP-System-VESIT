import assert from 'assert';
import { UpsertEngine } from '../src/sync/upsert-engine.ts';
import { SyncDiffer } from '../src/sync/differ.ts';
import { WorksheetMapper } from '../src/sync/worksheet-mapper.ts';
import { SyncLogger } from '../src/sync/sync-logger.ts';
import { SheetsClient } from '../src/sync/sheets-client.ts';

console.log('Running Sync Engine M1 Idempotency, Coercion & Fault-Tolerance Tests...');

async function runTests() {
  // Test 1: WorksheetMapper Coercion Unit Tests
  assert.strictEqual(WorksheetMapper.coerceRollNo('  2024cs01  '), '2024CS01');
  assert.strictEqual(WorksheetMapper.coerceRollNo(''), null);
  assert.strictEqual(WorksheetMapper.coerceStatus('P'), 'PRESENT');
  assert.strictEqual(WorksheetMapper.coerceStatus('1'), 'PRESENT');
  assert.strictEqual(WorksheetMapper.coerceStatus('Absent'), 'ABSENT');
  assert.strictEqual(WorksheetMapper.coerceStatus('0'), 'ABSENT');
  assert.strictEqual(WorksheetMapper.coerceStatus('unknown'), 'NA');
  assert.ok(WorksheetMapper.coerceDate('2026-08-01') instanceof Date);
  assert.strictEqual(WorksheetMapper.coerceDate('invalid-date'), null);
  console.log('  ✔ M1 WorksheetMapper Coercion & Normalization passed');

  // Test 2: SheetsClient Metadata & Range Values
  const client = new SheetsClient();
  const meta = await client.getSpreadsheetMetadata('sheet_test_123');
  assert.ok(meta.sheets.length >= 3);
  const rows = await client.getRangeValues('sheet_test_123', 'CS401!A1:E10');
  assert.ok(rows.length > 0);
  console.log('  ✔ M1 SheetsClient read-only fallback structure passed');

  // Test 3: First sync execution
  const engine = new UpsertEngine();
  const run1 = await engine.executeSyncRun('test-sheet-id', 'CS401!A1:E50', {
    dateCol: 0,
    rollNoCol: 1,
    statusCol: 2,
    facultyCol: 3,
    remarksCol: 4
  });

  assert.strictEqual(run1.status, 'SUCCESS');
  assert.strictEqual(run1.rowsUpserted, 5);
  assert.strictEqual(run1.historyEventsCreated, 5);
  assert.ok(run1.contentHash);
  console.log('  ✔ M1 First Sync Execution passed');

  // Test 4: Idempotent second run with identical content hash -> SKIPPED_NO_CHANGE
  const run2 = await engine.executeSyncRun(
    'test-sheet-id',
    'CS401!A1:E50',
    { dateCol: 0, rollNoCol: 1, statusCol: 2, facultyCol: 3, remarksCol: 4 },
    run1.contentHash
  );

  assert.strictEqual(run2.status, 'SKIPPED_NO_CHANGE');
  assert.strictEqual(run2.rowsUpserted, 0);
  assert.strictEqual(run2.historyEventsCreated, 0);
  console.log('  ✔ M1 Idempotent Range-Hash Short-Circuit passed');

  // Test 5: Row hashing stability check
  const rowHash1 = SyncDiffer.computeRowHash(['2026-08-01', '2024CS01', 'Present']);
  const rowHash2 = SyncDiffer.computeRowHash(['2026-08-01', '2024CS01', 'Present']);
  assert.strictEqual(rowHash1, rowHash2);
  console.log('  ✔ M1 SyncDiffer SHA-256 Stability passed');

  // Test 6: Malformed row handling (skipped rows leading to PARTIAL_FAILURE)
  const malformedRows = [
    ['Date', 'Roll No', 'Status'],
    ['invalid-date', '2024CS01', 'Present'],
    ['2026-08-01', '', 'Present'],
    ['2026-08-01', '2024CS02', 'Present']
  ];
  const parsed = engine.parseWorksheetRows(malformedRows, { dateCol: 0, rollNoCol: 1, statusCol: 2 });
  assert.strictEqual(parsed.validRows.length, 1);
  assert.strictEqual(parsed.skippedCount, 2);
  console.log('  ✔ M1 Malformed Row Isolation & Fault Tolerance passed');

  // Test 7: SyncLogger error message formatting
  const err429 = SyncLogger.formatErrorReason('429_RATE_LIMITED');
  assert.ok(err429.toLowerCase().includes('quota exceeded'));
  const err403 = SyncLogger.formatErrorReason('403_FORBIDDEN');
  assert.ok(err403.includes('access revoked'));
  console.log('  ✔ M1 SyncLogger Standardized Failure Modes passed');

  console.log('✅ All Milestone 1 (Sync Engine Core) tests passed cleanly!');
}

runTests().catch(err => {
  console.error('❌ Sync Engine test failed:', err);
  process.exit(1);
});
