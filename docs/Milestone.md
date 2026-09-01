# OpenAttend — Milestones (Java Spring Boot)

> Derived from `PRD.md` and `ui.md`
> Purpose: Sequenced build plan for autonomous agent development. Each milestone is independently testable and unlocks the next. No milestone ships UI for data that isn't real — per `ui.md` §16, no mocked data survives past the milestone that introduces its backend.

---

## How to read this file

- Each milestone lists **Scope**, **Depends on**, **Definition of Done**, and **Demo / Verification**.
- "Definition of Done" defines the exact exit criteria that must pass before advancing to the next milestone.
- Milestones are ordered so the **riskiest, most failure-prone part (the sync engine & data model) is proven first**, before UI is connected.

---

## M0 — Foundations & Project Scaffolding

**Scope**
- Monorepo Java Spring Boot 3 structure (`backend/pom.xml`, Java 21 LTS, Spring Data JPA, Spring Web, Flyway, PostgreSQL driver).
- Flyway migration `V1__init_schema.sql` defining all enum types and tables (`users`, `students`, `subjects`, `worksheet_mappings`, `attendance_records`, `attendance_history_events`, `notifications`, `sync_logs`).
- JPA Entity classes and Spring Data Repositories for all domain models.
- Basic Health and Actuator controller (`GET /api/v1/health`, `GET /actuator/health`).
- Docker Compose configuration for PostgreSQL local development.
- `.env.example` and `application.yml` setup.

**Depends on:** Nothing.

**Definition of Done**
- `mvn compile` and `mvn test` pass cleanly with zero errors.
- Flyway migrations apply cleanly against PostgreSQL on application startup.
- `GET /api/v1/health` returns `200 OK` with JSON `{ "status": "ok", "service": "OpenAttend API" }`.

**Demo / Verification:** Run `mvn spring-boot:run` and verify `GET http://localhost:3000/api/v1/health`.

---

## M1 — Sync Engine Core (Google Sheets & Idempotent Upsert)

**Scope**
- `SheetsClient`: Read-only wrapper over Google Sheets API Client for Java, supporting Base64-encoded `GOOGLE_SERVICE_ACCOUNT_JSON` and raw JSON with scoped `spreadsheets.readonly`.
- `WorksheetMapper`: Column-role mapping engine (Date, RollNo, Status, Faculty, Remarks) with malformed-row handling.
- `SyncDiffer`: Range-level and row-level SHA-256 content hashing for instant short-circuiting.
- `UpsertEngine`: Atomic `@Transactional` natural-key batch upsert (`studentId, subjectId, lectureDate, sessionIndex`) with `sourceRowHash` check.
- `SyncLogger`: Audit logger writing `SyncLog` with error classification (`429_RATE_LIMITED`, `403_FORBIDDEN`, `MALFORMED_ROWS`, `DB_ERROR`).
- Unit & integration test suites verifying idempotency and history event generation.

**Depends on:** M0.

**Definition of Done**
- Running sync twice against unchanged data produces `SKIPPED_NO_CHANGE` with **zero** duplicate rows or history events.
- Mutating a single cell in the sheet creates exactly one `AttendanceHistoryEvent` on the subsequent sync.
- Malformed rows log itemized skip reasons and flag the run as `PARTIAL_FAILURE` without crashing the process.

**Demo / Verification:** Execute `UpsertEngineTest` proving SHA-256 short-circuiting and idempotent database transactions.

---

## M2 — Predictor Core Engine

**Scope**
- `PredictorService`: Pure Java service implementing mathematical projection formulas per PRD §6.4:
  - Safe Skips: $\lfloor \frac{P - (T \times \text{target})}{\text{target}} \rfloor$
  - Must Attend: $\lceil \frac{\text{target} \times T - P}{1 - \text{target}} \rceil$
  - Honor Threshold (85%) calculation & Unrecoverable state detection.
- Standalone DTOs and test suite covering boundary cases (`T=0`, `P=T`, `P=0`, 100% threshold, unreachable thresholds).

**Depends on:** M0.

**Definition of Done**
- 100% unit test coverage on `PredictorServiceTest` matching reference hand-calculated attendance vectors.

**Demo / Verification:** Run `mvn test -Dtest=PredictorServiceTest`.

---

## M3 — Scheduler & Admin Sync REST APIs

**Scope**
- Spring `@Scheduled` worker periodically invoking `UpsertEngine` based on configured cron interval (`openattend.sync.cron`).
- Admin REST Endpoints:
  - `POST /api/v1/admin/sheet/verify` (Test Sheet connection & detect tabs)
  - `POST /api/v1/admin/mapping` (Persist worksheet column mapping)
  - `POST /api/v1/admin/roster/preview` (CSV import with CREATE/UPDATE/ERROR diff preview)
  - `POST /api/v1/admin/sync/trigger` (Manual sync with cooldown rate limiting)
  - `GET /api/v1/admin/sync/logs` (Retrieve audit history logs)
- Spring Security RBAC guards restricting all `/api/v1/admin/**` endpoints to `ADMIN` and `SUPER_ADMIN` roles.

**Depends on:** M1.

**Definition of Done**
- All admin endpoints match OpenAPI specification and return exact status codes (`429` on rapid re-trigger).
- Role check proven: Requests with `STUDENT` token receive `403 Forbidden` on every admin endpoint.

