# OpenAttend — Milestones

> Derived from `PRD.md` (v0.1) and `ui.md`
> Purpose: sequenced build plan for engineering + agentic coding tools. Each milestone is independently demoable and unlocks the next. No milestone ships UI for data that isn't real — per `ui.md` §16, no mocked data survives past the milestone that introduces its backend.

---

## How to read this file

- Each milestone lists **Scope**, **Depends on**, **Definition of Done**, and **Demo**.
- "Demo" is the concrete thing you should be able to show someone at the end of the milestone — if you can't do that, the milestone isn't done.
- Milestones are ordered so the **riskiest, most failure-prone part (the sync engine) ships first**, before any UI is built on top of it. Idempotency bugs are far cheaper to find against a raw API/DB than after a dashboard is layered on top.
- PRD/UI section references let an agent or engineer jump straight to the authoritative spec for each piece.

---

## M0 — Foundations

**Scope**
- Monorepo scaffolding per PRD §9 folder structure (`apps/web`, `apps/api`, `packages/shared-types`, `packages/predictor-core`, `infra/`, `docs/`).
- Prisma schema committed (PRD §7.2), initial migration against a local Postgres via Docker Compose (PRD §14.2).
- Auth module skeleton: `User` model, JWT issuance scaffolding, RBAC guard scaffolding (no login UI yet).
- ESLint + Prettier + TypeScript strict mode across all packages.
- GitHub Actions `ci.yml`: install → lint → typecheck → build (PRD §14.4), green on an empty-but-real app.
- `.env.example` with every variable from PRD §14.3, no real secrets committed.

**Depends on:** nothing.

**Definition of Done**
- `docker compose up` boots Postgres + API + a placeholder web app locally with zero manual steps beyond copying `.env.example`.
- `prisma migrate dev` applies cleanly from a fresh DB.
- CI is green on a trivial PR.

**Demo:** `docker compose up`, hit `GET /health` and get `200`, show the empty Prisma Studio with all tables from §7.2 present.

---

## M1 — Sync Engine Core (the hard part, built and proven first)

**Scope** (PRD §6.1–§6.5, §11)
- `SheetsClient` — read-only wrapper over `googleapis`, scoped strictly to `spreadsheets.readonly` (+ optional `drive.metadata.readonly`). No write methods exposed, structurally (PRD §4.3, §18.8).
- `WorksheetMapper` — config-driven row → entity transformer with the full column-role coercion table (PRD §11.2): date parsing, roll-no matching, status lookup, malformed-row handling.
- `Differ` — range-level SHA-256 content hashing (PRD §6.2) + optional Drive `modifiedTime` short-circuit.
- `UpsertEngine` — natural-key transactional upsert (`studentId, subjectId, lectureDate, sessionIndex`) with row-level `sourceRowHash` short-circuiting (PRD §6.3, §11.3 pseudocode implemented literally).
- `SyncLogger` — writes `SyncLog` rows, all five failure modes from PRD §11.4 handled explicitly (quota 429, access revoked 403, worksheet renamed/deleted, malformed rows, unknown student, DB transaction failure, partial network timeout).
- Retry/backoff per PRD §11.5 (exponential, jittered, configurable via env defaults).
- No scheduler yet — sync runs are triggered manually via a CLI script or an internal test endpoint only.

**Depends on:** M0.

**Definition of Done**
- Integration test (PRD §17): run sync twice against a fixture/mocked Sheet with zero changes between runs → assert **zero** new rows, zero duplicate `SyncLog` entries, zero duplicate `AttendanceRecord`s.
- Integration test: run sync, mutate one cell in the fixture, run again → assert exactly one `AttendanceHistoryEvent` created, exactly one row updated, everything else untouched (row-level hash short-circuit proven, not just range-level).
- Integration test: inject a malformed date, an unknown roll number, and a duplicate-lecture-same-day case into the fixture → all three land in `SyncLog` as itemized skip reasons, sync run still completes as `PARTIAL_FAILURE` rather than crashing.
- Unit tests on `Differ` hashing stability and sensitivity (≥90% coverage per PRD §17 coverage gate for sync-differ modules).
- A crash injected mid-transaction leaves the checkpoint untouched and the next run safely reprocesses without duplicating (idempotency-after-crash proven, PRD §11.3).

