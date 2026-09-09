-- V6: Proxy / Substitute lecture delegation.
-- When the allocated subject teacher is on leave, a colleague takes the lecture as a
-- substitute. The delegation grants marking authorisation for one session slot only and
-- keeps both the original teacher and the substitute on the audit trail.

CREATE TABLE IF NOT EXISTS proxy_assignments (
    id VARCHAR(36) PRIMARY KEY,
    subject_id VARCHAR(36) NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    division VARCHAR(32) NOT NULL,
    batch VARCHAR(32),
    lecture_date DATE NOT NULL,
    -- NULL = every slot of that subject/cohort on that date; a value narrows it to one slot.
    session_index INT,
    original_faculty_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proxy_faculty_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_by VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    revoked_by VARCHAR(255),
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- At most one ACTIVE delegation per slot. COALESCE keeps the nullable batch and
-- session_index from defeating the uniqueness check (NULLs compare as distinct in SQL).
CREATE UNIQUE INDEX IF NOT EXISTS uq_proxy_active_slot
    ON proxy_assignments (subject_id, division, COALESCE(batch, ''), lecture_date, COALESCE(session_index, -1))
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_proxy_faculty_date ON proxy_assignments(proxy_faculty_id, lecture_date);
CREATE INDEX IF NOT EXISTS idx_proxy_original_faculty ON proxy_assignments(original_faculty_id);
CREATE INDEX IF NOT EXISTS idx_proxy_slot_lookup ON proxy_assignments(subject_id, division, lecture_date);

-- Audit trail on the lecture itself: faculty_id already stores who actually took the
-- session (taken_by), these columns record that it was taken under a delegation and by whom.
ALTER TABLE lecture_sessions ADD COLUMN IF NOT EXISTS is_proxy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lecture_sessions ADD COLUMN IF NOT EXISTS original_faculty_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE lecture_sessions ADD COLUMN IF NOT EXISTS proxy_assignment_id VARCHAR(36) REFERENCES proxy_assignments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sessions_original_faculty ON lecture_sessions(original_faculty_id);
