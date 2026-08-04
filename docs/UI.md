# OpenAttend — UI Build Specification

> Derived from `PRD.md` (v0.1)
> Purpose: paste this file into your IDE/agent as the authoritative prompt for building the OpenAttend frontend.
> Stack: React + Vite + TypeScript + TailwindCSS + shadcn/ui + React Query + Recharts (per PRD §10). No new frameworks.

---

## 0. Design Direction

OpenAttend is a **live mirror of a spreadsheet**, not a generic student-portal dashboard. The UI must feel like the thing it actually is: a Sheet that learned to breathe — a ledger that updates itself, stays honest about when it last checked, and never pretends to know more than it was told. Avoid generic "campus portal" clichés (gradient hero banners, mascot illustrations, cluttered stat-soup, fake real-time confetti).

**Aesthetic pillars:**
- **Legible, not loud.** This is read constantly, often on a phone, often at a glance between classes. Every screen must answer "am I okay?" in under two seconds.
- **Honest about staleness.** OpenAttend is a *synced* copy, not a live feed. The UI should never imply more freshness than the last successful sync — this honesty is a feature, not an apology.
- **One recurring device, used everywhere.** Rather than ten dashboard widgets competing for attention, one visual grammar repeats across every screen so the whole app reads as a single system.

**Signature visual devices (pick and apply consistently):**
1. **The Roll Call Row** — the atomic unit of the entire app. Every lecture, every history entry, every sync-log line renders as a single horizontal row: `date · subject · status-dot · detail`, monospace for the date/numbers, sans for everything else. Stack these rows and you get history tables, timelines, and sync logs — one component, three contexts.
2. **The Percentage Ring** — a single circular progress ring is the app's one "hero" shape, used for Overall % on the dashboard and % on each subject card. It never appears more than once per screen at large size — everywhere else, percentages are flat numbers or thin bars. This restraint is what makes the ring feel earned rather than decorative.
3. **The Sync Pulse** — a small live-updating "last synced Xm ago" indicator, present in the top bar on every screen, that gently pulses (a slow 2s breathing opacity, not a spinner) while a sync is in progress and settles the moment fresh data lands. This is the one piece of "aliveness" the UI is allowed — it stands in for the Google Sheet actually being checked, which is otherwise invisible to the student.

**Theme:** Light-first ("notebook" mode) as default, since this is read by students constantly in bright hallways and lecture halls; a true dark mode is equally polished, not an afterthought.

### Design Tokens

```css
/* Light (default) */
--bg-base:        #FAFAF8;   /* app background — warm off-white, not stark white */
--bg-surface:     #FFFFFF;   /* cards, panels */
--bg-surface-2:   #F2F1EC;   /* nested/hover surfaces, subject card backs */
--border-subtle:  #E7E5DE;
--border-strong:  #D3D0C6;

--text-primary:   #1C1B18;
--text-secondary: #6B6A62;
--text-tertiary:  #A3A199;

--accent:         #2F6F5E;   /* OpenAttend pine — primary brand accent, sparing use */
--accent-dim:     #E4EFEB;   /* accent tint for subtle backgrounds/hover */

--state-safe:     #2F9E6E;   /* ≥ threshold, present, active */
--state-risk:     #D64545;   /* below threshold, absent, revoked/blocked */
--state-warn:     #C98A1E;   /* approaching threshold, pending, stale sync */
--state-neutral:  #8B897F;   /* no lecture / NA / inactive */

--font-sans: "Inter", -apple-system, "Segoe UI", sans-serif;
--font-mono: "IBM Plex Mono", "JetBrains Mono", ui-monospace, monospace;

--radius-sm: 8px;   /* inputs, chips, status dots' containers */
--radius-md: 12px;  /* cards, modals */
--radius-lg: 20px;  /* the Percentage Ring container and page-level hero panels only */
```

```css
/* Dark ("night study" mode) */
--bg-base:        #14161A;
--bg-surface:     #1B1E23;
--bg-surface-2:   #22262C;
--border-subtle:  #2B3038;
--border-strong:  #3A424C;

--text-primary:   #ECEBE6;
--text-secondary: #9B9A92;
--text-tertiary:  #6B6A62;

--accent:         #5FBFA6;
--accent-dim:     #1E2E29;

--state-safe:     #4ADE94;
--state-risk:     #F27272;
--state-warn:     #E0AA46;
--state-neutral:  #6E7178;
```

**Type scale:** 12 / 13 / 14 / 16 / 20 / 28 / 40px. Body copy at 14px. 40px is reserved *exclusively* for the Percentage Ring's center number — nothing else on the entire app reaches that size, so the one moment it appears, it reads as the answer to "how am I doing," not as generic hero-number decoration.