**Demo:** run the sync CLI twice against a real (test) Google Sheet, show the `SyncLog` table in Prisma Studio proving the second run is `SKIPPED_NO_CHANGE`; then edit a cell in the live Sheet, run again, show exactly one `AttendanceHistoryEvent` appear.

---

## M2 — Predictor Core

**Scope** (PRD §6.4)
- `packages/predictor-core`: pure functions implementing both predictor cases (safe-skips above threshold, must-attend below threshold, unrecoverable state) exactly per the PRD §6.4 formulas.
- Zero dependency on the API or DB — importable standalone by both `apps/api` and, later, tested directly by `apps/web`.

**Depends on:** M0 (can build in parallel with M1 — no shared dependency).

**Definition of Done**
- Unit tests cover every boundary case named in PRD §17: `T=0`, `P=T`, `threshold=1.0`, `R=0`, the unrecoverable case, and standard mid-range cases — ≥90% coverage per the PRD's coverage gate.
- Package is published/importable within the monorepo (workspace reference), no circular deps.

**Demo:** a short script feeding sample `{P, A, threshold, R}` inputs and printing the exact predictor output strings, matching hand-calculated expected values.

---

## M3 — Scheduler & Admin Sync API

**Scope** (PRD §2.8, §8.7, §8.8, §11.5)
- Wrap M1's sync engine in a scheduled job (`@Cron`, interval from `AdminSettings`).
- `POST /admin/sync/trigger` (rate-limited, PRD §8.7) and `GET /admin/sync/status`, `GET /admin/sync/logs` (PRD §8.7).
- `PUT /admin/config/sheet`, `POST /admin/config/worksheet-mapping`, `PUT /admin/config/sync-interval` (PRD §8.8).
- `POST /admin/students/import` (roster CSV/worksheet import, PRD §8.9).
- RBAC guards enforced server-side per PRD §12.3 matrix — every new route restricted to `ADMIN`/`SUPER_ADMIN`.

**Depends on:** M1.

**Definition of Done**
- All endpoints match the request/response contracts and error codes in PRD §8.7–§8.9 exactly, including the `429 RATE_LIMITED` cooldown behavior.
- Integration tests cover the RBAC matrix: a `STUDENT`-role token gets `403` on every admin route.
- Manual sync trigger correctly respects the configured cooldown (PRD FR-8.7) under concurrent requests.

**Demo:** Postman/curl walkthrough — configure a Sheet ID, add a worksheet mapping, trigger a manual sync, poll `/admin/sync/status` until it flips to `SUCCESS`, pull `/admin/sync/logs` and show the run.

---

## M4 — Auth & Student-Facing Read APIs

**Scope** (PRD §2.1, §8.1–§8.6)
- Full auth flow: college-email login, JWT access+refresh with rotation, Google OAuth login path, password reset (PRD §12.1).
- `GET /students/me`, `GET /attendance/overall`, `GET /attendance/subjects`, `GET /attendance/subjects/:code`, `GET /attendance/history` (PRD §8.2–§8.5), all reading from Postgres only — **never** calling the Sheets API synchronously (PRD FR-2.5).
- Predictor wired into `GET /attendance/subjects/:code` response (`predictor` field) using M2's package.

**Depends on:** M1 (data must exist to query), M2 (predictor), M3 (auth needs `AdminSettings`/roles context, though can build the JWT plumbing in parallel).

**Definition of Done**
- Every endpoint matches PRD §8.2–§8.6 payload shapes and error codes exactly.
- RBAC object-level check proven: a student token can only ever retrieve their own `studentId`-scoped data, verified by an integration test attempting cross-student access.
- Refresh-token rotation and reuse-detection tested (PRD §18.1): presenting an already-used refresh token revokes all sessions for that user.

**Demo:** log in via curl/Postman with a seeded student account, fetch overall + subject attendance, show the predictor field populated with real numbers matching M2's math.

