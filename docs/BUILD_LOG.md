# Build log

A running record of what each phase delivered and the decisions made along the way.

## Phase 0: Scaffold

**Environment (checked 2026-10-21):** Java 21.0.9 (Temurin), Node 26.12.0, npm 11.20.0, git 2.52.0,
MongoDB 9.0.4 running locally via Homebrew on `mongodb://localhost:27017`. Docker is not running on the
development machine, so `docker-compose.yml` is provided but was not started, and Testcontainers is not used.

**Delivered**
- Monorepo layout: `backend/` (Spring Boot), `frontend/` (React), `docs/`.
- Backend generated from start.spring.io: Spring Boot 4.1.1, Java 21, Maven Wrapper (Maven 3.10.0).
  Dependencies: WebMVC, Data MongoDB, Security, OAuth2 Resource Server (JWT), Validation, Actuator,
  Thymeleaf, OpenHTMLtoPDF 1.1.97, JFreeChart 1.5.6, springdoc-openapi 3.1.1, Lombok 1.18.48.
- Frontend: Vite 8, React 19, TypeScript 6, Tailwind CSS 4, shadcn/ui (Radix base), TanStack Query 5,
  React Router 8, Framer Motion 14, Recharts 3, React Hook Form 7 + Zod 4, sonner, canvas-confetti.
- `docker-compose.yml` with `mongo:8` and `mongo-express`, both bound to 127.0.0.1.
- Health endpoint at `GET /api/health` (Actuator, health only). The Vite dev server proxies `/api` to `:8080`.

**Decisions**
- Spring Boot 4 renamed the connection property to `spring.mongodb.uri` (the spec shows the Boot 3 name
  `spring.data.mongodb.uri`). `spring.data.mongodb.auto-index-creation` keeps its name.
- `.env` loading uses Spring's own `spring.config.import: optional:file:../.env[.properties]`, so no extra
  library or shell tricks are needed and it behaves the same on macOS, Linux and Windows.
- `demo` is a Spring profile *group* that also activates `dev`.
- Actuator's base path is `/api`, so health is `/api/health` and goes through the same dev proxy.
- Integration tests that need a real MongoDB are tagged `mongo-integration` and run with `./mvnw -Pintegration test`.

## Phase 1: Auth

**Delivered**
- `users` collection (unique lowercase email, BCrypt hash, avatar colour from a fixed palette, audited
  `createdAt`/`updatedAt`, `lastLoginAt`).
- `POST /api/auth/register|login|logout`, `GET /api/auth/me`, `GET /api/auth/csrf`, `PATCH /api/users/me`,
  `PUT /api/users/me/password`, `GET /api/users/avatar-colors`.
- JWT (HS256) with Spring Security's built-in `NimbusJwtEncoder`/`NimbusJwtDecoder`, carried in an httpOnly
  `SM_TOKEN` cookie (SameSite=Lax, Secure in prod, 7 days). `CookieBearerTokenResolver` also accepts
  `Authorization: Bearer` for curl/Postman. The cookie is ignored on public endpoints and cleared on a 401,
  so a stale cookie never breaks login.
- CSRF on, cookie-to-header (`csrf.spa()` in Spring Security 7). Requests authenticated by an explicit
  Authorization header skip CSRF (a browser cannot forge that header).
- `JWT_SECRET` must be >= 32 bytes; startup fails without it unless the dev/demo profile is active (then a
  random secret is generated with a loud warning).
- Global `@RestControllerAdvice` + JSON security handlers: every error has
  `{timestamp, status, error, message, path, fieldErrors}`.
- Login rate limit: 10 failed attempts per (IP, email) in a sliding 5-minute window → 429 with Retry-After.
  Unknown emails take the same BCrypt time as known ones.
- Frontend: theme system (midnight/light/system, stored, no flash on load), typed API client with CSRF and
  401 handling, landing page, split-screen login/register with animated brand panel and password strength
  meter, `next=` return-path support, route guards, app shell, profile page, 404 and error boundary.

