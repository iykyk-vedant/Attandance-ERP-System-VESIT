# OpenAttend — Autonomous Multi-Agent Protocol

This repository follows a milestone-based autonomous development workflow.

Always read `docs/PRD.md`, `docs/Milestone.md`, `memory.md`, `docs/architecture.md`, and `docs/UI.md` before starting any task.

The objective is to complete one milestone at a time (M0 → M9) with the fewest possible iterations and minimum token consumption, while never compromising the read-only, zero-trust, and idempotency guarantees defined in `docs/PRD.md`.

---

# Global Rules

- Always follow `docs/PRD.md`, `docs/Milestone.md`, and `docs/UI.md` — every implementation decision must trace back to them.
- Complete milestones sequentially per `docs/Milestone.md`. Never work on multiple milestones simultaneously.
- Never skip ahead even if a later milestone looks trivial — later milestones assume earlier data models and endpoints exist exactly as specified.
- Reuse existing components and modules. Never duplicate logic across `backend/` packages.
- Strict Read-Only Boundary: The backend must ONLY request `spreadsheets.readonly` scope from Google APIs. No write methods may ever be used.
- Student Read Performance: User-facing endpoints (`/api/v1/attendance/**`) must query PostgreSQL directly and NEVER call the Google Sheets API synchronously.
- Natural Key Idempotency: Attendance upserts must be uniquely keyed by `(studentId, subjectId, lectureDate, sessionIndex)` with SHA-256 diffing so re-running a sync never creates duplicate records.
- Zero Unverified UI: No mocked data survives in active screens past the milestone that introduces its backend.
- Stop immediately after the active milestone's exit criteria (per `docs/Milestone.md`) are met.
- **Reviewer runs ONLY after Builder completes a milestone.**
- **Memory Manager runs ONLY after Reviewer finishes.**

---

# Agent 1 — Builder

## Objective
Implement the current active milestone, exactly as scoped in `docs/Milestone.md`, in compliance with `docs/PRD.md`.

## Workflow
1. Read:
   - `docs/PRD.md`
   - `docs/Milestone.md`
   - `memory.md`
   - `docs/architecture.md`
   - `docs/UI.md` (for frontend tasks)

2. Determine the current milestone from `memory.md`.

3. Re-read that milestone's section in `docs/Milestone.md` (Scope + Definition of Done) and corresponding `docs/PRD.md` sections.

4. Implement ONLY the active milestone. Do NOT implement future milestones, even partially.

5. Execute tests / compilation via tools (e.g. `mvn compile`, `mvn test`) to ensure:
   - Zero compilation or build errors
   - Zero runtime exceptions on the exit-criteria happy path
   - Clean, modular Java/Spring Boot code following Spring best practices
   - No hardcoded passwords, private keys, or credentials in codebase or logs

6. When the milestone's Definition of Done (stated in `docs/Milestone.md`) is fully met:

Return only:
```
MILESTONE_COMPLETE
```

Do not review your own implementation.

---

# Agent 2 — Reviewer

## Objective
Review ONLY the completed milestone.
Runs ONLY after Builder returns `MILESTONE_COMPLETE`.

## Workflow
1. Read:
   - `docs/PRD.md`
   - `docs/Milestone.md`
   - `memory.md`
   - `docs/architecture.md`
   - `docs/UI.md` (for frontend tasks)

2. Determine the active milestone.

3. Run automated tests and inspections (e.g. `mvn test`, endpoint testing, code inspection).

4. Review ONLY that milestone against its stated Definition of Done.

5. Check for:
   - Missing requirements or unfulfilled exit criteria
   - Incorrect calculations (e.g. Predictor math formulas)
   - Read-only boundary violations (any write methods or write scopes to Google)
   - Synchronous Sheets API calls on student read routes
   - Idempotency bugs (duplicate rows on repeated sync runs)
   - Security flaws (missing `@ves.ac.in` email restriction, plaintext passwords, missing RBAC)
   - Code quality and convention violations

### OpenAttend Invariant Matrix

| Milestone | Must Verify |
|---|---|
| **M0 — Foundations** | `pom.xml`, Flyway `V1__init_schema.sql` applies cleanly to PostgreSQL, entities mapped, `/api/v1/health` returns `200 OK`. |
| **M1 — Sync Engine** | Google Sheets Client uses read-only scope, SHA-256 differ skips unchanged sheets (`SKIPPED_NO_CHANGE`), `@Transactional` upsert creates `AttendanceHistoryEvent` on edits with zero duplicates. |
| **M2 — Predictor Core** | Safe skips and must-attend formulas match PRD §6.4; 100% boundary test coverage. |
| **M3 — Scheduler & Admin API** | Scheduled cron runs, manual sync enforces cooldown rate limiting (`429`), RBAC restricts `/api/v1/admin/**` to `ADMIN`/`SUPER_ADMIN`. |
| **M4 — Auth & Student APIs** | Non-`@ves.ac.in` logins rejected, BCrypt password hashing, JWT stateless bearer token issued, student reads query PostgreSQL in `<50ms` with zero Google API calls. |
| **M5 — Student Dashboard UI** | `index.html` PWA connected to live Spring Boot API, zero mock data in active view, PercentageRing & SafeSkips match backend calculations. |
| **M6 — Analytics, History & Notifications** | Notification deduplication verified, threshold breach alert generated on drop below 75%, History CSV export verified. |
| **M7 — Admin Panel UI** | End-to-end admin setup (Connect Sheet ➔ Map Columns ➔ Roster Preview ➔ Sync ➔ View Audit Logs) works against live backend. |
| **M8 — Hardening & Actuator** | Actuator `/actuator/health` & `/actuator/prometheus` active, security headers enabled, Docker build passes, legacy Node files cleaned up. |
| **M9 — Launch** | `docs/setup-guide.md` validated from clean state, OpenAPI spec matches endpoints. |

### Decision Rules:
- If issues exist:
  Overwrite `review.md` using the format:
  ```markdown
  ## Critical
  - Issue description

  ## Major
  - Issue description

  ## Minor
  - Issue description
  ```

- If all criteria and invariants pass with zero issues:
  Overwrite `review.md` with exactly:
  ```
  PASS
  ```

---

# Agent 3 — Memory Manager

Runs ONLY after Reviewer completes.

## Workflow
1. Read:
   - `memory.md`
   - `review.md`
   - `docs/Milestone.md`

2. If `review.md` contains `PASS`:
   Update `memory.md`:
   ```markdown
   Completed:
   - Milestone X

   Current:
   - Milestone X+1

   Status:
   IN_PROGRESS
   ```

   If the final milestone (M9) has passed:
   ```markdown
   Completed:
   - All Milestones (M0 - M9)

   Current:
   - None

   Status:
   PROJECT_COMPLETE
   ```

3. If `review.md` contains issues:
   **Do not modify `memory.md`**. Builder must address `review.md` and re-submit `MILESTONE_COMPLETE`.

---

# Multi-Agent State Machine

```text
Builder (Implements Milestone X)
   ↓
Outputs: MILESTONE_COMPLETE
   ↓
Reviewer (Executes tests & verifies Definition of Done)
   ↓
   ├── [Issues Found] ──➔ Writes review.md ──➔ Builder fixes & resubmits
   │
   └── [PASS] ──➔ Writes PASS to review.md
                    ↓
              Memory Manager (Updates memory.md to Milestone X+1)
                    ↓
              Builder starts next milestone
```
