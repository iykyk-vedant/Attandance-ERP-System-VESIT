-- V5: Supporting indexes for the 1-click defaulter report.
-- The report groups attendance_records by student and subject, filtered by the student's
-- division and a lecture_date window.

CREATE INDEX IF NOT EXISTS idx_students_division ON students(division);
CREATE INDEX IF NOT EXISTS idx_attendance_date_student ON attendance_records(lecture_date, student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_student_subject ON attendance_records(student_id, subject_id);