**Tests:** `MoneyTest` (28 cases incl. European formats), `LoginRateLimiterTest`, `AuthServiceTest` (Mockito),
`AuthFlowIntegrationTest` (MockMvc against local MongoDB, tagged `mongo-integration`).

**Decisions**
- Port 8080 is taken by Jenkins on the development machine, so the local `.env` sets `PORT=8090` and
  `BACKEND_URL=http://localhost:8090` (Vite reads the root `.env`). Defaults stay at 8080 as specified.
- `_class` type hints are switched off in the Mongo converter (no polymorphic documents) to keep the demo
  data clean.
- `LocalDate` is stored with the driver's UTC codec so date aggregations land on the right day.
- `Money.parse` treats a lone separator followed by exactly three digits as thousands grouping
  (`1.234` = 1234, as written in Germany); the same rule is mirrored in `frontend/src/lib/money.ts`.

## Phase 2: Splits and invites

**Delivered**
- `splits` collection with embedded `members` (name/email/colour copied in, `active` flag kept for people who
  leave, because old expenses still point at them). Indexes: `inviteCode` unique, `members.userId`, `ownerId`.
- Endpoints: `GET/POST /api/splits`, `GET/PATCH/DELETE /api/splits/{id}`,
  `DELETE /api/splits/{id}/members/{userId}`, `POST /api/splits/{id}/invite/regenerate`,
  `PATCH /api/splits/{id}/invite`, public `GET /api/invites/{code}`, `POST /api/invites/{code}/join`,
  `GET /api/splits/{id}/activity`, `GET /api/activity` (dashboard feed).
- Invite codes: 12 chars base62 from `SecureRandom`; collisions retried against the unique index.
- Join is atomic and idempotent: `$push` guarded by `members.userId: {$ne: me}`; a former member is
  reactivated with a positional `members.$` update. No transactions needed.
- Non-members get 404 for any split URL or id (including malformed ids); owner-only actions are 403.
- Removing/leaving is blocked (409 with a clear reason) while the person is on a live expense or has a
  non-zero balance. The owner cannot leave (archive instead).
- DELETE removes an empty split (and, through `SplitDeletedEvent`, its activity/expenses/payments); a split
  with expenses is archived instead.
- Profile changes propagate into every split with a filtered positional update (`members.$[m]`).
- Dashboard figures (total spend, my net) come from `$facet`/`$group` aggregations (`BalanceQueries`).
- `settlementVersion` on the split is incremented with `findAndModify` + `$inc` (used by Phase 4).
- Frontend: dashboard (greeting, owed/owe summary strip, split cards with avatar stack, balance chip and
  stale dot, "New split" card, recent activity), create-split dialog with emoji/month/currency pickers and a
  share screen (copy with checkmark, Web Share, WhatsApp, QR), public join page with return-path login,
  split page header with owner menu (edit, new link, link on/off, archive/reopen, delete), members sheet with
  remove/leave, activity tab.

**Decisions**
- Extra activity types beyond the spec's list, so every action is visible in the timeline: `SPLIT_UPDATED`,
  `SPLIT_ARCHIVED`, `SPLIT_REOPENED`, `INVITE_ENABLED`, `INVITE_DISABLED`, `PAYMENT_DELETED`.
- Joining is only possible while a split is OPEN; existing members can always "join" again (no-op).
- On mobile every dialog is a bottom sheet (vaul); on desktop a centred dialog or right drawer.

## Phase 3: Categories and expenses

**Delivered**
- `categories`: 12 defaults (Rent, Groceries, Utilities, Dining Out, Transport, Household, Entertainment,
  Travel, Shopping, Health, Subscriptions, Other) seeded idempotently at startup by key; unique index on
  `{splitId, key}`. `GET /api/categories?splitId=`.
