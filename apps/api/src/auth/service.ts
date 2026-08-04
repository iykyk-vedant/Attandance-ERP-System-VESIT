import crypto from 'crypto';

export interface UserSession {
  id: string;
  email: string;
  role: 'STUDENT' | 'ADMIN' | 'SUPER_ADMIN';
  rollNo?: string;
  accessToken: string;
  refreshToken: string;
}

export class AuthService {
  private users = new Map<string, { id: string; email: string; passHash: string; role: 'STUDENT' | 'ADMIN' | 'SUPER_ADMIN'; rollNo?: string }>([
    [
      'student@ves.ac.in',
      {
        id: 'usr_student_1',
        email: 'student@ves.ac.in',
        passHash: crypto.createHash('sha256').update('password123').digest('hex'),
        role: 'STUDENT',
        rollNo: '2024CS01'
      }
    ],
    [
      'admin@ves.ac.in',
      {
        id: 'usr_admin_1',
        email: 'admin@ves.ac.in',
        passHash: crypto.createHash('sha256').update('admin123').digest('hex'),
        role: 'ADMIN'
      }
    ]
  ]);

  async login(email: string, pass: string): Promise<UserSession> {
    const user = this.users.get(email.toLowerCase());
    if (!user) throw new Error('INVALID_CREDENTIALS');

    const inputHash = crypto.createHash('sha256').update(pass).digest('hex');
    if (inputHash !== user.passHash) throw new Error('INVALID_CREDENTIALS');

    const accessToken = `at_${Date.now()}_${crypto.randomBytes(8).toString('hex')}`;
    const refreshToken = `rt_${Date.now()}_${crypto.randomBytes(16).toString('hex')}`;

    return {
      id: user.id,
      email: user.email,
      role: user.role,
      rollNo: user.rollNo,
      accessToken,
      refreshToken
    };
  }
}