---

## M5 — Student Dashboard UI (Frontend, first real screens)

**Scope** (`ui.md` §1–§4, PRD §2.2–§2.3)
- Global shell: sidebar, top bar, **SyncPulse**, theme system (light default + dark), all shared components from `ui.md` §1 (StatusDot, StatusBadge, PercentageRing, RollCallRow, EmptyState, Toast).
- `/login` screen wired to M4's auth endpoints.
- `/` Dashboard: hero PercentageRing, subject grid, DefaulterBanner — wired to `GET /attendance/overall` and `GET /attendance/subjects`.
- `/subjects/:code`: History/Timeline/Calendar tabs wired to `GET /attendance/subjects/:code` and `GET /attendance/history`; PredictorCallout wired to the API's `predictor` field.
- Protected routing (redirect to `/login` on expired/missing session) per `ui.md` §15.

**Depends on:** M4.

**Definition of Done**
- Every screen in this milestone binds to a live endpoint — **zero mocked data**, per `ui.md` §16.
- Skeleton loaders match final layout shape for each view (`ui.md` §1 Loading & Error Conventions); inline error+retry states verified by simulating a failed fetch.
- Responsive down to mobile per `ui.md` §1 (sidebar → bottom tab bar).
- Accessibility pass: keyboard nav through dashboard → subject page, ARIA labels on the PercentageRing and calendar grid, status never conveyed by color alone (`ui.md` §15).

**Demo:** log in as a seeded student in a browser, land on the dashboard with a real PercentageRing reflecting M1-synced data, click into a subject, see History/Timeline/Calendar all populated, see the predictor's plain-language sentence match M2's math.

---

## M6 — Analytics, History, Notifications UI + APIs

**Scope** (PRD §2.4–§2.7, `ui.md` §5, §7, §8)
- Backend: `GET /notifications`, `PATCH /notifications/:id/read`, `PATCH /notifications/read-all`; notification generation hooked into M1's `NotificationTrigger` (PRD §6.5, §11.3 step 9) — dedup constraint proven under repeated syncs.
- Frontend: `/history` (cross-subject filterable log + CSV export), `/analytics` (trend line + comparison bar chart via Recharts), `/notifications` (bell dropdown + full page).
- Predictor surfaced as its own `/predictor` page (`ui.md` §6), not just docked on the subject page.

**Depends on:** M3 (sync must emit `SyncLog.id` for notification dedup keys), M5 (shell/components already exist).

**Definition of Done**
- Notification dedup proven: re-running a sync with no new changes produces zero duplicate notifications (integration test against the unique constraint from PRD §6.5/§7.2).
- Threshold-breach and defaulter-flag notifications trigger correctly against fixture data crossing the configured threshold.
- CSV export in History produces a file matching the filtered table exactly (spot-checked against the API response).
- Charts render correctly with real multi-week synced data (not single-data-point placeholder state) — test against a fixture spanning at least 4 weeks.

**Demo:** trigger a sync that pushes a seeded student below 75% in one subject, show a new notification appear in the bell dropdown in real time (next poll), open Analytics and see the trend line dip below the threshold reference line, export History to CSV and open it.

---

## M7 — Admin Panel UI

**Scope** (PRD §2.8, `ui.md` §9–§14)
- `/admin/sheet` (connection test + detected-tabs confirmation), `/admin/mapping` (worksheet mapping with live column-preview), `/admin/roster` (CSV import with pre-commit preview), `/admin/sync` (interval config + manual trigger + cooldown countdown), `/admin/logs` (sync log viewer with expandable error detail), `/admin/roles` (Super Admin only).
- Role-aware navigation switch (student ⇄ admin nav) for dual-role users.

**Depends on:** M3 (all backing endpoints), M5 (shared shell/components).

**Definition of Done**
- Every admin screen binds to its M3 endpoint — zero mocked data.
- Worksheet-mapping column-preview step correctly fetches and displays real first-row data from the connected Sheet before an admin commits a mapping.
- Roster import preview correctly distinguishes create/update/error rows against the real DB state (not a client-side guess).
- Sync log expandable rows show the exact skip-reason strings from PRD §11.4, matching M1's `SyncLog`/`errorMessage` content verbatim.
- RBAC enforced in the UI *and* re-verified server-side (a non-admin hitting these routes directly gets redirected/blocked, not just hidden from nav).