- `expenses` with indexes `{splitId, date:-1}`, `{splitId, deleted}`, `{splitId, paidBy}`.
- `GET/POST /api/splits/{id}/expenses`, `GET/PUT/DELETE /api/splits/{id}/expenses/{expenseId}`.
  Filters: category, paidBy, participant, from, to, q (case-insensitive, regex-escaped), sort, page/size; the
  response carries the total amount of everything matching the filters (aggregation).
- Shares are computed on the server by the pure `EqualSplit` engine class (remainder cents to the first
  participants by id; sum asserted) and stored on the expense.
- Validation: amount via `Money.parse` (> 0, <= 2 decimals, <= €100k), description 1–80, payer and every
  participant must be active members, date at most one day in the future, split must be OPEN.
- Permissions: creator, payer or split owner can edit/delete. Delete is soft (`deleted: true`).
- Each change sets `lastExpenseChangeAt` and logs EXPENSE_ADDED/UPDATED/DELETED with the amount.
- Frontend: Expenses tab (search with `/`, payer/member/sort filters, category chips, totals for the filter,
  receipt-style list grouped by day with day totals, rows that expand in place to show who shares it),
  add/edit drawer (right drawer on desktop, bottom sheet on mobile) with a large amount field that accepts
  "86,40" and "86.40", category grid, date, payer picker, split-with toggles plus "Everyone" / "Only me + …",
  and a live per-person preview computed exactly like the server. Optimistic add/delete with rollback.
  `N` opens the drawer, `Enter` saves. Floating action bar (desktop) / "+" button (mobile).

**Tests:** `ExpenseFlowIntegrationTest` (exact stored shares, payer outside participants, permission rules,
soft delete, filters, the spec's worked example as live balances, closed split rejects writes).

**Decisions**
- "Today" for the future-date rule is evaluated in Europe/Berlin, where the target users live.
- Text search uses an escaped case-insensitive regex instead of a `$text` index so partial words match
  ("groc" finds "Groceries").

## Phase 4: Calculation engine and settlement

**Delivered**
- Pure engine in `settlement/engine` (no Spring or MongoDB types): `EqualSplit`, `SettlementCalculator`,
  records `LedgerExpense`, `LedgerPayment`, `MemberBalance`, `Transfer`, `SettlementResult`.
  Σ net = 0 and "shares sum to the amount" are asserted and throw if violated. Greedy settlement with
  priority queues (largest debtor ↔ largest creditor), ties by user id, at most n − 1 transfers.
- All eight required tests from §6.5 pass (`SettlementCalculatorTest`), including the 1,000-group randomised
  property test (Σ net = 0, transfers settle every balance to exactly 0, ≤ n − 1 transfers, deterministic),
  plus tie-breaking, invalid shares and edge cases.
- `settlements` snapshots (`{splitId, version:-1}` unique). The version comes from `findAndModify` + `$inc` on
  the split. `calculatedAt` is captured before the data is read, so any change after it marks the
  snapshot stale. Endpoints: `POST /calculate`, `GET /settlements/latest` (204 if none),
  `GET /settlements`, `GET /settlements/{id}`, `POST /settle`.
- `payments`: `POST/GET /payments`, `DELETE /payments/{id}` (recorder or owner). Payer, payee or owner may
  record. Paying more than the latest calculation says is owed is allowed but returns a clear `warning`.
- Stale detection: `calculationStale` is true when expenses or payments changed after `lastCalculatedAt`.
- Mark as settled requires an up-to-date calculation and every live balance to be exactly zero.
- Frontend: stale banner with Recalculate, Balances tab (personal "You owe Isha €23.40" line, diverging
  balance bars, graph/list toggle for who-pays-whom with "Record payment" per transfer, per-member cards
  with Paid / Their share / Net, payments list with delete, calculation history), the ~1.2s "crunching
  numbers" reveal with staggered results, record-payment dialog with quick notes, "Mark split as settled"
  with confetti. `C` calculates.

**Spot check (written by hand, matches the app):** the spec's worked example (A pays €90 for A, B, C; B pays
€40 for B, C) gives A paid 90.00 / share 30.00 / net +60.00, B paid 40.00 / share 50.00 / net −10.00,
C paid 0 / share 50.00 / net −50.00; transfers C→A €50.00, B→A €10.00. Verified via the API in
`SettlementFlowIntegrationTest` and in the UI.