**Elevation:** flat surfaces + 1px borders as the default; a very soft, short shadow only on the Percentage Ring card and on floating elements (modals, popovers, toasts). Attendance status is never communicated through shadow or elevation, only through the fixed state-color set above.

**Iconography:** [Lucide](https://lucide.dev) icons only, 1.5px stroke. No filled icons except status dots. No emoji, no mascots, no illustrated characters anywhere in the product UI (illustrated *empty states* use simple abstract line-art only — see §1).

**Motion:** 120–180ms ease-out for all standard transitions. No bounce, no spring. The two exceptions: (1) the Sync Pulse's slow 2s breathing opacity while syncing, and (2) the Percentage Ring animates its fill from 0 to the current value on first paint only (600ms ease-out), never re-animating on subsequent renders of the same value — it should feel like a needle settling, not a slot machine.

---

## 1. Global Shell

### Layout
- **Left sidebar** (fixed, ~220px, collapsible to icon-only ~64px on tablet/desktop; becomes a bottom tab bar on mobile — see §1 Responsive): wordmark "OpenAttend" (a simple abstract checkmark-in-circle glyph, not a literal calendar/clipboard clipart), nav sections, user identity + logout at the bottom.
- **Top bar** (per page): page title on the left; **Sync Pulse indicator** and notification bell on the right, present on every screen without exception.
- **Main content area:** max-width container, consistent 20–28px padding, 12-column responsive grid.

### Navigation (sidebar groups — student view)
```
  ▸ Dashboard
  ▸ Analytics
  ▸ Predictor
  ▸ History
  ▸ Notifications
```

### Navigation (sidebar groups — admin view, shown when role = ADMIN/SUPER_ADMIN, via a role switcher at the bottom of the sidebar rather than a separate app)
```
SETUP
  ▸ Sheet Connection
  ▸ Worksheet Mapping
  ▸ Roster

OPERATIONS
  ▸ Sync Control
  ▸ Sync Logs

ACCESS
  ▸ User Roles          (SUPER_ADMIN only)
```

Active nav item: left accent bar (2px, `--accent`) + brightened text + `--accent-dim` background tint on the item only (not a full-width pill). No icon-only ambiguity — every nav item keeps its label even in a moderately narrow sidebar; only the fully collapsed icon-rail state hides labels (shown via tooltip on hover).

### Global Components (build once, reuse everywhere)

- **StatusDot** — small filled circle (8px), colored by state token. Variants: `PRESENT` (safe), `ABSENT` (risk), `NA` (neutral), `SYNCING` (warn, pulsing).
- **StatusBadge** — pill, sentence case (not uppercase-tracked — this app is warmer/plainer than a security console), colored dot + label. Variants: `Present`, `Absent`, `No lecture`, `Defaulter`, `Active`, `Paused`, `Success`, `Skipped`, `Failed`.
- **PercentageRing** — SVG circular progress, center shows the % in `--font-mono` at the 40px size, ring color = state token based on threshold (safe ≥75%, warn 65–75%, risk <65%; thresholds pull from admin config, never hardcoded twice). Small size variant (used on subject cards) drops the animation and center-label to 20px.
- **RollCallRow** — the recurring "Roll Call Row" component: `date (mono, dimmed) · subject label (sans, optional — omitted when already scoped to one subject) · StatusDot + label · detail/remarks (sans, secondary, truncated with tooltip)`. Used identically in History tables, Timeline strips (compressed to dot-only), and Admin Sync Logs (date becomes timestamp, status becomes run-status, detail becomes row/error count).
- **SyncPulse** — top-bar indicator: a small dot that breathes (2s opacity cycle) while `SYNCING`, otherwise static, plus text "Synced 4m ago" / "Syncing…" / "Sync failed — retrying" (risk-colored only in the failed case). Always present, always truthful — never shows a fresher time than the last confirmed successful sync.
- **PredictorCallout** — a single-sentence, large-ish (20px) statement card: "You can miss **2** more lectures" or "Attend the next **4** lectures in a row" with the changing number in `--font-mono` and `--accent` color, plus a small collapsible "How is this calculated?" disclosure below it showing the plain-language formula from PRD §6.4.
- **EmptyState** — simple abstract line-art icon (not literal illustration) + one-line message + optional primary action. Never a blank void. Copy is written from the student's side of the screen (e.g., "No attendance synced yet — check back after the next sync," not "No data available").
- **ConfirmDangerDialog** — used for admin actions with irreversible or system-wide effect (delete worksheet mapping, force full re-sync, revoke admin role). Explicit confirm click required; risk-colored accent border.
- **Toast** — bottom-right, auto-dismiss, used for action confirmations ("Mapping saved," "Sync triggered," "Notifications marked read").

### Loading & Error Conventions
- Skeleton loaders (shimmering bars matching the exact shape of the PercentageRing, KPI cards, and RollCallRow list they'll become) — never a spinner-only blank page.
- Failed data fetch → inline error card with a retry button, written in plain language ("Couldn't load your attendance. Try again."), not a full-page crash.
- Every screen listed in §3–§14 must be wired to live data per the PRD's module boundaries (§5.1) — **no mocked data, no lorem ipsum** in the final build.

### Responsive Behavior
- **Desktop (≥1024px):** full sidebar + content, as described above.
- **Tablet (768–1023px):** sidebar collapses to icon rail by default, expandable on hover/tap.
- **Mobile (<768px):** sidebar becomes a fixed bottom tab bar (Dashboard / Analytics / Predictor / History / More) with the Sync Pulse relocating into the top app bar; PercentageRing and KPI rows stack to single-column; admin views remain accessible via "More" but are not optimized for phone-first use (admins are expected to primarily use tablet/desktop).

---

## 2. Authentication (Login)

**Route:** `/login`

- Centered card (max-width ~380px) on the base background, no vignette or atmosphere — OpenAttend's one moment of atmosphere is reserved for the Percentage Ring on the dashboard, not the login screen, keeping this screen quick and utilitarian.
- OpenAttend wordmark/glyph above the form.
- College email field (with the allowed domain shown as inline helper text, e.g., "Use your @ves.ac.in email"), password field (show/hide toggle), and a "Continue with Google" button above the email/password divider (since Google Sign-In is the recommended path per PRD §2.1).
- Generic failure messaging only: "That email or password isn't right" — never reveals which field was wrong.
- Primary button: "Sign in" — full width, accent color.
- "Forgot password?" link below the form, routing to a simple email-based reset flow.
- No sign-up flow — students are provisioned via admin roster import (PRD §2.8), so there is no "create account" link; instead, a small caption: "Don't have access yet? Contact your class admin."

---

## 3. Student Dashboard (`/`)

**Purpose:** answer "am I okay?" in one glance (PRD §2.2).

**Layout:**
1. **Hero card:** large **PercentageRing** (the one large-size use per screen) showing Overall %, with present/absent/total as three small stat labels beneath it, and the **last sync time** repeated here at slightly larger scale than the top-bar Sync Pulse (redundant on purpose — this is the number students check most).
2. **Subject grid** below (3 columns responsive → 2 → 1): each **SubjectCard** = subject name, small PercentageRing (or thin bar variant for very narrow layouts), present/total, tap → Subject Page. Cards below threshold get a risk-colored 2px left border; cards at/above stay neutral-bordered — color is reserved for the number and this one accent, not the whole card.
3. If the student appears on the Defaulters worksheet (PRD §11.2), a single non-dismissible **DefaulterBanner** sits above the hero card — plain-language, calm tone ("Your overall attendance is below the required threshold"), not alarmist styling (state-warn, not state-risk, since it's informational rather than a failure state).

---

## 4. Subject Page (`/subjects/:code`)

**Purpose:** full detail for one subject (PRD §2.3).

**Layout:**
- Header: subject name, medium PercentageRing (smaller than dashboard hero), present/absent/total stat row.
- Tabs: **History** | **Timeline** | **Calendar**.
  - **History:** a stack of **RollCallRow**s (date · status · faculty · remarks), sortable by date, paginated.
  - **Timeline:** the same rows compressed to a horizontal scrollable strip of **StatusDot**s only, most recent right-aligned, with a hover tooltip showing the full row detail.
  - **Calendar:** month grid, each day cell tinted by state token (safe/risk/neutral), click → popover showing that day's RollCallRow detail.
- **PredictorCallout** docked at the bottom of the page (scoped to this subject), matching §6's predictor logic exactly.

---

## 5. History (`/history`)

**Purpose:** cross-subject attendance log (PRD §2.4).

**Layout:**
- Filter bar: subject select, status select, date range picker, free-text search (matches remarks/faculty).
- A single long stack of **RollCallRow**s (subject label now shown, since this view spans subjects), grouped by month with a sticky month-header divider — this is the same component as the Subject Page's History tab, just unscoped.
- **Export CSV** button, top-right.
- Empty state (no records matching filters): "No records match these filters — try widening the date range."

---

## 6. Predictor (`/predictor`)

**Purpose:** standalone eligibility calculator surfaced as its own page, in addition to being docked on each Subject Page (PRD §2.6).

**Layout:**
- Subject selector at top (defaults to "Overall").
- Large **PredictorCallout** as the page's centerpiece — the single largest piece of content on the page, matching the dashboard's restraint principle (one hero element per screen).
- Below it, a small worked-example panel showing the exact numbers plugged into the PRD §6.4 formula (present / total / threshold / remaining lectures), in `--font-mono`, so the calculation is fully transparent and never feels like a black box.
- If the "not mathematically recoverable" case applies (PRD §6.4 Case B), the callout switches to state-risk color and plain language: "You won't be able to reach 75% this term with the lectures remaining" — stated once, calmly, without repeated warnings elsewhere on the page.

---

## 7. Analytics (`/analytics`)

**Purpose:** trend and comparison view (PRD §2.5).

**Layout:**
- Toggle: Weekly / Monthly aggregation.
- Line chart (Recharts): overall % trend over time, with a horizontal reference line at the configured threshold (75% default), plotted in `--accent`.
- Bar chart: subject comparison, sorted ascending by % (risk-first, so the subject needing attention is always leftmost/topmost).
- Small **trend chip** per subject: ▲ or ▼ with the percentage-point delta vs. the previous period, colored safe/risk accordingly, neutral gray if flat.

---

## 8. Notifications (`/notifications` + bell dropdown)

**Purpose:** in-app notification center (PRD §2.7).

**Layout:**
- Bell icon in the top bar opens a slide-over panel; a dedicated `/notifications` route shows the same content full-page for mobile/deep-linking.
- List grouped by date, each item: small icon (threshold breach / attendance update / defaulter flag — three distinct Lucide icons, not a single alert-triangle-for-everything), message in plain language, relative timestamp, unread indicator (dot, not bold text — keeps the list calm).
- "Mark all read" action at the top of the list.
- Empty state: "You're all caught up."

---

## 9. Admin — Sheet Connection (`/admin/sheet`)

**Purpose:** connect and verify the Google Sheet (PRD §2.8, §8.9).

**Layout:**
- Single form: Sheet ID input, service-account email shown read-only (generated/configured at deploy time), **"Test Connection"** button.
- On success: a calm confirmation state listing detected tab names as chips ("Student List," "OS," "AI," "Defaulters," …).
- On failure: inline error card explaining the specific failure (`SHEET_ACCESS_DENIED` → "This service account doesn't have access to that sheet yet — share it as a Viewer and try again"), never a raw error code alone.

---

## 10. Admin — Worksheet Mapping (`/admin/mapping`)

**Purpose:** map worksheet tabs to subjects and columns (PRD §2.8, §11.2).

**Layout:**
- Table of existing mappings: subject code/name · worksheet tab name · range · "Edit" / "Remove" (Remove behind ConfirmDangerDialog, since it affects sync history interpretation).
- **"+ Add Mapping"** opens a form: subject select/create, worksheet dropdown (populated from the verified connection in §9), range input, and a **column-role assignment** step that shows a live preview of the first few rows of that worksheet with dropdowns above each column ("This is the: Date / Roll No. / Status / Faculty / Remarks / — ignore —") rather than asking the admin to type raw column letters blind.
- Validation errors (unmapped required role, malformed range) surface inline, next to the specific field, in plain language.

---

## 11. Admin — Roster (`/admin/roster`)

**Purpose:** student roster import/management (PRD §2.8, §8.9).

**Layout:**
- CSV upload dropzone with drag-and-drop, plus "or import from Student List worksheet" alternate action.
- Before committing: an inline preview table showing rows to be created (safe-colored) vs. updated (warn-colored) vs. flagged as errors (risk-colored, e.g., duplicate roll numbers), with counts summarized above the table ("240 to import, 12 to update, 0 errors").
- Existing roster table below, searchable by name/roll number/division, each row editable inline.

---

## 12. Admin — Sync Control (`/admin/sync`)

**Purpose:** configure and trigger sync (PRD §2.8, §11.5).

**Layout:**
- Interval selector: preset dropdown (5 min / 15 min / 1 hr / manual-only) plus a custom cron input for advanced users, with the next-scheduled-run time shown in plain language beneath it ("Next sync at 10:15 AM").
- Advanced settings (collapsed by default): max retries, backoff base, manual-sync cooldown — each with the current default shown as placeholder text.
- Live status cards, one per mapped worksheet: worksheet name, current state (Idle/Running/Success/Failed as **StatusBadge**), last-run timestamp, **"Sync Now"** button (disabled with a visible countdown during cooldown, per PRD FR-8.7 — never silently disabled without explanation).

---

## 13. Admin — Sync Logs (`/admin/logs`)

**Purpose:** sync run history for debugging (PRD §2.8, §11.4).

**Layout:**
- Filter bar: worksheet select, status select, date range.
- A dense stack of **RollCallRow**s in their sync-log configuration: timestamp (mono) · worksheet name · run status badge · rows read/upserted (mono) · duration (mono, secondary).
- Expandable row → shows `errorMessage` and any per-row skip reasons (`UNKNOWN_STUDENT`, `MALFORMED_DATE`, etc.) listed in plain language with counts, matching PRD §11.4's failure-mode table exactly so admins can self-diagnose without reading source code.
- Empty state: "No sync runs yet — trigger one from Sync Control."

---

## 14. Admin — User Roles (`/admin/roles`) — SUPER_ADMIN only

**Purpose:** promote/demote admin roles (PRD §2.8 FR-8.8, §12.3).

**Layout:**
- Searchable user table: name, email, current role (**StatusBadge**-style role chip), "Change role" action opening a small select (Student/Admin/Super Admin) behind a ConfirmDangerDialog when demoting a Super Admin or promoting to Super Admin (lateral Student↔Admin changes for non-self accounts can save inline without the dialog, since they're lower-stakes and reversible).

---

## 15. Feature List (what must be visibly present in the UI)

### Authentication & Access
- [ ] College-email login (password + Google Sign-In), generic failure messaging
- [ ] Protected routing (redirect to `/login` when unauthenticated/expired session)
- [ ] Role-aware navigation (student nav vs. admin nav, switchable for dual-role users)
- [ ] Password reset flow

### Student Dashboard & Detail Views
- [ ] Overall % via PercentageRing, present/absent/total, last-sync time (dashboard + subject page + top bar)
- [ ] Subject grid with per-subject PercentageRing/bar and threshold-based left-border coloring
- [ ] Defaulter banner when applicable, calm/plain-language tone
- [ ] Subject page: History / Timeline / Calendar tabs, all built from the shared RollCallRow component
- [ ] Cross-subject History page with filters and CSV export

### Analytics & Predictor
- [ ] Weekly/monthly trend line chart with threshold reference line
- [ ] Subject comparison bar chart, risk-first sorted
- [ ] Trend delta chips per subject
- [ ] Predictor: safe-skips / must-attend / not-recoverable states, with the formula disclosed in plain language
- [ ] Predictor available both docked (subject page) and as its own page

### Notifications
- [ ] Bell dropdown + full notifications page
- [ ] Three distinct notification types with distinct icons (update / threshold breach / defaulter flag)
- [ ] Mark-as-read (single + all)

### Admin — Setup & Sync
- [ ] Sheet connection test with detected-tabs confirmation and plain-language error states
- [ ] Worksheet-mapping UI with live column-preview (not blind column-letter entry)
- [ ] Roster CSV import with pre-commit create/update/error preview
- [ ] Sync interval configuration (presets + custom cron) with next-run-time display
- [ ] Manual "Sync Now" with visible cooldown countdown
- [ ] Sync logs with expandable per-run error/skip-reason detail
- [ ] User role management (Super Admin only), with confirm dialogs on privilege changes

### System-wide
- [ ] Sync Pulse indicator present on every screen without exception, never overstating freshness
- [ ] Light theme (default) + dark "night study" theme
- [ ] Skeleton loading states, inline error+retry states, and empty states on every data view
- [ ] Fully responsive down to mobile, with sidebar becoming a bottom tab bar
- [ ] Accessible: keyboard-navigable, ARIA-labeled charts/tables, status never conveyed by color alone (StatusDot + label pairing always)

---

## 16. Explicit Non-Goals for the UI

- No UI path may imply the dashboard is "live" beyond the last confirmed successful sync — the Sync Pulse's stated time must always be truthful, never optimistic.
- No mock/placeholder data anywhere once a corresponding backend endpoint exists — every view above binds to the live API per PRD §8.
- No write-back affordance of any kind toward the Google Sheet — no "edit in Sheet," no inline attendance-marking control, anywhere in the product, reinforcing PRD NG2 at the UI layer.
- No decorative illustrations, stock photography, mascots, or emoji — icons only, from Lucide; empty-state art is abstract line-art at most.
- No dense, security-console-style monochrome-plus-one-accent treatment applied uniformly — OpenAttend is read by stressed students checking a number, so warmth and legibility take priority over the clinical tone appropriate to a firewall-type product.