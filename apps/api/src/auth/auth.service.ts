import { Injectable, ForbiddenException, UnauthorizedException } from '@nestjs/common';
import * as jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'openattend-super-secret-key-2026';

export interface JwtPayload {
  userId: string;
  email: string;
  role: 'STUDENT' | 'ADMIN' | 'SUPER_ADMIN';
  studentId?: string;
}

@Injectable()
export class AuthService {
  generateTokens(user: { id: string; email: string; role: 'STUDENT' | 'ADMIN' | 'SUPER_ADMIN'; studentId?: string }) {
    const payload: JwtPayload = {
      userId: user.id,
      email: user.email,
      role: user.role,
      studentId: user.studentId
    };

    const accessToken = jwt.sign(payload, JWT_SECRET, { expiresIn: '15m' });
    const refreshToken = jwt.sign({ userId: user.id }, JWT_SECRET, { expiresIn: '7d' });

    return { accessToken, refreshToken, user };
  }

  verifyToken(token: string): JwtPayload {
    try {
      return jwt.verify(token, JWT_SECRET) as JwtPayload;
    } catch (err) {
      throw new UnauthorizedException('INVALID_OR_EXPIRED_TOKEN');
    }
  }

  checkRole(payload: JwtPayload, allowedRoles: Array<'STUDENT' | 'ADMIN' | 'SUPER_ADMIN'>) {
    if (!allowedRoles.includes(payload.role)) {
      throw new ForbiddenException('INSUFFICIENT_PERMISSIONS');
    }
  }
}