**Demo / Verification:** Execute admin integration tests verifying sheet verification, manual sync trigger, and RBAC rejection.

---

## M4 — Auth & Student-Facing Read APIs

**Scope**
- Spring Security 6 stateless JWT filter chain (`JwtAuthenticationFilter`, `JwtTokenProvider`).
- Institutional email domain restriction (`@ves.ac.in`) with BCrypt password hashing.
- REST Endpoints:
  - `POST /api/v1/auth/login` (Returns JWT access token and user profile)
  - `GET /api/v1/auth/me` (Validates token and returns active session)
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/attendance/overall` (Overall percentage, counts, and integrated Predictor data)
  - `GET /api/v1/attendance/subjects` (Per-subject breakdown with safe skips)
  - `GET /api/v1/attendance/history` (Filterable roll-call history records)
- Object-level security: Students can only retrieve data belonging to their own authenticated `studentId`.

**Depends on:** M1, M2, M3.

**Definition of Done**
- Login rejects non-`@ves.ac.in` email addresses with `400 Bad Request`.
- Student read endpoints query PostgreSQL only and complete in `<50ms` without calling external Google APIs.
- Object-level security integration tests pass.

**Demo / Verification:** Authenticate via `POST /api/v1/auth/login` and query `/api/v1/attendance/overall`.

---

## M5 — Student Dashboard UI Integration

**Scope**
- Connect existing PWA frontend (`index.html`) to live Spring Boot backend.
- Configure Spring Web MVC static asset handler or CORS headers so `index.html` loads directly from `http://localhost:3000`.
- Wire live data to:
  - Hero Percentage Ring & Attendance Summary
  - Predictor Safe Skips / Must Attend Card
  - Subject Cards Grid & Defaulter Warning Banner
  - Login / Logout state management and token persistence in `localStorage`.

**Depends on:** M4.

**Definition of Done**
- Login as `student@ves.ac.in` displays live attendance metrics loaded from the Spring Boot API.
- Zero mock data in active views; error states render cleanly if the backend is unreachable.

**Demo / Verification:** Log in via web browser at `http://localhost:3000` and view live student dashboard metrics.

---

## M6 — Analytics, History & Notifications

**Scope**
- Backend:
  - `GET /api/v1/attendance/analytics` (Weekly trends and subject deltas)
  - `GET /api/v1/notifications`
  - `PATCH /api/v1/notifications/{id}/read`
  - `PATCH /api/v1/notifications/read-all`
  - Automated notification triggers on threshold breach during sync runs with deduplication constraint.
- Frontend:
  - Connect History table with filtering and CSV export.
  - Connect Analytics trend lines and subject comparison deltas.
  - Connect Notification bell dropdown and unread counter.

**Depends on:** M3, M5.

**Definition of Done**
- Re-running a sync generates zero duplicate notifications.
- Threshold breach triggers a notification when a student drops below 75%.
- History CSV export downloads accurate filtered records.

**Demo / Verification:** Trigger a sync that reduces attendance below 75% and inspect the real-time notification in the UI.

---

## M7 — Admin Panel UI Integration

**Scope**
- Connect Admin screens in `index.html` to live Spring Boot backend:
  - Sheet Connection & Tab Detection
  - Worksheet Column Mapping Interface
  - Roster CSV Upload & Diff Preview
  - Manual Sync Trigger with Cooldown Timer
  - Sync Audit Logs Viewer with expandable error details.

**Depends on:** M3, M5.

**Definition of Done**
- Admin interface executes cold-start flow end-to-end against live backend endpoints.
- Role switcher allows Admin users to toggle between Student Dashboard and Admin Portal.

**Demo / Verification:** Perform complete admin setup walkthrough (Connect Sheet ➔ Map Columns ➔ Sync ➔ View Logs).

---

## M8 — Production Hardening, Actuator & Packaging

**Scope**
- Spring Boot Actuator: `/actuator/health`, `/actuator/info`, `/actuator/prometheus` metrics.
- Security Headers (Content Security Policy, X-Content-Type-Options, Strict-Transport-Security).
- Multi-stage `Dockerfile` creating an optimized container image.
- Comprehensive end-to-end integration test sweep across all endpoints and RBAC roles.
- Cleanup of legacy Node.js backend files (`api/`, `apps/api/`, `packages/`, `dev-server.js`).

**Depends on:** M6, M7.

**Definition of Done**
- All automated unit and integration tests pass (`mvn test`).
- Actuator endpoints provide live health and Prometheus metrics.
- Docker image builds and runs successfully.

**Demo / Verification:** Run `mvn clean package` and launch the containerized application.

---

## M9 — Open-Source Documentation & Launch

**Scope**
- Comprehensive `README.md`, `CONTRIBUTING.md`, `docs/setup-guide.md`, and OpenAPI JSON specification.
- Clean repository structure ready for public contributions.

**Depends on:** M8.

**Definition of Done**
- Fresh clone can be launched following `docs/setup-guide.md` with zero undocumented steps.

---

## Invariants That Must Never Regress

- **Strict Read-Only Boundary**: No Google write scopes requested anywhere.
- **Zero Synchronous External Calls**: Student read endpoints query PostgreSQL only.
- **Idempotency**: Repeated sync runs never duplicate records or history events.
- **Institutional Email Validation**: Non-`@ves.ac.in` accounts cannot authenticate.