-- V7: Paperless Official Duty (OD) and medical leave approval workflow.
-- Replaces the physical letter a student carries round campus after a hackathon, sports meet,
-- fest or illness. One application carries its own proof; a teacher approves it in one click and
-- the student's ABSENT records inside the window become DUTY_PRESENT, which counts towards the
-- attendance percentage as (attended + OD) / total conducted.

CREATE TABLE IF NOT EXISTS leave_requests (
    id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    leave_type VARCHAR(32) NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    reason TEXT,
    from_date DATE NOT NULL,
    -- Inclusive: a single-day leave has to_date = from_date.
    to_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    submitted_by VARCHAR(255) NOT NULL,
    decided_by VARCHAR(255),
    decided_at TIMESTAMP,
    decision_note TEXT,
    -- Records the approval moved to DUTY_PRESENT; stays 0 until a decision is taken.
    sessions_adjusted INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_leave_window CHECK (to_date >= from_date)
);

-- The approval queue reads (status, created_at); the student portal reads by student.
CREATE INDEX IF NOT EXISTS idx_leave_status_created ON leave_requests(status, created_at);
CREATE INDEX IF NOT EXISTS idx_leave_student_created ON leave_requests(student_id, created_at DESC);
-- Marking a roster asks "which of these students is on approved leave today".
CREATE INDEX IF NOT EXISTS idx_leave_active_window ON leave_requests(status, from_date, to_date);

-- Held in its own table so listing the queue never drags the file bytes along, and in the
-- database rather than on disk because the deployment target has an ephemeral filesystem —
-- a proof that vanished on the next restart would put the paper letter straight back.
CREATE TABLE IF NOT EXISTS leave_proof_documents (
    id VARCHAR(36) PRIMARY KEY,
    leave_request_id VARCHAR(36) NOT NULL UNIQUE REFERENCES leave_requests(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    -- The sniffed type, not the one the browser declared; it is what the download endpoint serves.
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- An approval rewrites one student's records across a date range.
CREATE INDEX IF NOT EXISTS idx_attendance_student_date ON attendance_records(student_id, lecture_date);
