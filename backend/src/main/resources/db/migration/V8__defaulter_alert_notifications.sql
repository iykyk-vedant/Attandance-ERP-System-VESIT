-- V8: Automated 75% attendance defaulter warning notifications (Issue #11).
-- A lecture submission that pushes a student below 75% (warning) or 65% (critical) stores an
-- in-app notification in the same transaction and emails a copy to the student's institutional
-- address once the submission commits.

-- Delivery of the email copy, so a missing email can be traced without trawling logs.
-- NULL for notification types that are never emailed (e.g. leave_decision).
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS email_status VARCHAR(16);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS emailed_at TIMESTAMP;

-- The 7-day cooldown asks "has this student had an alert of this type since last week".
CREATE INDEX IF NOT EXISTS idx_notifications_alert_cooldown ON notifications(student_id, type, created_at);
