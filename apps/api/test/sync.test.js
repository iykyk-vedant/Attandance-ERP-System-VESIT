import assert from 'assert';
import { UpsertEngine } from '../src/sync/upsert-engine.ts';
import { SyncDiffer } from '../src/sync/differ.ts';

console.log('Running Sync Engine Idempotency & Fault-Tolerance Tests...');

async function runTests() {
  const engine = new UpsertEngine();

  // Test 1: First sync execution
  const run1 = await engine.executeSyncRun('test-sheet-id', 'CS401!A1:E50', {
    dateCol: 0,
    rollNoCol: 1,
    statusCol: 2,
    facultyCol: 3,
    remarksCol: 4
  });

  assert.strictEqual(run1.status, 'SUCCESS');
  assert.strictEqual(run1.rowsUpserted, 5);
  assert.ok(run1.contentHash);

  // Test 2: Idempotent second run with identical hash -> SKIPPED_NO_CHANGE
  const run2 = await engine.executeSyncRun(
    'test-sheet-id',
    'CS401!A1:E50',
    { dateCol: 0, rollNoCol: 1, statusCol: 2, facultyCol: 3, remarksCol: 4 },
    run1.contentHash
  );

  assert.strictEqual(run2.status, 'SKIPPED_NO_CHANGE');
  assert.strictEqual(run2.rowsUpserted, 0);

  // Test 3: Row hashing stability check
  const rowHash1 = SyncDiffer.computeRowHash(['2026-08-01', '2024CS01', 'Present']);
  const rowHash2 = SyncDiffer.computeRowHash(['2026-08-01', '2024CS01', 'Present']);
  assert.strictEqual(rowHash1, rowHash2);

  // Test 4: Malformed row handling (skipped rows leading to PARTIAL_FAILURE)
  const malformedRows = [
    ['Date', 'Roll No', 'Status'],
    ['invalid-date', '2024CS01', 'Present'],
    ['2026-08-01', '', 'Present'],
    ['2026-08-01', '2024CS02', 'Present']
  ];
  const parsed = engine.parseWorksheetRows(malformedRows, { dateCol: 0, rollNoCol: 1, statusCol: 2 });
  assert.strictEqual(parsed.validRows.length, 1);
  assert.strictEqual(parsed.skippedCount, 2);

  console.log('✅ All Sync Engine tests passed cleanly!');
}

runTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
