# OpenAttend — System Architecture & Database Schema

OpenAttend is a high-speed, full-fledged **Native Attendance ERP System** tailored for engineering colleges (specifically VESIT) to replace cumbersome Google Forms and manual spreadsheets.

---

## 🏗️ System Architecture Diagram

```mermaid
graph TD
    subgraph Onboarding Phase (Start of Semester)
        ClassTeacher[Class Teacher] -->|Uploads Master Excel Roster| ExcelParser[Excel Parser / Apache POI]
        ExcelParser -->|Extracts Students, Batches & Electives| PostgreSQL[(PostgreSQL Database)]
        Admin[HOD / Admin] -->|Allocates Subjects & Batches| AllocationService[Faculty Allocation Engine]
        AllocationService -->|Persists Teacher-Subject Mapping| PostgreSQL
    end

    subgraph Daily Attendance Marking Workflow
        Faculty[Subject Faculty / Lab Teacher] -->|Logs into Portal @ves.ac.in| FastMarkingUI[High-Speed Marking UI]
        FastMarkingUI -->|Taps Absentees in <10s| FastMarkingAPI[Fast Attendance Submission API]
        FastMarkingAPI -->|Natural-Key Hash & Atomic Batch| LectureSession[LectureSession & AttendanceRecords]
        LectureSession --> PostgreSQL
        LectureSession -->|Topic Covered Log| AcademicDiary[NAAC Academic Diary Tracker]
    end

    subgraph 48-Hour Audit & Correction Loop
        Faculty -->|Corrects Marking within 48h| CorrectionAPI[Correction Endpoint with Mandatory Reason]
        CorrectionAPI -->|Append Immutable Audit Event| HistoryEvents[attendance_history_events]
        HistoryEvents --> PostgreSQL
    end

    subgraph Student Experience & Alerts
        PostgreSQL -->|Reads Direct in <50ms| StudentAPI[REST Student API]
        StudentAPI -->|Live Dashboards| StudentPWA[Student PWA Dashboard - Rings & Safe Skips]
        LectureSession -->|Aggregates Drop Below 75%| DefaulterAlerts[7-Day Throttled Defaulter Alerts]
        DefaulterAlerts -->|Email / In-App| StudentPWA
    end

    subgraph Administrative & Accreditation Reporting
        PostgreSQL -->|Aggregated SQL Engine| DefaulterEngine[Defaulter Reporting Engine]
        DefaulterEngine -->|1-Click Notice Board Export| A4PDF[Printable A4 Defaulter PDF & Excel]
        PostgreSQL -->|Syllabus Metrics| DiaryReport[NAAC / NBA Course File Export]
    end
```

---

