// Client for OpenAttend Spring Boot 3.x REST Backend
const API_BASE = '';

export async function fetchStudentMe(token?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}/api/v1/attendance/student/me`, { headers });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function submitClassroomAttendance(payload: {
  subjectId: string;
  division: string;
  lectureDate: string;
  slotIndex: number;
  absentRollNumbers: string[];
  topicCovered?: string;
}, token?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}/api/v1/faculty/attendance/submit`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function fetchDefaulters(division = 'D12B', token?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}/api/v1/admin/defaulters?division=${division}`, { headers });
  return res.json();
}