**Decisions**
- Removed `spring.jackson.default-property-inclusion: non_null`: explicit `null`s keep the frontend types
  honest (`lastCalculatedAt === null`, `warning: null`).
- The balance chart follows the dataviz guidance: bars ≤ 24px with a rounded data end and a square
  baseline, neutral zero line, value labels in text colours with +/− (never colour alone), hover tooltip,
  and the member cards act as the table view.

## Phase 5: Reports (IN PROGRESS, stopped here)

Phases 0–4 are complete, verified and committed. Phase 5 was interrupted part-way. This commit holds the
backend pieces written so far. They compile and the 68 existing tests still pass, but **nothing is wired up
yet, so there is no report endpoint, PDF or UI.**

**Done (in `backend/src/main/java/com/splitmate/report/`)**
- `Report` document (`reports` collection, index `{splitId, generatedAt:-1}`), `ReportStats`, `ReportRepository`,
  and the request/response/summary DTOs.
- `ReportAggregations`: one `$facet` pipeline over the split's expenses via `MongoTemplate` that produces totals,
  by-category (`$group` + `$lookup` + `$unwind`), daily trend (`$dateToString`), weekday pattern (`$dayOfWeek`),
  top 5 (`$sort` + `$limit` + `$lookup`), paid and share per member (`$unwind` + `$group`) and the
  member × category matrix. Averages use integer half-up division (`divideRounded`), no doubles near money.
  **Not yet run against MongoDB**, so the pipeline may need fixes on first execution.
- `InsightGenerator`: rule-based insights (top category share, highest-spend day, biggest expense, paid most vs
  consumed most, average per person per day, weekend vs weekday, change vs previous split). Not yet unit tested.

**Still to do for Phase 5**
1. `ReportService` + `ReportController`: `POST /api/splits/{id}/reports`, `GET /api/splits/{id}/reports`,
   `GET /api/reports/{id}`, `GET /api/reports/{id}/pdf`, `GET /api/splits/{id}/export.csv`. Resolve the period
   (request → split period → first/last expense date), find a previous split with the same members for the
   comparison insight, require a calculation first, log `REPORT_GENERATED`, delete reports on `SplitDeletedEvent`.
2. PDF: Thymeleaf `templates/report.html` → OpenHTMLtoPDF (A4, page numbers in the footer), JFreeChart PNGs for
   the category donut and daily trend, stored in GridFS with `{reportId, splitId}` metadata, streamed back with
   `Content-Disposition: attachment; filename="SplitMate-<split>-<yyyy-MM>.pdf"`. Fonts are not bundled; a
   download of Inter and JetBrains Mono into the scratchpad was started but not used.
3. Tests: aggregation integration test against the worked example, `InsightGenerator` unit tests, PDF smoke test.
4. Frontend: Report tab, "Generate report" on the calculation results, report view (KPI cards, category donut,
   daily area chart, weekday bars, paid-vs-share grouped bars, member × category heat-map, top expenses,
   insight cards, Download PDF and Export CSV buttons).

**Then:** Phase 6 (UI polish), Phase 7 (demo seeder, docs, single-jar `-Pprod` build) and Phase 8 (acceptance
checklist), as specified in SPEC.md §12.

**Environment notes for whoever resumes**
- Port 8080 is used by Jenkins on this machine. The local, uncommitted `.env` sets `PORT=8090` and
  `BACKEND_URL=http://localhost:8090`.