## 🗄️ Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    USER ||--o{ REFRESH_TOKEN : owns
    USER ||--o{ FACULTY_SUBJECT_ALLOCATION : assigned
    USER ||--o{ LECTURE_SESSION : conducts
    USER ||--o| STUDENT : profiles

    STUDENT ||--o{ STUDENT_SUBJECT_ENROLLMENT : enrolled
    STUDENT ||--o{ ATTENDANCE_RECORD : receives
    STUDENT ||--o{ OD_LEAVE_REQUEST : submits
    STUDENT ||--o{ NOTIFICATION : notified

    SUBJECT ||--o{ FACULTY_SUBJECT_ALLOCATION : allocated
    SUBJECT ||--o{ STUDENT_SUBJECT_ENROLLMENT : links
    SUBJECT ||--o{ LECTURE_SESSION : hosts

    LECTURE_SESSION ||--o{ ATTENDANCE_RECORD : contains
    ATTENDANCE_RECORD ||--o{ ATTENDANCE_HISTORY_EVENT : audits

    USER {
        string id PK
        string email UK
        string passwordHash
        enum role "STUDENT | FACULTY | CLASS_TEACHER | ADMIN | SUPER_ADMIN"
        boolean isActive
        datetime createdAt
        datetime updatedAt
    }

    STUDENT {
        string id PK
        string userId FK
        string rollNo UK
        string name
        string division
        string batch
        datetime createdAt
        datetime updatedAt
    }

    SUBJECT {
        string id PK
        string code UK
        string name
        int totalPlanned
        boolean isElective
        datetime createdAt
        datetime updatedAt
    }

    FACULTY_SUBJECT_ALLOCATION {
        string id PK
        string facultyId FK
        string subjectId FK
        string division
        string batch "A | B | C | ALL"
        string semester
        datetime createdAt
    }

    STUDENT_SUBJECT_ENROLLMENT {
        string id PK
        string studentId FK
        string subjectId FK
        string division
        string batch "A | B | C | ALL"
        boolean isElective
        datetime createdAt
    }

    LECTURE_SESSION {
        string id PK
        string subjectId FK
        string facultyId FK
        string division
        string batch "A | B | C | ALL"
        date lectureDate
        int sessionIndex
        int lectureHours
        string topicCovered
        enum lectureType "THEORY | LAB | TUTORIAL | REMEDIAL"
        boolean isProxy
        string proxyFacultyId FK
        string sessionHash
        datetime lockedAt
        datetime createdAt
    }

    ATTENDANCE_RECORD {
        string id PK
        string lectureSessionId FK
        string studentId FK
        enum status "PRESENT | ABSENT | DUTY_PRESENT"
        datetime syncedAt
        datetime updatedAt
    }

    ATTENDANCE_HISTORY_EVENT {
        string id PK
        string attendanceRecordId FK
        enum previousStatus "PRESENT | ABSENT | DUTY_PRESENT"
        enum newStatus "PRESENT | ABSENT | DUTY_PRESENT"
        string modifiedBy
        string reason
        datetime changedAt
    }

    OD_LEAVE_REQUEST {
        string id PK
        string studentId FK
        string eventName
        date startDate
        date endDate
        string proofUrl
        enum status "PENDING | APPROVED | REJECTED"
        string reviewedBy
        datetime reviewedAt
        datetime createdAt
    }

    NOTIFICATION {
        string id PK
        string studentId FK
        string subjectId FK
        string type
        string message
        boolean isRead
        datetime createdAt
    }
```

---

## 🛡️ Architectural Guarantees & Non-Negotiable Invariants

1. **High-Speed Marking UX (<10 Seconds)**:
   - Faculty mark **only absent students**; all others default to present.
   - Endpoint processes an entire division (up to 80 students) in `<100ms`.

2. **Strict Natural-Key Idempotency**:
   - Every attendance record is uniquely keyed by `(lecture_session_id, student_id)`.
   - Repeated submissions, multi-taps, or network retries are deduplicated via SHA-256 session hashing.

3. **48-Hour Academic Integrity Lock**:
   - Faculty can correct marking errors within 48 hours of lecture creation.
   - Every modification requires a mandatory explanation (`min 10 chars`) and generates an immutable audit record in `attendance_history_events`.
   - After 48 hours, the session is permanently locked.

4. **Institutional Security & RBAC Boundary**:
   - Accounts must match the `@ves.ac.in` domain.
   - Faculty can ONLY inspect rosters and submit attendance for classes allocated to them (`faculty_subject_allocations`). Unauthorized attempts are rejected with `403 Forbidden`.

5. **NAAC / NBA Accreditation Diary Integration**:
   - Every lecture submission records `topic_covered` and `lecture_type` directly in the database, automatically compiling real-time Course Diary syllabus progress metrics.

6. **Offline-First Classroom Marking (PWA)**:
   - A service worker (`sw.js`) precaches the app shell, so the PWA launches with **zero connectivity**. Navigations are network-first with a cached-shell fallback; `/api/**` is never served from the cache.
   - Today's assigned rosters are pulled into IndexedDB at login (`precacheTodayRosters`), so the marking grid renders fully offline.
   - Submissions made while offline are persisted to the IndexedDB store `pending_submissions` and replayed automatically on `window.online` (and via Background Sync where supported).
   - Replay leans on the natural-key idempotency above: a `409 Conflict` means the session already reached the server, so the queued row is dropped rather than retried. `401/403/5xx` keep the row queued; other `4xx` mark it `REJECTED` with the server's reason for the teacher to review.
   - Cached rosters are cleared on logout (student PII on shared devices); **unsent attendance in `pending_submissions` is deliberately preserved**.

### Client-Side Offline Store (`openattend-offline`, IndexedDB v1)

| Store | Key | Contents |
|---|---|---|
| `rosters` | `division::batch` | Precached cohort roster + `cachedAt` |
| `pending_submissions` | generated `id` | Queued submission `payload`, `status` (`PENDING` / `REJECTED`), `attempts`, `lastError`, `queuedAt` |
| `meta` | `key` | Bookkeeping, e.g. `rosters_precached_at` |
