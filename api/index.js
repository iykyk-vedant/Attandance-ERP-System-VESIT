import crypto from 'node:crypto';

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

// Active sessions store
const sessions = new Map();

function sendJson(res, statusCode, data) {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.statusCode = statusCode;
  res.end(JSON.stringify(data));
}

export default async function handler(req, res) {
  // CORS Preflight
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    res.statusCode = 204;
    return res.end();
  }

  const url = req.url || '';

  // Auth Login Route
  if (url.includes('/api/v1/auth/login') && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    await new Promise(resolve => req.on('end', resolve));

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
      return sendJson(res, 200, { success: true, message: 'Authentication successful', session: sessionData });
    } catch (err) {
      return sendJson(res, 500, { success: false, error: 'Server error processing request' });
    }
  }

  // Auth Me Route
  if (url.includes('/api/v1/auth/me') && req.method === 'GET') {
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace('Bearer ', '').trim();
    if (!token || !sessions.has(token)) {
      return sendJson(res, 401, { success: false, error: 'Unauthorized or expired session' });
    }
    return sendJson(res, 200, { success: true, user: sessions.get(token) });
  }

  // Auth Logout Route
  if (url.includes('/api/v1/auth/logout') && req.method === 'POST') {
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace('Bearer ', '').trim();
    if (token) sessions.delete(token);
    return sendJson(res, 200, { success: true, message: 'Logged out successfully' });
  }

  return sendJson(res, 404, { success: false, error: 'API Endpoint Not Found' });
}