- `~/.npm` has root-owned cache files. Use `npm_config_cache=<some dir>` for `npx`/`npm install` or run
  `sudo chown -R 501:20 ~/.npm`.
- Run `./mvnw -Pintegration test` for the full suite (needs local MongoDB; uses the `splitmate_test` database).

## Re-plan (after Phase 4)

New order requested: demo seeder, then finish reports (aggregation check, endpoints, simple PDF, CSV), then the
report UI, then polish of the demo screens only (dark theme), then `docs/mongodb-queries.md` and
`docs/demo-script.md`. Skipped for now: further work on login rate limiting, keyboard shortcuts, QR code, light
theme, the prod single-jar build, Swagger and everything in §13 (the first three exist already; they are just
not being worked on).

### Step 1: Demo data seeder (done)

- `seed/DemoDataSeeder` runs only with `--spring.profiles.active=demo` (`app.seed.enabled`), is idempotent (skips
  if `aarav@demo.com` exists) and goes through the real services, so documents, activity logs and balances are
  exactly what the app would create.
- Users: Aarav Mehta, Isha Rao, Rohan Nair, Sneha Iyer (`@demo.com`, password `Demo@1234`).
- **September month split** (🏠, EUR, 2026-09-01 to 2026-09-30): 13 expenses, calculated, every transfer paid,
  calculated again (all zero), marked **SETTLED**.
- **October month split** (🏠, EUR, 2026-10-01 to 2026-10-31, "Flat 4B, Berlin"): 30 expenses (rent, utilities,
  internet, Lidl/Aldi/Rewe/Edeka groceries by different people, a dinner for 3, Deutschlandticket for 2, a Prague
  weekend for 3, household supplies, Netflix for 2), one calculation (v1) and one recorded payment, so the
  split is stale and the Recalculate banner shows straight away. Total spend €3,057.06; v1: Sneha pays Aarav
  €564.83, Rohan pays Aarav €394.88, Isha pays Aarav €321.08 (nets +1,280.79 / −321.08 / −394.88 / −564.83,
  sum 0). Sneha's €564.83 payment is already recorded.
- Expense dates and the opening timeline entries are backdated; for September the calculations, payments and the
  settle entry are shifted to 2026-09-30 while keeping their order.
- A demo report for each split is generated by `DemoReports` once the report module exists (step 3).
- Defaults changed to match this machine: backend `PORT` and the Vite proxy default to **8090** (Jenkins uses
  8080), in `application.yml`, `vite.config.ts`, `.env.example` and the README.
- The dev database held only my own test users and data, so it was dropped before seeding.

Start commands (verified): backend `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`,
frontend `cd frontend && npm install && npm run dev`.

### Step 2: Report aggregation checked against the seeded data (done)

- `ReportAggregationsIntegrationTest` seeds the demo data into the `splitmate_test` database and runs the `$facet`
  pipeline for the October split. The pipeline worked as written (no fixes needed to the pipeline itself); every
  breakdown (by category, by member, daily trend, weekday pattern, top 5, member × category matrix) is compared
  with an independent Java computation over the same expenses. Also checks the period filter, the zero-filled
  daily trend, the first/last expense date helper and that the seeder is idempotent.
- Numbers for October (hand-checkable): total €3,057.06 over 30 expenses; rent is 52.3% (€1,600.00); average per
  expense €101.90, per day €98.61 (31 days), per person €764.27. All use integer half-up division.
- `InsightGeneratorTest` (12 tests): each rule with its exact sentence, the edge cases (no expenses, one category,
  no weekend in the period, same person paid and consumed most, up/down/same vs the previous split, no previous
  split), the 4 to 7 insight cap and that the text really comes from the data.
- A test of my own had wrong arithmetic (305,706 / 31 = 9,861.48, not 9,862); the code was right.

### Step 3: Report endpoints, PDF and CSV (done)

