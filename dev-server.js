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

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer((req, res) => {
  // Enable CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    return res.end();
  }

  // --- API ROUTER ---
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
        { date: '2026-08-05', subject: 'CS401 • Data Structures', status: 'Present', faculty: 'Dr. Rao' },
        { date: '2026-08-04', subject: 'CS402 • Operating Systems', status: 'Present', faculty: 'Prof. Sharma' },
        { date: '2026-08-03', subject: 'CS403 • Database Systems', status: 'Absent', faculty: 'Dr. Patel' },
        { date: '2026-08-02', subject: 'CS401 • Data Structures', status: 'Present', faculty: 'Dr. Rao' }
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
