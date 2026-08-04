import assert from 'assert';
import { UpsertEngine } from '../apps/api/src/sync/upsert-engine.ts';
import { SyncDiffer } from '../apps/api/src/sync/differ.ts';

console.log('Running Sync Engine Idempotency Tests...');

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

  console.log('✅ All Sync Engine Idempotency tests passed!');
}

runTests().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