- Endpoints: `POST /api/splits/{id}/reports` (optional `{periodStart, periodEnd}`), `GET /api/splits/{id}/reports`,
  `GET /api/reports/{id}`, `GET /api/reports/{id}/pdf`, `GET /api/splits/{id}/export.csv`.
- Rules: needs a calculation (409 otherwise) and at least one expense in the period (409). The period defaults to the
  split's period, else the first and last expense date; start must not be after end, at most one year. Non-members get 404
  on everything (including a report id and the PDF).
- "Compared with last month" insight: the newest earlier split of the caller with exactly the same active members whose
  period ended before this one started (October finds September: spending up 35%).
- PDF: Thymeleaf template `templates/report.html` rendered with OpenHTMLtoPDF, A4, page numbers in the footer, tables and
  KPIs only (no charts yet): cover header, KPI row, spending by category with bars, per-person Paid / Their share / Net,
  who pays whom (from the latest calculation, with a note when it is stale), top 5 expenses, insights, full ledger. Stored in
  GridFS (`fs.files` / `fs.chunks`, metadata `{reportId, splitId}`) and streamed back with
  `Content-Disposition: attachment; filename="SplitMate-October-month-split-2026-10.pdf"`. If rendering fails the
  report document is removed again, so a report never exists without its PDF.
- Built-in PDF fonts only, so text is reduced to what they can draw: emoji are dropped, the typographic minus becomes a
  hyphen, the euro sign works. (A bundled TTF for nicer typography was started but the download stalled; not needed.)
- CSV: UTF-8 with BOM for Excel, RFC 4180 quoting, cells starting with `= + - @` get a leading apostrophe so a
  spreadsheet cannot run them as formulas.
- The demo seeder now also generates both reports (`DemoReports` → `ReportService`).
- Tests: `ReportFlowIntegrationTest` (409s, stats, GridFS file linked to the report, PDF text checked with PDFBox including
  the worked-example numbers, permissions, CSV quoting and formula safety). Suite: 86 tests, 0 failures.
- Verified by eye: rendered the October PDF with PyMuPDF (3 pages); figures match the app.

### Step 4: Report UI (done)

- Split page has a **Report** tab (list of generated reports with period, total, top insight; "Generate report";
  prompts to calculate first when there is no calculation). The calculation results screen has **Generate spending
  report**, which creates the report and opens it.
- Report view at `/app/splits/:splitId/reports/:reportId`: hero total, KPI cards (expenses, average per day, per person,
  biggest expense), insights as callout cards, category donut + legend table, paid vs their share grouped columns,
  daily trend area chart (peak labelled), weekday columns, top 5 expenses, member × category heat-map, and
  **Download PDF** / **Export CSV** buttons (real file downloads with the server's file names).
- Charts follow the dataviz skill. I ran its validator: the app's own category colours **fail** for a donut (rose vs orange,
  normal-vision ΔE 12.5 < 15), so the report charts use the skill's validated palette (`--viz-1..5`, dark and light steps in
  `index.css`): single-series charts use slot 1, the donut uses slots 1-5 by rank and folds the rest into a neutral
  "Other", the two-series chart uses slots 1 and 2 with a legend. Bars are ≤ 24px with a 4px rounded data end, lines 2px,
  area fill 10%, hairline grid, a 2px surface gap between donut slices, direct label on the peak/maximum only, hover tooltips,
  text in text tokens (never the series colour), an `aria-label` description per chart, and a **Table** toggle on each chart.
  The heat-map uses a one-hue blue ramp whose lightness rises with value.
- Checked in the browser: tab, generate, PDF and CSV downloads, and the calculation-results shortcut, no console errors.
  (A full-page screenshot caught the line chart mid-animation once; an element screenshot shows it correctly.)
- Note: my testing added extra reports and a recalculation to the demo database; reset it with `db.dropDatabase()` and a
  backend restart to get the pristine seeded state.

