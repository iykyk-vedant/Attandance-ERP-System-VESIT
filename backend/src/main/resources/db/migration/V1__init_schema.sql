-- Flyway V1: Initialize OpenAttend Core PostgreSQL Schema

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    role VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS students (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    roll_no VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    division VARCHAR(32),
    batch VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS subjects (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    total_planned INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS worksheet_mappings (
    id VARCHAR(36) PRIMARY KEY,
    subject_id VARCHAR(36) NOT NULL UNIQUE REFERENCES subjects(id) ON DELETE CASCADE,
    sheet_id VARCHAR(255) NOT NULL,
    worksheet_name VARCHAR(255) NOT NULL,
    range VARCHAR(64) NOT NULL,
    column_roles TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_logs (
    id VARCHAR(36) PRIMARY KEY,
    worksheet_mapping_id VARCHAR(36) REFERENCES worksheet_mappings(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    rows_read INT NOT NULL DEFAULT 0,
    rows_upserted INT NOT NULL DEFAULT 0,
    content_hash VARCHAR(64),
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms INT
);

CREATE TABLE IF NOT EXISTS attendance_records (
    id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    subject_id VARCHAR(36) NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    lecture_date DATE NOT NULL,
    session_index INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    faculty VARCHAR(255),
    remarks TEXT,
    source_row_hash VARCHAR(64) NOT NULL,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT attendance_natural_key UNIQUE (student_id, subject_id, lecture_date, session_index)
);

CREATE TABLE IF NOT EXISTS attendance_history_events (
    id VARCHAR(36) PRIMARY KEY,
    attendance_record_id VARCHAR(36) NOT NULL REFERENCES attendance_records(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sync_log_id VARCHAR(36) REFERENCES sync_logs(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    subject_id VARCHAR(36) REFERENCES subjects(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    sync_log_id VARCHAR(36) REFERENCES sync_logs(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_dedup_key UNIQUE (student_id, subject_id, type, sync_log_id)
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance_records(student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_subject ON attendance_records(subject_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance_records(lecture_date);
CREATE INDEX IF NOT EXISTS idx_notifications_student ON notifications(student_id);
CREATE INDEX IF NOT EXISTS idx_sync_logs_mapping ON sync_logs(worksheet_mapping_id);
