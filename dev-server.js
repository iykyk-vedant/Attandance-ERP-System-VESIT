import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT || 3000;

// Mock database users for OpenAttend
const users = new Map([
  [
    'student@ves.ac.in',
    {
      id: 'usr_student_1',
      email: 'student@ves.ac.in',
      passHash: crypto.createHash('sha256').update('password123').digest('hex'),
      name: 'Vedant Gharat',
      role: 'STUDENT',
      rollNo: '2024CS01',
      division: 'D12B',
      batch: 'B1'
    }
  ],
  [
    'admin@ves.ac.in',
    {
      id: 'usr_admin_1',
      email: 'admin@ves.ac.in',
      passHash: crypto.createHash('sha256').update('admin123').digest('hex'),
      name: 'Prof. Admin User',
      role: 'ADMIN',
      rollNo: 'ADM-01'
    }
  ]
]);

// In-memory active sessions store
const sessions = new Map();

function sendJson(res, statusCode, data, contentType = 'application/json') {
  res.writeHead(statusCode, {
    'Content-Type': contentType,
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'X-XSS-Protection': '1; mode=block',
    'Strict-Transport-Security': 'max-age=31536000; includeSubDomains',
    'X-RateLimit-Limit': '100',
    'X-RateLimit-Remaining': '98',
    'X-RateLimit-Reset': String(Math.floor(Date.now() / 1000) + 60),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(typeof data === 'string' ? data : JSON.stringify(data));
}

const server = http.createServer((req, res) => {
  // Enable CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PATCH, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    return res.end();
  }

  // --- API ROUTER ---
  if (req.url === '/health' || req.url === '/api/v1/health') {
    return sendJson(res, 200, {
      status: 'ok',
      service: 'OpenAttend API',
      uptimeSeconds: Math.floor(process.uptime()),
      timestamp: new Date().toISOString()
    });
  }

  if (req.url === '/health/ready' || req.url === '/api/v1/health/ready') {
    return sendJson(res, 200, {
      status: 'ready',
      database: 'connected',
      sheetsClient: 'mock_mode_active',
      lastSyncAgeMs: 45000
    });
  }

  if (req.url === '/metrics' || req.url === '/api/v1/metrics') {
    const metricsText = [
      '# HELP openattend_http_requests_total Total HTTP requests served by OpenAttend',
      '# TYPE openattend_http_requests_total counter',
      'openattend_http_requests_total{method="GET",handler="/health",code="200"} 42',
      'openattend_http_requests_total{method="GET",handler="/attendance/overall",code="200"} 124',
      '# HELP openattend_sync_duration_seconds Sync worker execution duration in seconds',
      '# TYPE openattend_sync_duration_seconds histogram',
      'openattend_sync_duration_seconds_bucket{le="0.5"} 18',
      'openattend_sync_duration_seconds_sum 8.42',
      'openattend_sync_duration_seconds_count 18',
      '# HELP openattend_active_sessions Active user sessions count',
      '# TYPE openattend_active_sessions gauge',
      `openattend_active_sessions ${sessions.size || 2}`
    ].join('\n');

    return sendJson(res, 200, metricsText, 'text/plain; version=0.0.4; charset=utf-8');
  }

  if (req.url === '/api/v1/auth/login' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const { email, password } = JSON.parse(body || '{}');
        
        if (!email || !password) {
          return sendJson(res, 400, { success: false, error: 'Email and password are required.' });
        }

        const cleanEmail = email.trim().toLowerCase();
        if (!cleanEmail.endsWith('@ves.ac.in')) {
          return sendJson(res, 400, { success: false, error: 'Access restricted: Only official @ves.ac.in email addresses are permitted.' });
        }

        const user = users.get(cleanEmail);
        if (!user) {
          return sendJson(res, 401, { success: false, error: 'Invalid credentials. Check your email and password.' });
        }

        const inputHash = crypto.createHash('sha256').update(password).digest('hex');
        if (inputHash !== user.passHash) {
          return sendJson(res, 401, { success: false, error: 'Invalid credentials. Password does not match.' });
        }

        const accessToken = `at_${Date.now()}_${crypto.randomBytes(16).toString('hex')}`;
        const sessionData = {
          id: user.id,
          email: user.email,
          name: user.name,
          role: user.role,
          rollNo: user.rollNo,
          division: user.division,
          batch: user.batch,
          accessToken
        };

        sessions.set(accessToken, sessionData);

        return sendJson(res, 200, {
          success: true,
          message: 'Authentication successful',
          session: sessionData
        });
      } catch (err) {
        return sendJson(res, 500, { success: false, error: 'Server error processing request' });
      }
    });
    return;
  }

  if (req.url === '/api/v1/auth/me' && req.method === 'GET') {
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace('Bearer ', '').trim();
    
    if (!token || !sessions.has(token)) {
      return sendJson(res, 401, { success: false, error: 'Unauthorized or expired session' });
    }

    return sendJson(res, 200, { success: true, user: sessions.get(token) });
  }

  if (req.url === '/api/v1/auth/logout' && req.method === 'POST') {
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace('Bearer ', '').trim();
    if (token) sessions.delete(token);
    return sendJson(res, 200, { success: true, message: 'Logged out successfully' });
  }

  if (req.url === '/api/v1/attendance/overall' && req.method === 'GET') {
    return sendJson(res, 200, {
      success: true,
      rollNo: '2024CS01',
      overallPct: 80.2,
      present: 101,
      total: 126,
      absent: 25,
      isDefaulter: false,
      predictor: {
        present: 101,
        total: 126,
        pct: 80.2,
        threshold: 75,
        status: 'SAFE',
        safeSkips: 8,
        mustAttend: 0,
        message: 'Safe to skip 8 more lecture(s) while remaining above 75% threshold.'
      }
    });
  }

  if (req.url === '/api/v1/attendance/subjects' && req.method === 'GET') {
    return sendJson(res, 200, {
      success: true,
      subjects: [
        {
          code: 'CS401',
          name: 'Data Structures & Algorithms',
          present: 36,
          total: 42,
          pct: 85.7,
          predictor: {
            status: 'SAFE',
            safeSkips: 6,
            mustAttend: 0,
            message: 'Safe to skip 6 lecture(s).'
          }
        },
        {
          code: 'CS402',
          name: 'Operating Systems',
          present: 36,
          total: 44,
          pct: 81.8,
          predictor: {
            status: 'SAFE',
            safeSkips: 4,
            mustAttend: 0,
            message: 'Safe to skip 4 lecture(s).'
          }
        },
        {
          code: 'CS403',
          name: 'Database Management Systems',
          present: 29,
          total: 40,
          pct: 72.5,
          predictor: {
            status: 'RISK',
            safeSkips: 0,
            mustAttend: 4,
            message: 'Must attend 4 consecutive lecture(s) to reach 75% threshold.'
          }
        }
      ]
    });
  }

  if (req.url === '/api/v1/attendance/history' && req.method === 'GET') {
    return sendJson(res, 200, {
      success: true,
      history: [
        { date: '2026-08-05', subject: 'CS401', subjectName: 'Data Structures & Algorithms', status: 'Present', faculty: 'Dr. Rao', remarks: 'Lecture 12 - Binary Trees' },
        { date: '2026-08-04', subject: 'CS402', subjectName: 'Operating Systems', status: 'Present', faculty: 'Prof. Sharma', remarks: 'Process Scheduling' },
        { date: '2026-08-03', subject: 'CS403', subjectName: 'Database Management Systems', status: 'Absent', faculty: 'Dr. Patel', remarks: 'Normalized Schemas' },
        { date: '2026-08-02', subject: 'CS401', subjectName: 'Data Structures & Algorithms', status: 'Present', faculty: 'Dr. Rao', remarks: 'Heap Data Structures' },
        { date: '2026-08-01', subject: 'CS402', subjectName: 'Operating Systems', status: 'Present', faculty: 'Prof. Sharma', remarks: 'Virtual Memory' },
        { date: '2026-07-31', subject: 'CS403', subjectName: 'Database Management Systems', status: 'Present', faculty: 'Dr. Patel', remarks: 'SQL Indexes & B-Trees' }
      ]
    });
  }

  if (req.url === '/api/v1/attendance/analytics' && req.method === 'GET') {
    return sendJson(res, 200, {
      success: true,
      weeklyTrends: [
        { period: 'Week 1', pct: 78.0 },
        { period: 'Week 2', pct: 79.5 },
        { period: 'Week 3', pct: 82.1 },
        { period: 'Week 4', pct: 80.2 }
      ],
      subjectComparison: [
        { code: 'CS403', name: 'Database Systems', pct: 72.5, delta: -2.1, status: 'RISK' },
        { code: 'CS402', name: 'Operating Systems', pct: 81.8, delta: +1.4, status: 'SAFE' },
        { code: 'CS401', name: 'Data Structures', pct: 85.7, delta: +0.5, status: 'SAFE' }
      ]
    });
  }

  if (req.url.startsWith('/api/v1/notifications') && req.method === 'GET') {
    return sendJson(res, 200, {
      success: true,
      notifications: [
        {
          id: 'notif_1',
          type: 'threshold_breach',
          title: 'Attendance Threshold Warning',
          message: 'CS403 (Database Systems) has fallen to 72.5% — 4 consecutive lectures required to recover.',
          date: '2026-08-03T10:15:00Z',
          read: false
        },
        {
          id: 'notif_2',
          type: 'sync_update',
          title: 'Automated Sync Completed',
          message: 'Latest worksheet sync finished cleanly: 42 records verified with zero errors.',
          date: '2026-08-05T09:00:00Z',
          read: true
        }
      ]
    });
  }

  if (req.url.startsWith('/api/v1/notifications') && req.method === 'PATCH') {
    return sendJson(res, 200, { success: true, message: 'Notification state updated' });
  }

  // --- Admin Endpoints (M7) ---
  if (req.url === '/api/v1/admin/sheet/verify' && req.method === 'POST') {
    return sendJson(res, 200, {
      success: true,
      verified: true,
      title: 'VESIT Attendance 2026',
      tabs: ['Student List', 'CS401', 'CS402', 'CS403', 'Defaulters']
    });
  }

  if (req.url === '/api/v1/admin/mapping' && req.method === 'POST') {
    return sendJson(res, 200, {
      success: true,
      message: 'Worksheet mapping saved successfully'
    });
  }

  if (req.url === '/api/v1/admin/roster/preview' && req.method === 'POST') {
    return sendJson(res, 200, {
      success: true,
      summary: { total: 4, create: 2, update: 1, error: 1 },
      rows: [
        { rollNo: '2024CS04', name: 'Aarav Sharma', email: 'aarav.sharma@ves.ac.in', status: 'CREATE' },
        { rollNo: '2024CS05', name: 'Ananya Iyer', email: 'ananya.iyer@ves.ac.in', status: 'CREATE' },
        { rollNo: '2024CS01', name: 'Vedant Gharat', email: 'vedant.gharat@ves.ac.in', status: 'UPDATE' },
        { rollNo: 'INVALID', name: 'Rohan Mehta', email: 'rohan@gmail.com', status: 'ERROR', reason: 'Invalid email domain (must be @ves.ac.in)' }
      ]
    });
  }

  if (req.url === '/api/v1/admin/sync/trigger' && req.method === 'POST') {
    return sendJson(res, 200, {
      success: true,
      status: 'SUCCESS',
      log: {
        id: `sync_${Date.now()}`,
        rowsRead: 42,
        rowsUpserted: 0,
        status: 'SKIPPED_NO_CHANGE',
        durationMs: 340
      }
    });
  }

  if (req.url === '/api/v1/admin/sync/logs' && req.method === 'GET') {
    return sendJson(res, 200, {
      success: true,
      logs: [
        {
          id: 'sync_log_101',
          timestamp: '2026-08-05T14:00:00Z',
          sheet: 'CS401 • Data Structures',
          status: 'SKIPPED_NO_CHANGE',
          rowsRead: 42,
          rowsUpserted: 0,
          durationMs: 320,
          detail: 'Range hash matched existing check; zero changes detected.'
        },
        {
          id: 'sync_log_100',
          timestamp: '2026-08-05T13:45:00Z',
          sheet: 'CS403 • Database Systems',
          status: 'PARTIAL_FAILURE',
          rowsRead: 40,
          rowsUpserted: 38,
          durationMs: 480,
          detail: 'Sync completed with malformed/skipped rows: 2 rows skipped (missing date or status).'
        },
        {
          id: 'sync_log_99',
          timestamp: '2026-08-05T13:30:00Z',
          sheet: 'CS402 • Operating Systems',
          status: 'SUCCESS',
          rowsRead: 44,
          rowsUpserted: 2,
          durationMs: 410,
          detail: '2 new attendance records upserted cleanly.'
        }
      ]
    });
  }

  // --- STATIC FILE SERVER ---
  let filePath = path.join(__dirname, req.url === '/' ? 'index.html' : req.url);
  let extname = path.extname(filePath);

  let contentType = 'text/html';
  switch (extname) {
    case '.js':
      contentType = 'text/javascript';
      break;
    case '.css':
      contentType = 'text/css';
      break;
    case '.json':
    case '.webmanifest':
      contentType = 'application/manifest+json';
      break;
  }

  fs.readFile(filePath, (err, content) => {
    if (err) {
      if (err.code === 'ENOENT') {
        res.writeHead(404, { 'Content-Type': 'text/html' });
        res.end('<h1>404 Not Found</h1>', 'utf-8');
      } else {
        res.writeHead(500);
        res.end(`Server Error: ${err.code}`);
      }
    } else {
      res.writeHead(200, { 'Content-Type': contentType });
      res.end(content, 'utf-8');
    }
  });
});

server.listen(PORT, () => {
  console.log(`OpenAttend Dev Server running at http://localhost:${PORT}`);
});

export default server;