### Step 5: UI polish on the demo screens, dark theme only (done)

Reviewed dashboard, split detail (expenses and balances), add expense, calculation results and the report on the seeded
data at 1440px and on a phone, with screenshots. Light theme was not touched.

- **Horizontal overflow on phones fixed.** Grids without a column template size themselves to the min-content of their
  widest child, so one chart or card pushed the page 6 to 31px past the viewport (dashboard, balances, report). Single-column
  grids now use `grid-cols-1` and grid items `min-w-0`. A script now measures `scrollWidth - innerWidth` on every screen:
  **0 at both 390px and 360px** on dashboard, split, balances, report tab, report and profile.
- Avatar stacks overlap less, so initials are no longer clipped.
- Dashboard recent activity shows 8 items instead of 12, so the page is not twice as tall as the split cards.
- Expense names wrap to two lines on phones instead of being cut off at "Dinner at Lokál Pra…".
- Daily trend chart: x-axis labels no longer collide on narrow screens (`preserveStartEnd`, minimum tick gap).
- Calculation results: the settlement graph is capped at 340px so the transfer list is visible next to it on desktop.
- Left as is on purpose: toast position, the floating action bar and the full-page screenshots of fixed overlays (those
  looked odd only because a full-page capture re-lays out fixed elements).

### Step 6: docs/mongodb-queries.md and docs/demo-script.md (done)

- `docs/mongodb-queries.md`: 23 copy-paste `mongosh` blocks, each with a one-line "what it proves": collections and counts, a user
  without the password (and only the start of the BCrypt hash), a user's splits via `members.userId` (with an `IXSCAN` check),
  expenses newest first, one expense's exact shares, spend by category (`$group` + `$lookup`), paid per person, share per person
  (`$unwind`), the latest calculation snapshot, Σ net = 0, calculation history, payments, the activity timeline, invite codes and
  their unique index, soft delete, reports and the GridFS PDF (`fs.files` / `fs.chunks`), all indexes, and a from-scratch
  recomputation of every balance. **All 23 blocks were executed against the seeded database** (a script extracts every block and
  runs it through `mongosh`; 0 failures).
- Running them was worth it: two queries ran without error but gave **wrong answers** because MongoDB returns amounts as `Long`
  objects and `+` on those concatenates strings ("shares add up: false", garbage totals). Fixed with `Number(...)` and the outputs
  now match the app (Aarav +€715.96, Sneha settled, Σ = 0).
- `docs/demo-script.md`: a timed 7-minute viva script (new user and split, invite link in a second browser, the shared October
  split: add an expense for some people, calculate, record a payment, recalculate, report and PDF) with what to say and which
  query to show at each step, a cheat sheet of expected numbers and a troubleshooting list.
- **The script was verified by clicking through it in a real browser**, Part A (register, create split, copy link, logged-out
  preview, register and return to the join page, join) and Part B (add expense, calculate, record payment, recalculate, report,
  PDF download), and the figures in it were taken from the API and the database rather than computed by hand. That caught a bug
  and two wrong cells: a table in my first draft had a wrong "Paid" and "Share" cell (corrected from the real v2 snapshot).
- **Bug found by that run and fixed:** in the add-expense drawer, pressing Enter after clicking a toggle activated the focused
  button instead of saving. Plain Enter in a text field still saves; **Ctrl/Cmd+Enter now saves from anywhere** in the drawer.
- The demo database was reset to the pristine seeded state afterwards (4 users, 2 splits, 43 expenses, 3 calculations, 4 payments,
  2 reports, 2 PDFs in GridFS).

## Status after this round

All six steps requested are done and committed. Tests: 86 backend tests (`./mvnw -Pintegration test`) pass; the frontend builds and
lints clean. Still not done (deliberately deferred): light-theme polish, the prod single-jar build, Swagger, `docs/architecture.md`,
`docs/api.md`, the final README, the full §14 acceptance pass and all of §13.