**Demo:** as an admin, connect a real test Sheet, map a worksheet with the live column preview, import a small roster CSV, trigger a sync, watch the sync log populate with real run data end to end — the full "cold start" flow a real college admin would follow.

---

## M8 — Hardening

**Scope** (PRD §17–§18)
- Security pass: rate limiting on `/auth/login` and manual sync trigger (PRD §18.5), `helmet` headers, CORS lockdown, input validation whitelist on every DTO (PRD §18.4), refresh-token reuse-detection re-verified end-to-end.
- Load test (PRD §17): 5,000 seeded students querying the dashboard concurrently; sync timing against a 500-row worksheet fixture.
- Observability: structured `pino` logging with redaction (PRD §18.10), `/metrics` Prometheus endpoint, `/health` and `/health/ready` (PRD §14.5).
- Dependency audit gate in CI (`pnpm audit --audit-level=high`), OWASP ZAP baseline scan against staging.
- Full RBAC × endpoint matrix test sweep (every route × every role, PRD §17 Integration — API).
- Cross-browser + accessibility manual QA pass (`ui.md` §15, PRD §17 Manual QA Checklist).

**Depends on:** M6, M7 (needs the full surface area to test against).

**Definition of Done**
- Load test report shows dashboard TTFB and sync duration within PRD §3 NFR targets at the 5,000-student scale.
- `pnpm audit` and ZAP baseline scan both clean or triaged with documented exceptions.
- `/metrics` exposes all four named metrics from PRD §14.5; `/health/ready` correctly fails when the last successful sync exceeds 2× the configured interval.
- No secret, JWT, or credential value appears in logs at any log level (automated redaction test).

**Demo:** show a Grafana/metrics dashboard (or raw `/metrics` output) during a live load test run; show `/health/ready` flip to unhealthy when sync is artificially stalled past threshold, then recover.

---

## M9 — Open-Source Launch

**Scope** (PRD §15 Open Source Deliverables list)
- `README.md` (setup, architecture summary, screenshots), `CONTRIBUTING.md`, `LICENSE` (MIT), issue templates, `docs/architecture.md`, ER diagram export, OpenAPI spec published under `docs/api-docs/`.
- `docs/setup-guide.md` walking a new college admin through: provisioning a Google service account, sharing the Sheet, running `docker compose up`, completing the M7 admin cold-start flow.
- Final `deploy.yml` verified against real Vercel + Railway/Render targets (PRD §14.4), not just CI dry-runs.
- Public repo visibility flipped on.

**Depends on:** M8.

**Definition of Done**
- A person with zero prior context can follow `docs/setup-guide.md` alone, from an empty Google Sheet to a working dashboard, with no undocumented steps.
- OpenAPI spec validated against live responses (contract test from PRD §17 passes in CI).
- Repo passes a final read-through against PRD §15's full open-source checklist item by item.

**Demo:** a cold clone of the public repo, following only `README.md` + `docs/setup-guide.md`, ending in a running local instance synced against a fresh test Google Sheet — recorded as the launch walkthrough.

---

## Cross-Cutting: What Must Never Regress

These hold from the milestone that introduces them through every milestone after, and should be re-checked (not just built once) at every subsequent milestone's CI run:

- **Idempotency** (from M1 onward): re-running sync with no source changes never mutates the DB or emits duplicate notifications.
- **Read-only boundary** (from M1 onward): no code path anywhere in the repo requests a Google API write scope or calls a write method.
- **No synchronous Sheets calls from user-facing requests** (from M4 onward): student/admin dashboard reads only ever hit Postgres.
- **No mocked data in shipped UI** (from M5 onward): every new screen binds to its real backend before merge, per `ui.md` §16.
- **RBAC enforced server-side** (from M3 onward): UI-level hiding is never the only protection on an admin route.