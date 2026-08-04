import { calculatePredictor } from '../../../../packages/predictor-core/index.ts';

export interface SubjectAttendance {
  code: string;
  name: string;
  present: number;
  total: number;
  pct: number;
  predictor: ReturnType<typeof calculatePredictor>;
}

export class AttendanceService {
  getStudentOverall(rollNo: string) {
    const subjects = this.getStudentSubjects(rollNo);
    const present = subjects.reduce((sum, s) => sum + s.present, 0);
    const total = subjects.reduce((sum, s) => sum + s.total, 0);
    const pct = total > 0 ? Number(((present / total) * 100).toFixed(1)) : 0;
    const predictor = calculatePredictor({ present, total, threshold: 0.75 });

    return {
      rollNo,
      overallPct: pct,
      present,
      total,
      absent: total - present,
      isDefaulter: pct < 75,
      predictor
    };
  }

  getStudentSubjects(rollNo: string): SubjectAttendance[] {
    return [
      {
        code: 'CS401',
        name: 'Data Structures & Algorithms',
        present: 36,
        total: 42,
        pct: 85.7,
        predictor: calculatePredictor({ present: 36, total: 42, threshold: 0.75 })
      },
      {
        code: 'CS402',
        name: 'Operating Systems',
        present: 36,
        total: 44,
        pct: 81.8,
        predictor: calculatePredictor({ present: 36, total: 44, threshold: 0.75 })
      },
      {
        code: 'CS403',
        name: 'Database Management Systems',
        present: 29,
        total: 40,
        pct: 72.5,
        predictor: calculatePredictor({ present: 29, total: 40, threshold: 0.75 })
      }
    ];
  }
}
