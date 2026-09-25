-- Flyway V4: Attendance correction audit metadata
-- Records who made a change and why, while keeping the existing audit rows valid.

ALTER TABLE attendance_history_events
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(255);

ALTER TABLE attendance_history_events
    ADD COLUMN IF NOT EXISTS mandatory_reason VARCHAR(1000);

-- Existing rows are historical records created before correction metadata existed.
-- Populate them with explicit migration values before enforcing the invariant.
UPDATE attendance_history_events
SET modified_by = COALESCE(modified_by, 'system'),
    mandatory_reason = COALESCE(mandatory_reason, 'Historical attendance record')
WHERE modified_by IS NULL
   OR mandatory_reason IS NULL;

ALTER TABLE attendance_history_events
    ALTER COLUMN modified_by SET NOT NULL;

ALTER TABLE attendance_history_events
    ALTER COLUMN mandatory_reason SET NOT NULL;
