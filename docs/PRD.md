# OpenAttend — Software Requirements Specification (SRS) / PRD

**Working Title:** OpenAttend
**Document Type:** Software Requirements Specification & Product Requirements Document
**Status:** Draft v0.1
**Audience:** Engineering, SRE, Open-Source Contributors

---

## Table of Contents

1. [Overview](#1-overview)
2. [Functional Requirements](#2-functional-requirements)
3. [Non-Functional Requirements](#3-non-functional-requirements)
4. [Architecture](#4-architecture)
5. [High-Level Design](#5-high-level-design)
6. [Low-Level Design](#6-low-level-design)
7. [Database Schema](#7-database-schema)
8. [API Specifications](#8-api-specifications)
9. [Folder Structure](#9-folder-structure)
10. [Tech Justification](#10-tech-justification)
11. [Sync Workflow](#11-sync-workflow)
12. [Auth Flow](#12-auth-flow)
13. [UI Wireframes (Descriptions)](#13-ui-wireframes-descriptions)
14. [Deployment](#14-deployment)
15. [Future Scope](#15-future-scope)
16. [Roadmap](#16-roadmap)
17. [Testing Strategy](#17-testing-strategy)
18. [Security Best Practices](#18-security-best-practices)

---

## 1. Overview

### 1.1 Problem Statement

Colleges commonly run attendance through **Google Forms → Google Sheets**, because faculty already know the tool and it requires zero onboarding. This produces a working but low-visibility system: students cannot see live attendance percentages, subject-wise breakdowns, or defaulter risk without manually opening a shared spreadsheet and hunting for the right tab.

Existing attendance apps solve this by *replacing* the faculty workflow — a form of their own, a new marking UI — which colleges resist adopting because faculty are already trained on Sheets and don't want another tool.

### 1.2 Solution

OpenAttend is a **read-only companion platform**. Faculty continue marking attendance exactly as before, via Google Forms into Google Sheets. OpenAttend polls the same spreadsheet through the **Google Sheets API**, incrementally syncs it into a normalized PostgreSQL database, and serves students (and admins) a real-time dashboard: overall %, subject-wise %, history, trends, a 75%-eligibility predictor, and notifications.

The system **never writes to the Sheet** and **never parses exported files** — it is a pure, idempotent, incremental read pipeline sitting in front of a modern web dashboard.

### 1.3 Goals

- G1: Zero change to faculty's existing Google Forms/Sheets workflow.
- G2: Near-real-time (poll-interval-bound) reflection of Sheet state in the dashboard.
- G3: Idempotent sync — safe to re-run, safe to crash mid-run, no duplicate rows.
- G4: Production-grade security, observability, and CI/CD suitable for a public open-source repo.
- G5: Self-hostable by any college's admin with minimal configuration (Sheet ID + service account + worksheet mapping).

### 1.4 Non-Goals

- NG1: OpenAttend will not replace Google Forms as the attendance-marking interface.
- NG2: OpenAttend will not write, edit, or reformat any cell in the source Google Sheet.
- NG3: OpenAttend will not support arbitrary/unstructured spreadsheet layouts out of the box — it requires an admin-defined worksheet mapping (see §11).
- NG4: OpenAttend will not do OCR, biometric, or QR-code attendance capture in v1.

### 1.5 Primary Personas

| Persona | Needs |
|---|---|
| **Student** | See live %, know if they're a defaulter, know exactly how many lectures they can miss/must attend, get notified on drop below threshold. |
| **Faculty** (indirect user) | Keep using Forms/Sheets exactly as today; zero new UI to learn. |
| **Admin (CR / HOD / IT)** | Configure Sheet ID, map worksheets to subjects, manage sync interval, monitor sync health, import/manage the student roster. |

### 1.6 Assumptions

- The source Google Sheet has a **relatively stable structure** per academic term (sheet names/columns don't change daily).
- The college can provision a **Google Cloud service account** with read-only access (`https://www.googleapis.com/auth/spreadsheets.readonly`) shared onto the target Sheet.
- Student roster (roll no., name, email, division) exists in a "Student List" sheet or is imported once via CSV during admin setup (import only — not an ongoing sync dependency).

---

## 2. Functional Requirements

### 2.1 Authentication & Authorization
- FR-1.1: Users log in with **college email** (domain-restricted, e.g., `@ves.ac.in`) via email+password or OAuth (Google Sign-In recommended, since Sheets access already implies Google ecosystem trust).
- FR-1.2: JWT-based session (access token + refresh token).
- FR-1.3: RBAC with roles: `STUDENT`, `ADMIN`, `SUPER_ADMIN` (multi-department support, future-proofed).
- FR-1.4: Password reset via emailed signed token (1-hour expiry).

### 2.2 Student Dashboard
- FR-2.1: Show **Overall %** computed from `Final Overall %` sheet or derived from aggregated per-subject attendance if the sheet provides raw counts only.
- FR-2.2: Show **subject-wise %** as a grid/list, one card per subject sheet mapped in admin config.
- FR-2.3: Show **present/absent counts** and **total lectures held** per subject and overall.
- FR-2.4: Show **last sync time** (timestamp of the most recent successful sync run touching this student's data) with a relative label ("Synced 4 min ago").
- FR-2.5: Dashboard must load from cached Postgres data — **never** call the Sheets API synchronously on a user request.

### 2.3 Subject Page
- FR-3.1: Per-subject page showing %, present/absent/total.
- FR-3.2: **History** table: date, status (Present/Absent/NA), faculty (from the Form respondent, if captured), remarks (if a remarks column exists in the sheet).
- FR-3.3: **Timeline** view (chronological list/strip of lecture-day statuses).
- FR-3.4: **Calendar** view (month grid, color-coded present/absent/no-lecture).

### 2.4 Attendance History
- FR-4.1: Cross-subject history log, filterable by date range, subject, and status.
- FR-4.2: Exportable as CSV (client-side generation from already-fetched data — no server file storage requirement in v1).

### 2.5 Analytics
- FR-5.1: Progress graphs (line chart of cumulative % over time) — overall and per-subject.
- FR-5.2: Monthly/weekly aggregation toggle.
- FR-5.3: Subject comparison (bar chart, all subjects side by side).
- FR-5.4: Trend indicator (▲/▼ vs. previous period).

### 2.6 Attendance Predictor
- FR-6.1: Given current present/total and a configurable threshold (default 75%), compute:
  - **Lectures that can still be missed** while staying ≥ threshold, assuming a fixed number of remaining lectures (admin-configured `total_planned_lectures` per subject, optional) OR assuming no further lectures are added (worst-case "safe skips from today").
  - **Lectures that must be attended consecutively** to reach the threshold if currently below it.
- FR-6.2: Formula must be shown in-app (transparency) — see §6.4 for exact math.
- FR-6.3: Predictor recalculates client-side from already-synced numbers (no extra API call).

### 2.7 Notifications
- FR-7.1: Trigger on: (a) new sync detects a change in the student's attendance, (b) percentage crosses below 75% (or admin-configured threshold), (c) student is newly flagged in the "Defaulters" sheet.
- FR-7.2: In-app notification center (bell icon, unread count) + optional email digest (configurable, off by default in v1).
- FR-7.3: Notifications are deduplicated — the same (student, subject, trigger-type, sync-run) combination never generates two notifications.

### 2.8 Admin Panel
- FR-8.1: Configure Google Sheet ID and validate service-account access (test-connection button).
- FR-8.2: Worksheet-to-subject mapping UI: map sheet tab name → subject code/name, define column roles (date, status, roll-no, remarks, faculty).
- FR-8.3: Configure sync interval (cron expression or preset dropdown: 5 min / 15 min / 1 hr / manual-only).
- FR-8.4: Configure rate limits/retry/backoff parameters (advanced settings, sane defaults pre-filled).
- FR-8.5: Student roster import (CSV or from "Student List" worksheet) — create/update `Student` records; supports upsert by roll number.
- FR-8.6: View sync logs (run history, status, rows processed, errors, duration).
- FR-8.7: View live sync status (idle / running / last success / last failure) with a manual "Sync Now" trigger (rate-limited to prevent abuse).
- FR-8.8: Manage user roles (promote/demote admin).

---

## 3. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Availability** | 99.5% uptime target for API/dashboard (excludes scheduled Sheets API outages). |
| **Performance** | Dashboard TTFB < 300ms (cached data), full page interactive < 2s on 4G. |
| **Sync Latency** | Default interval 15 min; configurable down to 5 min (bounded by Google Sheets API quota — see §11.5). |
| **Scalability** | Support ≥ 5,000 students, ≥ 50 subjects, ≥ 20 concurrent admin sync configs per deployment without redesign. |
| **Data Consistency** | Sync is idempotent and eventually consistent; no partial-write states visible to API consumers (transactional upserts). |
| **Observability** | Structured JSON logs, Prometheus-compatible metrics endpoint, sync run history persisted (`SyncLogs`). |
| **Security** | OWASP ASVS L2 baseline; see §18. |
| **Portability** | Fully containerized (Docker Compose for local/self-host); cloud-deployable per §14. |
| **Maintainability** | TypeScript strict mode across stack; ESLint + Prettier enforced in CI; ≥ 70% unit test coverage on backend business logic. |
| **Accessibility** | WCAG 2.1 AA — keyboard navigable, ARIA labels on charts/tables, color-blind-safe status colors (not color-only encoding). |
| **Internationalization** | English only in v1; string externalization structured for future i18n. |

---

## 4. Architecture

### 4.1 System Context (C4 Level 1)

```
┌─────────────┐        ┌──────────────────┐        ┌─────────────────────┐
│   Faculty   │──marks──▶  Google Forms    │──saves──▶   Google Sheets     │
└─────────────┘        └──────────────────┘        │  (source of truth,  │
                                                     │   read-only to us)  │
                                                     └──────────┬───────────┘
                                                                │ Sheets API
                                                                │ (read-only, polled)
                                                     ┌──────────▼───────────┐
                                                     │   OpenAttend Backend  │
                                                     │  (Node.js/NestJS +    │
                                                     │   Sync Worker)        │
                                                     └──────────┬───────────┘
                                                                │ SQL
                                                     ┌──────────▼───────────┐
                                                     │   PostgreSQL (Prisma)│
                                                     └──────────┬───────────┘
                                                                │ REST/JSON (JWT)
                                                     ┌──────────▼───────────┐
                                                     │  OpenAttend Frontend  │
                                                     │  (React/Vite SPA)    │
                                                     └──────────┬───────────┘
                                                                │ HTTPS
                                                     ┌──────────▼───────────┐
                                                     │  Student / Admin      │
                                                     └───────────────────────┘
```

### 4.2 C4 Level 2 — Containers

- **Web App (SPA)** — React + Vite + TS, deployed on Vercel, talks only to the Backend REST API.
- **API Server** — NestJS (or Express) exposing REST endpoints, JWT auth middleware, RBAC guards.
- **Sync Worker** — A scheduled process (can run in-process as a NestJS `@Cron` task, or as a separate worker service/queue consumer for horizontal scaling) that talks to the Google Sheets API and writes to Postgres.
- **PostgreSQL** — System of record for normalized attendance data, users, logs.
- **Google Sheets API** — External read-only dependency.
- **(Optional) Redis** — For job queue (BullMQ) if sync is split into a distributed worker, and for rate-limiting/session-blacklist storage.

### 4.3 Design Principle: Strict Read-Only Boundary

The Sheets API client is wrapped in a single module (`sync/sheets-client.ts`) that **only exposes `read` methods** (`spreadsheets.values.get`, `spreadsheets.values.batchGet`, `spreadsheets.get` for metadata). No `update`/`append`/`clear` scopes are requested from Google, and the OAuth scope granted to the service account is `spreadsheets.readonly` — this makes write-back structurally impossible, not just a coding convention.

---

## 5. High-Level Design

### 5.1 Major Modules

| Module | Responsibility |
|---|---|
| `auth` | Login, JWT issuance/refresh, RBAC guards, password reset |
| `students` | Student profile CRUD (admin-driven), roster import |
| `subjects` | Subject CRUD, worksheet-mapping CRUD |
| `attendance` | Query layer over normalized attendance data (overall/subject/history) |
| `analytics` | Aggregation queries (trends, comparisons) — computed on read, optionally materialized |
| `predictor` | Pure-function eligibility calculator (§6.4), stateless |
| `notifications` | Notification generation (triggered post-sync) + delivery (in-app, optional email) |
| `sync` | Google Sheets polling, diffing, normalization, upsert, checkpointing, logging |
| `admin` | Sheet/worksheet config, sync control, log viewing, role management |

### 5.2 Data Flow — Sync to Dashboard

1. Scheduler triggers `SyncWorker.run(sheetConfigId)` on interval.
2. Worker fetches worksheet metadata (`spreadsheets.get`) to check `sheets[].properties` revision hints and per-tab `gridProperties`.
3. Worker fetches values (`spreadsheets.values.batchGet`) for all mapped ranges.
4. Worker computes a content hash per worksheet range; compares to `SyncLogs.lastHash` checkpoint.
5. If unchanged → mark run as `SKIPPED_NO_CHANGE`, exit early (saves DB writes and quota).
6. If changed → normalize rows per worksheet mapping rules (§11.2) → diff against existing DB rows by natural key → upsert only changed/new rows inside a DB transaction.
7. Post-transaction: compute derived notification triggers (threshold crossed, new defaulter) → enqueue notifications.
8. Write `SyncLogs` row: status, rowsRead, rowsUpserted, duration, error (if any), new checkpoint hash.
9. Frontend, on next poll/query, reads only from Postgres — sync latency is the only staleness bound.

---

## 6. Low-Level Design

### 6.1 Sync Worker — Component Detail

```
SyncOrchestrator
 ├─ SheetsClient (read-only wrapper over googleapis)
 ├─ WorksheetMapper (config-driven row → entity transformer)
 ├─ Differ (hash-based change detection, per range)
 ├─ UpsertEngine (Prisma transactional batch upsert)
 ├─ NotificationTrigger (post-commit hook)
 └─ SyncLogger (writes SyncLogs, emits metrics)
```

### 6.2 Change Detection Strategy

Two layers, cheapest-first:

1. **Sheet-level ETag / revision check** (cheap): Google Sheets API doesn't expose a direct ETag on `values.get`, so OpenAttend uses `spreadsheets.get(fields="sheets.properties")` — if available in the account's Drive API scope, cross-reference `Drive.files.get(fileId, fields="modifiedTime")` (requires `drive.metadata.readonly` scope, optional but recommended). If `modifiedTime` is unchanged since last checkpoint, **skip entirely**.
2. **Range-level content hash** (authoritative fallback): SHA-256 over the serialized 2D value array per mapped range. Stored as `SyncCheckpoint.contentHash` per worksheet. If Drive metadata is unavailable/unauthorized, this is the primary check.

```ts
function computeRangeHash(values: string[][]): string {
  const serialized = JSON.stringify(values);
  return crypto.createHash('sha256').update(serialized).digest('hex');
}
```

### 6.3 Idempotent Upsert Key Strategy

Every synced row must map to a **natural key** that's stable across syncs, since Sheets rows have no reliable primary key of their own (row insertion/deletion shifts sheet row numbers).

- **Attendance record natural key:** `(studentRollNo, subjectCode, lectureDate, sessionIndex)`
  - `sessionIndex` handles the (rare) case of two lectures of the same subject on the same date (default `0`).
- Prisma upsert uses a compound unique constraint on this tuple (see §7.2 `Attendance` model `@@unique`).
- Because the key is content-derived (not row-position-derived), inserting/deleting unrelated rows in the Sheet does not create duplicates or orphan records.

### 6.4 Predictor Algorithm (exact math)

Given:
- `P` = lectures present, `A` = lectures absent, `T = P + A` = lectures held so far
- `threshold` = required % (default 0.75)
- `R` = remaining lectures planned for the term (admin-configured per subject; if unset, predictor only shows "safe skips assuming no more lectures are added")

**Current %:** `current_pct = P / T` (guard `T = 0` → "No data yet")

**Case A — Above threshold, "how many can I still miss":**
Find max `x ≥ 0` such that `P / (T + x) ≥ threshold`, bounded by `x ≤ R`.

```
x = floor(P / threshold) - T
safe_skips = max(0, min(x, R))
```

**Case B — Below threshold, "how many must I attend consecutively":**
Find min `y ≥ 0` such that `(P + y) / (T + y) ≥ threshold`, bounded by `y ≤ R`.

```
y = ceil((threshold * T - P) / (1 - threshold))
must_attend = max(0, min(y, R))
```
If `y > R`: predictor shows *"Not mathematically recoverable within remaining lectures — you will not reach {threshold*100}% this term"* rather than a false number.

Both formulas are pure functions, unit-tested with boundary cases (`T=0`, `P=T`, `threshold=1.0`, `R=0`).

### 6.5 Notification Deduplication

`Notification` rows carry a **unique constraint** on `(studentId, subjectId, type, syncLogId)`. The `NotificationTrigger` step runs inside the same transaction as the upsert batch and uses `createMany({ skipDuplicates: true })`, guaranteeing exactly-once notification emission per real change event.

---

## 7. Database Schema

### 7.1 Entity-Relationship Diagram (textual)

```
Users ──1:1── Students
Users ──1:N── AdminSettings (createdBy)
Students ──1:N── AttendanceRecord
Students ──1:N── Notification
Subjects ──1:N── AttendanceRecord
Subjects ──1:1── WorksheetMapping
AttendanceRecord ──1:N── AttendanceHistoryEvent (audit trail of changes to a record)
SyncLogs ──1:N── Notification (origin sync run)
SyncLogs ──N:1── WorksheetMapping
```

### 7.2 Prisma Schema (sample)

```prisma
// schema.prisma

generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum Role {
  STUDENT
  ADMIN
  SUPER_ADMIN
}

enum AttendanceStatus {
  PRESENT
  ABSENT
  NA // no lecture held that day, or excused
}

enum SyncRunStatus {
  SUCCESS
  SKIPPED_NO_CHANGE
  PARTIAL_FAILURE
  FAILED
}

model User {
  id           String   @id @default(cuid())
  email        String   @unique
  passwordHash String?
  role         Role     @default(STUDENT)
  isActive     Boolean  @default(true)
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt

  student      Student?

  @@index([role])
}

model Student {
  id          String   @id @default(cuid())
  userId      String   @unique
  user        User     @relation(fields: [userId], references: [id], onDelete: Cascade)
  rollNo      String   @unique
  name        String
  division    String?
  batch       String?
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  attendance    AttendanceRecord[]
  notifications Notification[]

  @@index([rollNo])
  @@index([division])
}

model Subject {
  id           String   @id @default(cuid())
  code         String   @unique // e.g. "OS", "AI", "IOT"
  name         String
  totalPlanned Int?     // R in predictor formula; optional
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt

  worksheetMapping WorksheetMapping?
  attendance       AttendanceRecord[]

  @@index([code])
}

model WorksheetMapping {
  id            String   @id @default(cuid())
  subjectId     String   @unique
  subject       Subject  @relation(fields: [subjectId], references: [id], onDelete: Cascade)
  sheetId       String   // Google Spreadsheet ID (shared across mappings typically)
  worksheetName String   // tab name, e.g. "OS"
  range         String   // e.g. "A1:F500"
  columnRoles   Json     // { date: "A", rollNo: "B", status: "C", faculty: "D", remarks: "E" }
  isActive      Boolean  @default(true)
  createdAt     DateTime @default(now())
  updatedAt     DateTime @updatedAt

  syncLogs      SyncLog[]

  @@index([sheetId])
}

model AttendanceRecord {
  id            String            @id @default(cuid())
  studentId     String
  student       Student           @relation(fields: [studentId], references: [id], onDelete: Cascade)
  subjectId     String
  subject       Subject           @relation(fields: [subjectId], references: [id], onDelete: Cascade)
  lectureDate   DateTime          @db.Date
  sessionIndex  Int               @default(0)
  status        AttendanceStatus
  faculty       String?
  remarks       String?
  sourceRowHash String            // hash of the raw sheet row, for change detection at row level
  syncedAt      DateTime          @default(now())
  updatedAt     DateTime          @updatedAt

  history       AttendanceHistoryEvent[]

  @@unique([studentId, subjectId, lectureDate, sessionIndex], name: "attendance_natural_key")
  @@index([studentId, subjectId])
  @@index([lectureDate])
}

model AttendanceHistoryEvent {
  id                 String           @id @default(cuid())
  attendanceRecordId String
  attendanceRecord   AttendanceRecord @relation(fields: [attendanceRecordId], references: [id], onDelete: Cascade)
  previousStatus     AttendanceStatus?
  newStatus          AttendanceStatus
  changedAt          DateTime         @default(now())
  syncLogId          String?

  @@index([attendanceRecordId])
}

model Notification {
  id         String   @id @default(cuid())
  studentId  String
  student    Student  @relation(fields: [studentId], references: [id], onDelete: Cascade)
  subjectId  String?
  type       String   // "THRESHOLD_BREACH" | "ATTENDANCE_UPDATE" | "DEFAULTER_FLAGGED"
  message    String
  isRead     Boolean  @default(false)
  syncLogId  String?
  syncLog    SyncLog? @relation(fields: [syncLogId], references: [id])
  createdAt  DateTime @default(now())

  @@unique([studentId, subjectId, type, syncLogId], name: "notification_dedup_key")
  @@index([studentId, isRead])
}

model SyncLog {
  id                String            @id @default(cuid())
  worksheetMappingId String
  worksheetMapping  WorksheetMapping  @relation(fields: [worksheetMappingId], references: [id], onDelete: Cascade)
  status            SyncRunStatus
  rowsRead          Int               @default(0)
  rowsUpserted      Int               @default(0)
  contentHash       String?
  errorMessage      String?
  startedAt         DateTime          @default(now())
  finishedAt        DateTime?
  durationMs        Int?

  notifications     Notification[]

  @@index([worksheetMappingId, startedAt])
  @@index([status])
}

model AdminSettings {
  id                String   @id @default(cuid())
  key               String   @unique // e.g. "sync.interval.cron", "notifications.threshold"
  value             String
  updatedByUserId   String?
  updatedAt         DateTime @updatedAt

  @@index([key])
}
```

### 7.3 Indexing Notes

- `AttendanceRecord` composite unique index doubles as the upsert conflict target (`ON CONFLICT (studentId, subjectId, lectureDate, sessionIndex) DO UPDATE`).
- `(studentId, subjectId)` index supports the dashboard's most common query pattern (fetch all records for one student, optionally filtered by subject).
- `SyncLog(worksheetMappingId, startedAt)` supports the admin "last N runs per worksheet" log view without a full scan.

---

## 8. API Specifications

Base URL: `/api/v1`. All authenticated routes require `Authorization: Bearer <JWT>`.

### 8.1 Auth

**POST `/auth/login`**
```json
// Request
{ "email": "student@ves.ac.in", "password": "••••••••" }
// 200 Response
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "user": { "id": "clx123", "email": "student@ves.ac.in", "role": "STUDENT" }
}
// 401
{ "error": "INVALID_CREDENTIALS", "message": "Email or password is incorrect." }
```

**POST `/auth/refresh`** → `{ "refreshToken": "..." }` → `200 { "accessToken": "..." }` / `401 TOKEN_EXPIRED`

**POST `/auth/logout`** → `204 No Content`

### 8.2 Student Profile

**GET `/students/me`**
```json
// 200
{
  "id": "clx123", "rollNo": "16010123", "name": "Vedant Sharma",
  "division": "A", "batch": "2024-2028",
  "overallPct": 82.4, "lastSyncAt": "2026-08-04T09:15:00Z"
}
```

### 8.3 Overall Attendance

**GET `/attendance/overall`**
```json
// 200
{
  "overallPct": 82.4,
  "present": 141, "absent": 30, "total": 171,
  "lastSyncAt": "2026-08-04T09:15:00Z",
  "isDefaulter": false
}
```

### 8.4 Subject Attendance

**GET `/attendance/subjects`** → list of per-subject summaries
```json
// 200
[
  { "subjectCode": "OS", "subjectName": "Operating Systems", "pct": 78.9, "present": 30, "absent": 8, "total": 38 },
  { "subjectCode": "AI", "subjectName": "Artificial Intelligence", "pct": 65.0, "present": 26, "absent": 14, "total": 40 }
]
```

**GET `/attendance/subjects/:code`**
```json
// 200
{
  "subjectCode": "OS", "pct": 78.9, "present": 30, "absent": 8, "total": 38,
  "predictor": { "safeSkips": 2, "mustAttend": null, "threshold": 75, "recoverable": true }
}
// 404
{ "error": "SUBJECT_NOT_FOUND" }
```

### 8.5 Attendance History

**GET `/attendance/history?subject=OS&from=2026-06-01&to=2026-08-01&status=ABSENT`**
```json
// 200
{
  "records": [
    { "date": "2026-07-30", "subject": "OS", "status": "ABSENT", "faculty": "Dr. Rao", "remarks": null }
  ],
  "page": 1, "pageSize": 50, "total": 8
}
```

### 8.6 Notifications

**GET `/notifications?unreadOnly=true`** → paginated list
**PATCH `/notifications/:id/read`** → `204`
**PATCH `/notifications/read-all`** → `204`

### 8.7 Synchronization (Admin-only)

**POST `/admin/sync/trigger`** (rate-limited: 1 request / 5 min / admin)
```json
// 202
{ "status": "QUEUED", "worksheetMappingId": "clxabc" }
// 429
{ "error": "RATE_LIMITED", "retryAfterSeconds": 214 }
```

**GET `/admin/sync/status`**
```json
// 200
[
  { "worksheet": "OS", "lastRun": "2026-08-04T09:15:00Z", "status": "SUCCESS", "rowsUpserted": 4 },
  { "worksheet": "AI", "lastRun": "2026-08-04T09:00:00Z", "status": "SKIPPED_NO_CHANGE", "rowsUpserted": 0 }
]
```

**GET `/admin/sync/logs?worksheet=OS&limit=20`** → paginated `SyncLog` history.

### 8.8 Admin — Config

**PUT `/admin/config/sheet`**
```json
// Request
{ "sheetId": "1a2b3c...", "serviceAccountEmail": "sync@openattend.iam.gserviceaccount.com" }
// 200
{ "status": "VERIFIED", "sheetsFound": ["Student List", "OS", "AI", "Defaulters"] }
// 400
{ "error": "SHEET_ACCESS_DENIED", "message": "Service account does not have read access to this sheet." }
```

**POST `/admin/config/worksheet-mapping`**
```json
{
  "subjectCode": "OS", "worksheetName": "OS", "range": "A1:F500",
  "columnRoles": { "date": "A", "rollNo": "B", "status": "C", "faculty": "D", "remarks": "E" }
}
```

**PUT `/admin/config/sync-interval`** → `{ "cron": "*/15 * * * *" }`

**POST `/admin/students/import`** → multipart CSV upload → `202 { "imported": 240, "updated": 12, "errors": [] }`

### 8.9 Standard Error Envelope

All 4xx/5xx responses follow:
```json
{ "error": "MACHINE_READABLE_CODE", "message": "Human-readable explanation.", "details": {} }
```

| HTTP | Meaning |
|---|---|
| 400 | Validation error / malformed request |
| 401 | Missing/expired/invalid JWT |
| 403 | Authenticated but insufficient role (RBAC) |
| 404 | Resource not found |
| 409 | Conflict (e.g., duplicate worksheet mapping) |
| 429 | Rate limited |
| 500 | Unhandled server error |
| 502 | Upstream Google Sheets API failure |

---

## 9. Folder Structure

```
openattend/
├── apps/
│   ├── web/                        # React + Vite frontend
│   │   ├── src/
│   │   │   ├── components/
│   │   │   ├── pages/
│   │   │   │   ├── dashboard/
│   │   │   │   ├── subject/
│   │   │   │   ├── analytics/
│   │   │   │   ├── admin/
│   │   │   │   └── auth/
│   │   │   ├── hooks/               # React Query hooks per resource
│   │   │   ├── lib/                 # api client, predictor client util
│   │   │   ├── charts/              # Recharts wrappers
│   │   │   └── styles/
│   │   ├── index.html
│   │   ├── vite.config.ts
│   │   └── package.json
│   │
│   └── api/                        # NestJS backend
│       ├── src/
│       │   ├── auth/
│       │   ├── students/
│       │   ├── subjects/
│       │   ├── attendance/
│       │   ├── analytics/
│       │   ├── predictor/
│       │   ├── notifications/
│       │   ├── sync/
│       │   │   ├── sheets-client.ts
│       │   │   ├── worksheet-mapper.ts
│       │   │   ├── differ.ts
│       │   │   ├── upsert-engine.ts
│       │   │   ├── sync.scheduler.ts
│       │   │   └── sync.module.ts
│       │   ├── admin/
│       │   ├── common/              # guards, interceptors, filters, decorators
│       │   ├── prisma/
│       │   │   └── schema.prisma
│       │   ├── main.ts
│       │   └── app.module.ts
│       ├── test/
│       └── package.json
│
├── packages/
│   ├── shared-types/                # DTOs shared between web & api
│   └── predictor-core/              # pure predictor math, unit-testable standalone
│
├── infra/
│   ├── docker/
│   │   ├── Dockerfile.api
│   │   ├── Dockerfile.web
│   │   └── docker-compose.yml
│   └── github-actions/
│       ├── ci.yml
│       └── deploy.yml
│
├── docs/
│   ├── architecture.md
│   ├── er-diagram.png
│   ├── api-docs/ (OpenAPI spec)
│   └── setup-guide.md
│
├── .github/
│   ├── ISSUE_TEMPLATE/
│   └── workflows/
├── CONTRIBUTING.md
├── LICENSE (MIT)
├── README.md
└── package.json (workspaces root)
```

---

## 10. Tech Justification

| Choice | Why |
|---|---|
| **React + Vite + TS** | Fast HMR dev loop, strong typing catches Sheets-schema-drift bugs at compile time in DTOs, huge ecosystem for charts/UI. |
| **TailwindCSS + shadcn/ui** | Utility-first styling matches the "Linear/Notion aesthetic" requirement without hand-rolling a design system; shadcn gives accessible, unstyled-by-default primitives. |
| **React Query** | Server-state caching/invalidation is exactly the model needed for polling-based dashboards (background refetch, stale-while-revalidate) — avoids hand-rolled polling logic. |
| **Recharts** | Declarative, React-native charting sufficient for line/bar comparisons; lighter than D3 for this scope. |
| **NestJS over raw Express** | Built-in DI, guards (perfect for RBAC), module boundaries mirror the module table in §5.1, first-class `@Cron` scheduling for the sync worker without extra infra. Express remains a valid lighter-weight fallback for smaller self-hosted deployments. |
| **Prisma ORM** | Type-safe query builder, migration tooling, and native `upsert`/`createMany({skipDuplicates})` map directly onto the idempotent-sync requirement (§6.3). |
| **PostgreSQL** | Transactional upserts, strong indexing, `JSONB` for flexible `columnRoles` config, mature and self-hostable — no vendor lock-in, aligning with open-source goals. |
| **JWT (access+refresh)** | Stateless auth scales horizontally without sticky sessions; refresh-token rotation limits blast radius of token theft. |
| **Google Sheets API (read-only scope)** | Only viable integration point that touches zero faculty workflow; read-only scope structurally enforces NG2. |
| **Vercel (frontend) / Railway or Render (backend)** | Both offer generous free/hobby tiers suited to college-scale open-source deployments, git-push-to-deploy simplicity, and are decoupled so either can be swapped without touching the other. |
| **Docker Compose for local/self-host** | Any college IT admin can `docker compose up` without cloud accounts, critical for an open-source, self-hostable tool. |

---

## 11. Sync Workflow

### 11.1 Trigger Model

- Default: **cron-based** (`node-cron` / NestJS `@Cron`), interval admin-configurable, default `*/15 * * * *`.
- Manual: Admin "Sync Now" button → enqueues an immediate run, rate-limited to 1 per 5 minutes per worksheet to protect Sheets API quota.
- On backend boot: one "catch-up" sync run after a configurable grace delay (avoids thundering-herd on every deploy).

### 11.2 Worksheet Mapping Rules

Each `WorksheetMapping.columnRoles` (JSONB) defines which spreadsheet column maps to which logical field:

```json
{
  "date": "A",
  "rollNo": "B",
  "status": "C",
  "faculty": "D",
  "remarks": "E"
}
```

**Type coercion & validation per column:**

| Logical field | Expected sheet format | Coercion rule | On failure |
|---|---|---|---|
| `date` | `DD/MM/YYYY` or ISO | Parsed via `date-fns/parse`, normalized to `DATE` | Row skipped, logged as `MALFORMED_DATE` |
| `rollNo` | String matching roster | Trimmed, uppercased | If no matching `Student.rollNo` → row skipped, logged as `UNKNOWN_STUDENT` |
| `status` | `Present`/`Absent`/`P`/`A`/blank | Mapped via lookup table to `PRESENT`/`ABSENT`/`NA` | Unrecognized value → logged as `UNKNOWN_STATUS`, row skipped |
| `faculty` | Free text | Trimmed | Optional; null if absent |
| `remarks` | Free text | Trimmed, max 500 chars | Optional; truncated if longer |

**Formula-derived sheets** (`Overall %`, `Final Overall %`, `Defaulters`): treated as **computed views**, not raw sources. OpenAttend reads the *rendered value* (not the formula) via `valueRenderOption: FORMATTED_VALUE` for these, and uses them only for **cross-validation** (compare against internally computed %) and for detecting **Defaulter list membership** (drives notification FR-7.1c). The system's own computed percentages (from raw per-subject sheets) remain the source of truth shown in the UI, since formula sheets can lag or use slightly different rounding.

### 11.3 Idempotent Sync — Pseudocode

```
function runSync(worksheetMappingId):
    mapping = db.WorksheetMapping.find(worksheetMappingId)
    log = db.SyncLog.create({ worksheetMappingId, status: "RUNNING", startedAt: now() })

    try:
        raw = sheetsClient.batchGet(mapping.sheetId, [mapping.range])
        newHash = computeRangeHash(raw.values)

        if newHash == mapping.lastContentHash:
            log.update({ status: "SKIPPED_NO_CHANGE", finishedAt: now() })
            return

        rows = worksheetMapper.parse(raw.values, mapping.columnRoles)
        # rows = [{ rollNo, date, status, faculty, remarks, rawRowHash }, ...]

        validRows, errors = validate(rows)   # per §11.2 coercion table

        db.transaction:
            for row in validRows:
                student = db.Student.findByRollNo(row.rollNo)
                if not student:
                    errors.push({ row, reason: "UNKNOWN_STUDENT" })
                    continue

                existing = db.AttendanceRecord.findUnique({
                    studentId: student.id, subjectId: mapping.subjectId,
                    lectureDate: row.date, sessionIndex: 0
                })

                if existing and existing.sourceRowHash == row.rawRowHash:
                    continue  # unchanged, skip write entirely

                upserted = db.AttendanceRecord.upsert({
                    where: { natural_key },
                    update: { status: row.status, faculty, remarks, sourceRowHash, syncedAt: now() },
                    create: { ...row, studentId: student.id, subjectId: mapping.subjectId }
                })

                if existing and existing.status != upserted.status:
                    db.AttendanceHistoryEvent.create({
                        attendanceRecordId: upserted.id,
                        previousStatus: existing.status,
                        newStatus: upserted.status,
                        syncLogId: log.id
                    })
                    notificationTrigger.enqueue(student, mapping.subjectId, "ATTENDANCE_UPDATE", log.id)

                recomputeThresholdAndMaybeNotify(student, mapping.subjectId, log.id)

            mapping.update({ lastContentHash: newHash })
            log.update({
                status: errors.length > 0 ? "PARTIAL_FAILURE" : "SUCCESS",
                rowsRead: raw.values.length,
                rowsUpserted: validRows.length - errors.length,
                contentHash: newHash,
                finishedAt: now(),
                durationMs: now() - log.startedAt
            })

    catch (err):
        log.update({ status: "FAILED", errorMessage: err.message, finishedAt: now() })
        raise  # let scheduler's retry/backoff handle it
```

**Key idempotency guarantees:**
- Natural-key upsert (§6.3) → re-running never duplicates.
- Row-level `sourceRowHash` short-circuits unchanged rows even within a changed range → minimizes writes and history-event noise.
- Notification dedup constraint (§6.5) → re-running never double-notifies.
- Checkpoint (`lastContentHash`) only commits **after** the transaction succeeds → a crash mid-run leaves the checkpoint stale, so the *next* run safely reprocesses the same range (safe because of the two guarantees above).

### 11.4 Failure Modes & Handling

| Failure | Detection | Response |
|---|---|---|
| Google API quota exceeded (429) | HTTP 429 from googleapis | Exponential backoff (base 2s, max 5 retries, jitter), log `FAILED`, alert via metrics counter `sync_quota_exceeded_total` |
| Sheet access revoked | HTTP 403 | Immediate `FAILED`, admin-panel banner "Access revoked — reconnect service account" |
| Worksheet renamed/deleted | HTTP 400 "Unable to parse range" | `FAILED`, admin notified to re-map worksheet |
| Malformed rows (bad date/status) | Row-level validation | Row skipped, aggregated into `PARTIAL_FAILURE`, listed in log details |
| Unknown student (roll no. not in roster) | Lookup miss | Row skipped, surfaced as actionable admin todo ("12 unmatched roll numbers — import roster?") |
| DB transaction failure | Prisma exception | Full transaction rollback (all-or-nothing per run), `FAILED`, checkpoint untouched, retried next interval |
| Partial network timeout mid-fetch | Fetch throws/times out | Treated as `FAILED`, no partial data ever enters the transaction (fetch completes fully before any DB write begins) |

### 11.5 Rate Limits & Backoff Configuration

- Google Sheets API default quota: 60 read requests/min/user, 300/min/project (varies by tier) — OpenAttend batches all mapped ranges for a sheet into a single `batchGet` call per sync run to minimize request count.
- Configurable in Admin panel:
  - `syncIntervalCron` (default `*/15 * * * *`)
  - `maxRetries` (default 5)
  - `backoffBaseMs` (default 2000, exponential ×2 per retry, capped at 60000ms, ±20% jitter)
  - `manualSyncCooldownSeconds` (default 300)

---

## 12. Auth Flow

### 12.1 Login Sequence (text)

```
1. Student submits email+password (or Google OAuth) to POST /auth/login
2. Backend validates:
   a. Email domain matches configured college domain(s)
   b. bcrypt.compare(password, user.passwordHash) [password flow]
      OR Google ID token verified via google-auth-library [OAuth flow]
3. On success:
   - Issue accessToken (JWT, 15 min expiry, claims: sub, role, studentId)
   - Issue refreshToken (JWT, 7 day expiry, rotated on use, stored hashed in DB)
   - Return both to client
4. Client stores accessToken in memory (not localStorage), refreshToken in httpOnly Secure cookie
5. Subsequent requests: Authorization: Bearer <accessToken>
6. On 401 (expired access token): client calls POST /auth/refresh with refreshToken cookie
   - Backend validates refresh token against stored hash, rotates it (old one invalidated), issues new access token
7. On refresh failure (token reused/expired/revoked): force re-login, clear cookie
```

### 12.2 RBAC Guard Flow

```
Request → JwtAuthGuard (validates signature+expiry) → RolesGuard(requiredRoles) → Handler
```
`RolesGuard` reads `@Roles('ADMIN')` decorator metadata and compares against `req.user.role`; mismatch → `403 { error: "FORBIDDEN" }`.

### 12.3 RBAC Permission Matrix

| Action | STUDENT | ADMIN | SUPER_ADMIN |
|---|:---:|:---:|:---:|
| View own dashboard/attendance | ✅ | ✅ | ✅ |
| View other students' attendance | ❌ | ✅ (read-only) | ✅ |
| Configure Sheet ID / worksheet mapping | ❌ | ✅ | ✅ |
| Trigger manual sync | ❌ | ✅ | ✅ |
| View sync logs | ❌ | ✅ | ✅ |
| Import/edit student roster | ❌ | ✅ | ✅ |
| Manage user roles (promote/demote) | ❌ | ❌ | ✅ |
| Configure notification thresholds | ❌ | ✅ | ✅ |
| Delete a subject/worksheet mapping | ❌ | ✅ | ✅ |

---

## 13. UI Wireframes (Descriptions)

### 13.1 Student Dashboard (Home)
- Top bar: college logo/name, "last synced Xm ago" badge (green if <30 min, amber if stale), notification bell.
- Hero card: large circular progress ring showing Overall %, color-coded (green ≥75%, amber 65–75%, red <65%), with present/absent/total as sub-stats.
- Grid of subject cards below (2–3 columns responsive → 1 column mobile): each card = subject name, % as a mini progress bar, present/total, tap → subject page.
- Sidebar/bottom-nav: Dashboard, Analytics, History, Predictor, Notifications.

### 13.2 Subject Page
- Header: subject name, current %, large stat row (present/absent/total).
- Tabs: **History** | **Timeline** | **Calendar**.
  - History: sortable table (date, status badge, faculty, remarks).
  - Timeline: horizontal scrollable strip of dots (green=present, red=absent, grey=NA), most recent right-aligned.
  - Calendar: month-grid, each day cell color-coded, click → shows that day's record detail in a popover.
- Predictor widget docked at page bottom: "You can miss **2** more lectures" or "Attend the next **4** lectures to reach 75%" with the formula explained in a collapsible "How is this calculated?" info panel.

### 13.3 Analytics Page
- Toggle: Weekly / Monthly aggregation.
- Line chart: overall % trend over time, with a horizontal reference line at 75%.
- Bar chart: subject comparison, sorted ascending by % (risk-first).
- Trend badges next to each subject: ▲2.1% or ▼1.4% vs. previous period.

### 13.4 Notifications Center
- Slide-over panel from bell icon.
- List grouped by date, each item: icon (⚠ threshold breach / 🔔 update / 🚩 defaulter), message, relative time, unread dot.
- "Mark all read" action at top.

### 13.5 Admin Panel
- **Setup tab:** Sheet ID input + "Test Connection" button → shows detected tab names on success, error banner on failure.
- **Worksheet Mapping tab:** table of subject↔worksheet mappings, "+ Add Mapping" opens a form with column-role dropdowns (auto-suggests columns from a preview fetch).
- **Sync Control tab:** interval selector (dropdown of presets + custom cron input), "Sync Now" button (disabled during cooldown, shows countdown), live status cards per worksheet (idle/running/success/failed with last-run timestamp).
- **Logs tab:** paginated table of `SyncLog` entries, filterable by worksheet/status, expandable rows showing error details/skipped-row reasons.
- **Roster tab:** CSV upload dropzone + inline preview/validation before commit; existing roster table with search.

### 13.6 Visual Language
- Aesthetic reference: GitHub/Linear/Notion — generous whitespace, subtle borders over heavy shadows, monochrome-plus-one-accent color palette, Inter/system-ui typeface.
- Dark mode: true dark background (`#0d1117`-class), not just inverted colors.
- Light glassmorphism reserved for modal/overlay surfaces only, not overused.
- Skeleton loaders match the exact shape of the content they replace (card skeletons, table-row skeletons).
- Empty states: illustrated + actionable ("No attendance synced yet — check back after the next sync" for students; "No worksheet mapped — add one to get started" for admins).
- Error states: non-alarming tone, always paired with a retry action where applicable.

---

## 14. Deployment

### 14.1 Environments

| Env | Frontend | Backend | DB |
|---|---|---|---|
| Local/Dev | `vite dev` | `nest start --watch` | Docker Compose Postgres |
| Staging | Vercel Preview Deploy | Railway/Render staging service | Managed Postgres (staging instance) |
| Production | Vercel Production | Railway/Render production service | Managed Postgres (production instance, daily backups) |

### 14.2 Docker Compose (local/self-host)

```yaml
# infra/docker/docker-compose.yml
version: "3.9"
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: openattend
      POSTGRES_USER: openattend
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  api:
    build:
      context: ../../
      dockerfile: infra/docker/Dockerfile.api
    env_file: ../../.env
    depends_on: [postgres]
    ports:
      - "4000:4000"

  web:
    build:
      context: ../../
      dockerfile: infra/docker/Dockerfile.web
    environment:
      VITE_API_BASE_URL: http://localhost:4000/api/v1
    ports:
      - "5173:80"

volumes:
  pgdata:
```

### 14.3 Environment Variables

```
# Backend (.env)
DATABASE_URL=postgresql://user:pass@host:5432/openattend
JWT_ACCESS_SECRET=<32+ char random>
JWT_REFRESH_SECRET=<32+ char random>
JWT_ACCESS_EXPIRY=15m
JWT_REFRESH_EXPIRY=7d
GOOGLE_SERVICE_ACCOUNT_JSON=<base64-encoded key file>
ALLOWED_EMAIL_DOMAINS=ves.ac.in
SYNC_DEFAULT_CRON=*/15 * * * *
SYNC_MAX_RETRIES=5
SYNC_BACKOFF_BASE_MS=2000
NODE_ENV=production
LOG_LEVEL=info

# Frontend (.env)
VITE_API_BASE_URL=https://api.openattend.app/api/v1
```

### 14.4 CI/CD (GitHub Actions)

`ci.yml` — on every PR:
1. Install deps (`pnpm install --frozen-lockfile`)
2. Lint (`eslint .`) + typecheck (`tsc --noEmit`)
3. Unit tests (`vitest run` / `jest`) with coverage gate ≥70% on `apps/api/src`
4. `prisma migrate diff` check (no unapplied schema drift)
5. Build both apps (`vite build`, `nest build`)

`deploy.yml` — on merge to `main`:
1. Re-run CI gate
2. Build & push Docker images (tagged with commit SHA) to GHCR
3. Trigger Railway/Render deploy hook for `api`
4. Trigger Vercel deploy hook for `web` (or native Vercel git integration)
5. Run `prisma migrate deploy` against production DB as a pre-deploy step
6. Post-deploy smoke test: `GET /health` on both services

### 14.5 Observability

- **Logging:** structured JSON via `pino` (backend), correlation ID per request, sync-run ID attached to all sync-related log lines.
- **Metrics:** `/metrics` Prometheus endpoint exposing `sync_runs_total{status}`, `sync_duration_ms_bucket`, `sheets_api_errors_total`, `http_request_duration_ms_bucket`.
- **Health checks:** `GET /health` (liveness) and `GET /health/ready` (readiness — checks DB connectivity + last successful sync recency).
- **Alerting (recommended, not v1-mandatory):** wire metrics into Grafana/Prometheus or a hosted equivalent; alert on `sync_runs_total{status="FAILED"}` rate and stale-sync duration exceeding 2× configured interval.

---

## 15. Future Scope

- Multi-college / multi-tenant support (currently single-Sheet-per-deployment assumption).
- Native mobile apps (React Native) reusing `packages/shared-types` and API layer.
- Faculty-facing read-only insight view (attendance trends per class, without any write capability — preserves NG2).
- Push notifications (web push / FCM) in addition to in-app + email.
- Configurable per-subject attendance thresholds (currently one global default of 75%).
- ICS calendar export/subscription feed for a student's lecture-attendance calendar.
- Pluggable data-source adapters (e.g., Microsoft Forms/Excel Online) behind the same normalized schema, without touching core sync guarantees.
- ML-based "at-risk" early warning (trend-based prediction beyond the linear predictor in §6.4).

---

## 16. Roadmap

| Phase | Scope | Target Outcome |
|---|---|---|
| **M0 — Foundations** | Repo scaffolding, CI, Docker Compose, Prisma schema, auth module | Local dev environment fully runnable |
| **M1 — Sync Core** | Sheets client, worksheet mapper, differ, upsert engine, sync logs | Idempotent sync verified against a real sample Sheet |
| **M2 — Student Dashboard** | Overall/subject attendance APIs + dashboard UI, subject pages | Students can view live-ish attendance |
| **M3 — Analytics & Predictor** | Analytics endpoints/charts, predictor module (frontend+backend parity tests) | Full self-serve insight for students |
| **M4 — Notifications** | Notification generation, in-app center, optional email digest | Threshold-breach alerting live |
| **M5 — Admin Panel** | Full admin config UI, roster import, log viewer, sync control | Non-engineer admin can self-onboard a college |
| **M6 — Hardening** | Security audit pass (§18), load testing, observability dashboards | Production-readiness sign-off |
| **M7 — Open-Source Launch** | README, CONTRIBUTING, issue templates, architecture/ER diagrams, API docs, MIT license finalized | Public repo launch |

---

## 17. Testing Strategy

| Layer | Approach | Tooling |
|---|---|---|
| **Unit — Predictor** | Pure-function tests covering all boundary cases in §6.4 (`T=0`, `P=T`, threshold edge, unrecoverable case) | Vitest/Jest |
| **Unit — Worksheet Mapper** | Table-driven tests per column-role coercion rule (§11.2), including malformed inputs | Jest |
| **Unit — Differ/Hashing** | Hash stability across re-serialization, sensitivity to any single-cell change | Jest |
| **Integration — Sync Worker** | Run against a mocked Google Sheets API (recorded fixtures) + real Postgres (test container) to verify idempotency: run twice → assert zero duplicate rows/notifications | Jest + Testcontainers |
| **Integration — API** | Supertest against NestJS app instance with test DB; cover RBAC matrix (§12.3) — every endpoint × every role | Jest + Supertest |
| **Contract Tests** | OpenAPI spec validated against actual responses (schema drift detection) | `openapi-typescript` + Dredd or Schemathesis |
| **E2E** | Login → dashboard load → subject page → predictor value matches manually computed expectation | Playwright |
| **Load/Perf** | Simulate 5,000 students querying dashboard concurrently against seeded DB; sync run timing under a 500-row worksheet | k6 |
| **Security** | Dependency audit (`npm audit`/`pnpm audit` in CI), OWASP ZAP baseline scan against staging | CI-integrated |
| **Manual QA Checklist** | Cross-browser (Chrome/Firefox/Safari), mobile responsive breakpoints, dark mode visual pass, accessibility pass (axe DevTools) | Pre-release checklist |

**Coverage gate:** ≥70% line coverage on `apps/api/src` enforced in CI (§14.4); predictor and sync-differ modules require ≥90% due to correctness sensitivity.

---

## 18. Security Best Practices

1. **Authentication**
   - Passwords hashed with `bcrypt` (cost factor 12), never stored/logged in plaintext.
   - JWT access tokens short-lived (15 min); refresh tokens rotated on every use with reuse-detection (if an already-used refresh token is presented, all sessions for that user are revoked).
   - Email domain allowlist enforced server-side at registration/login, not just UI-hidden.

2. **Authorization**
   - Every mutating/admin route guarded by `RolesGuard` server-side — no client-side-only role checks.
   - Object-level checks: a `STUDENT` querying `/attendance/*` always scoped to `req.user.studentId`, never accepts a client-supplied student ID for that role.

3. **Secrets Management**
   - All secrets (`JWT_*_SECRET`, `GOOGLE_SERVICE_ACCOUNT_JSON`, `DATABASE_URL`) via environment variables only — never committed, `.env` in `.gitignore`, `.env.example` provided with placeholder values.
   - Google service-account key stored base64-encoded in a single env var (or platform secret manager) to avoid a JSON file on disk in production containers.

4. **Input Validation**
   - Every API DTO validated via `class-validator`/`zod` at the controller boundary — reject unknown fields (`whitelist: true`, `forbidNonWhitelisted: true`).
   - Sheets-sourced data treated as **untrusted input** even though it's "internal" — same validation rigor as user-submitted data (§11.2).

5. **Rate Limiting**
   - Global API rate limit (e.g., 100 req/min/IP) via `@nestjs/throttler`.
   - Stricter limits on `/auth/login` (5 attempts/15 min/IP) to blunt credential stuffing.
   - Manual sync trigger cooldown enforced server-side (§8.7), not just UI-disabled.

6. **Transport & Headers**
   - HTTPS enforced everywhere (HSTS header).
   - `helmet` middleware for standard security headers (CSP, X-Frame-Options, X-Content-Type-Options).
   - CORS restricted to the known frontend origin(s) only.

7. **Data Protection**
   - Refresh tokens stored **hashed** in DB (never plaintext), compared via constant-time comparison.
   - No PII beyond what's necessary (roll no., name, email, division) — no storage of sensitive categories.
   - DB backups encrypted at rest (provider-managed, e.g., Railway/Render managed Postgres encryption).

8. **Read-Only Boundary Enforcement**
   - Google OAuth scope requested is strictly `spreadsheets.readonly` (+ optional `drive.metadata.readonly`) — write scopes never requested, making NG2 a structural guarantee, not just a code-review convention.

9. **Dependency & Supply Chain**
   - Automated dependency audit in CI (`pnpm audit --audit-level=high` fails the build).
   - Dependabot/Renovate enabled for the open-source repo.

10. **Logging Hygiene**
    - No JWTs, passwords, or Google service-account keys ever logged, even at debug level (redaction middleware on `pino`).

---

*End of document.*