-- V4: Immutable audit trail for the 48-hour attendance correction window.
-- Captures WHO changed a record and WHY, alongside the existing previous/new status pair.

ALTER TABLE attendance_history_events ADD COLUMN IF NOT EXISTS modified_by VARCHAR(255);
ALTER TABLE attendance_history_events ADD COLUMN IF NOT EXISTS mandatory_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_history_events_record ON attendance_history_events(attendance_record_id);
CREATE INDEX IF NOT EXISTS idx_history_events_modified_by ON attendance_history_events(modified_by);
CREATE INDEX IF NOT EXISTS idx_history_events_changed_at ON attendance_history_events(changed_at);
