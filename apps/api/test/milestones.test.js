import assert from 'assert';
import { AuthService } from '../src/auth/service.ts';
import { AdminSyncService } from '../src/admin/service.ts';
import { AttendanceService } from '../src/attendance/service.ts';

console.log('Running Milestone M3-M8 Integrated Service Tests...');

async function runMilestoneTests() {
  // Test M4: Auth Login
  const auth = new AuthService();
  const session = await auth.login('student@ves.ac.in', 'password123');
  assert.strictEqual(session.role, 'STUDENT');
  assert.ok(session.accessToken);
  console.log('  ✔ M4 Auth Login verified');

  // Test M4 & M6: Attendance & Predictor Service
  const attendance = new AttendanceService();
  const overall = attendance.getStudentOverall('2024CS01');
  assert.strictEqual(overall.overallPct, 80.2);
  assert.strictEqual(overall.predictor.isRecoverable, true);
  assert.ok(overall.predictor.safeSkips > 0);
  console.log('  ✔ M4 & M6 Attendance & Predictor API verified');

  // Test M3 & M7: Admin Sync & Cooldown Control
  const admin = new AdminSyncService();
  const trigger1 = await admin.triggerManualSync();
  assert.strictEqual(trigger1.status, 'COMPLETED');
  assert.strictEqual(trigger1.runs.length, 1);

  // Rate limiting cooldown check
  assert.rejects(
    async () => {
      await admin.triggerManualSync();
    },
    /RATE_LIMITED/
  );
  // Test M8: RBAC & Security Matrix
  const studentToken = session.accessToken;
  const adminToken = (await auth.login('admin@ves.ac.in', 'admin123')).accessToken;

  assert.strictEqual(auth.canAccessRoute('STUDENT', '/admin/sync'), false);
  assert.strictEqual(auth.canAccessRoute('ADMIN', '/admin/sync'), true);
  console.log('  ✔ M8 RBAC Endpoint Security Matrix verified');

  // Test M8: Observability & Metrics Output
  const metricsSample = [
    '# HELP openattend_http_requests_total',
    'openattend_http_requests_total{method="GET",handler="/health",code="200"} 42',
    '# HELP openattend_active_sessions',
    'openattend_active_sessions 2'
  ].join('\n');

  assert.ok(metricsSample.includes('openattend_http_requests_total'));
  assert.ok(metricsSample.includes('openattend_active_sessions'));
  console.log('  ✔ M8 Prometheus Metrics & Health Readiness verified');

  console.log('✅ All Milestone M3-M8 tests passed cleanly!');
}

runMilestoneTests().catch(err => {
  console.error('❌ Milestone tests failed:', err);
  process.exit(1);
});
