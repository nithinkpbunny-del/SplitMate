# SplitMate: Build Spec for Claude Code

> **How to use this file:** put it in an empty project folder as `SPEC.md`, open Claude Code in that folder, and say:
> *"Read SPEC.md fully. Then build the entire project phase by phase exactly as specified. Verify each phase before moving on."*

---

## 0. Your role and working rules

You are a senior full-stack engineer and product designer. Build **SplitMate**, a production-quality, demo-ready web app where friends who live together (e.g. Indian master's students sharing a flat in Germany) track shared expenses, split them fairly, and see who owes whom.

This is a college project that will be **demonstrated live**, including the MongoDB data. The examiner must be able to see every action stored in the database. It is also meant to show off **Java** skills, so the backend must be clean, idiomatic, layered Java.

Working rules:
1. **Read this whole spec before writing code.** Then make a short plan and start.
2. **Check the environment first:** run `java -version`, `node -v`, `npm -v`, `git --version`, and check MongoDB is reachable (`docker ps` or a `mongosh` ping). If something is missing, stop and tell me exactly what to install. Do not work around a missing JDK or database.
3. Work through the **phases in Section 12 in order**. After each phase: build, run the tests, start the app and check the feature works, then `git commit` with a clear message. Do not stop to ask for confirmation between phases unless you are genuinely blocked.
4. Use the **latest stable** versions of every library (generate the backend from start.spring.io conventions). Do not invent APIs. If unsure how a library works, check its docs.
5. **Money is never a `double`/`float`.** All amounts are stored and computed as `long` minor units (cents). See Section 6.
6. No placeholder code, no `TODO` left behind, no fake data in production paths. The demo seeder is the only place sample data exists.
7. Keep a running `docs/BUILD_LOG.md` with what each phase delivered and any decisions you made.
8. If a skill for frontend design is available to you, load and use it for the UI work.

---

## 1. Product summary

| # | Requirement | Where it is specified |
|---|---|---|
| 1 | Users register and log in | §5 Auth, §9 pages |
| 2 | A user creates a **Split** (a group is always called a "Split" in the UI and code). Creating it generates an **invite link** the creator can copy and send | §7.2 |
| 3 | A split has a name, e.g. "October month split" | §4 `splits` |
| 4 | Members add any number of expenses and choose **who each expense is shared with** (everyone, or only selected members) | §7.3 |
| 5 | **Calculate split** shows how much each person owes/gets back, who pays whom, and each person's individual spend | §6, §7.4 |
| 6 | MongoDB is the database, everything is persisted | §4 |
| 7 | Java backend | §2 |
| 8 | Outstanding UI | §8 |
| 9 | After calculating, generate a **spending analysis report** for the split/month (in-app + downloadable PDF) | §7.6 |
| 10 | All data is stored so it can be demonstrated | §4, §10 |

---

## 2. Tech stack

**Backend (Java)**
- Java 21 (LTS), Spring Boot (latest stable), Maven with the **Maven Wrapper** (`mvnw`) committed
- Spring Web, Spring Data MongoDB (use both repositories and `MongoTemplate` for aggregations), Spring Security, Bean Validation (Jakarta), Spring Boot Actuator (health only)
- JWT auth: Spring Security's built-in JWT support (`spring-boot-starter-oauth2-resource-server`, `NimbusJwtEncoder`/`NimbusJwtDecoder` with an HS256 secret) or `jjwt`. Pick one and use it consistently.
- Passwords: BCrypt
- PDF report: an HTML template (Thymeleaf) rendered to PDF with **OpenHTMLtoPDF** (maintained fork) or **OpenPDF**. Charts in the PDF via **JFreeChart** rendered to PNG. Do **not** use iText (AGPL).
- MongoDB **GridFS** to store generated PDF files (shows off another MongoDB feature)
- Lombok is allowed but optional; Java `record`s for DTOs are preferred
- Tests: JUnit 5, AssertJ, Spring Boot Test. Testcontainers for Mongo integration tests **only if Docker is available**; otherwise use unit tests plus a clearly named integration test profile.

**Frontend**
- React + TypeScript + Vite
- Tailwind CSS, shadcn/ui (Radix primitives), lucide-react icons
- Framer Motion (animation), Recharts (charts), TanStack Query (server state), React Router, React Hook Form + Zod, sonner (toasts), canvas-confetti
- `Intl.NumberFormat` for currency display

**Infrastructure**
- `docker-compose.yml` with `mongo:8` and `mongo-express` (browser DB viewer for the demo, bound to localhost only)
- Also works with a local MongoDB install or MongoDB Atlas via `MONGODB_URI`

**Single deployable:** in the `prod` Maven profile, `frontend-maven-plugin` installs Node, builds the React app, and copies `frontend/dist` into the Spring Boot static resources, so `java -jar` serves everything on one port. Spring must forward unknown non-`/api` routes to `index.html` for client-side routing.

---

## 3. Repository layout

```
splitmate/
├── SPEC.md
├── README.md
├── docker-compose.yml
├── .env.example
├── .gitignore
├── docs/
│   ├── BUILD_LOG.md
│   ├── architecture.md          # Mermaid diagrams: system, data model, calculate flow, invite flow
│   ├── api.md                   # every endpoint with request/response examples
│   ├── mongodb-queries.md       # mongosh queries for the live demo (see §10)
│   └── demo-script.md           # step-by-step script for the viva demo
├── backend/
│   ├── mvnw, mvnw.cmd, .mvn/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/splitmate/
│       │   ├── SplitMateApplication.java
│       │   ├── config/          # SecurityConfig, MongoConfig (indexes, auditing), CorsConfig, WebConfig (SPA forwarding)
│       │   ├── auth/            # controller, service, JWT util, cookie handling, DTOs
│       │   ├── user/
│       │   ├── split/           # Split, Member, invite logic
│       │   ├── expense/
│       │   ├── category/
│       │   ├── settlement/      # calculation engine + snapshots
│       │   ├── payment/
│       │   ├── report/          # aggregations, insights, PDF, GridFS
│       │   ├── activity/
│       │   ├── common/          # Money util, exceptions, error DTO, security helpers
│       │   └── seed/            # DemoDataSeeder
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   ├── application-demo.yml
│       │   └── templates/report.html
│       └── test/java/com/splitmate/...
└── frontend/
    ├── package.json, vite.config.ts (proxy /api -> http://localhost:8080), tailwind config
    └── src/
        ├── main.tsx, App.tsx, routes.tsx
        ├── api/                 # typed client, one module per resource
        ├── components/ui/       # shadcn components
        ├── components/          # app components (MoneyText, AvatarStack, SettlementGraph...)
        ├── features/            # auth, splits, expenses, settlement, reports, activity
        ├── hooks/, lib/ (format.ts, money.ts, utils.ts)
        └── styles/
```

---

## 4. MongoDB data model

Database name: `splitmate`. Use `@Document`, `@Indexed`, `@CompoundIndex`, and create indexes on startup (`MongoConfig` with `auto-index-creation: true` or explicit `IndexOperations`). Enable Mongo auditing for `createdAt`/`updatedAt`.

### 4.1 `users`
```json
{
  "_id": ObjectId,
  "name": "Aarav Mehta",
  "email": "aarav@demo.com",          // lowercase, trimmed, UNIQUE index
  "passwordHash": "$2a$...",
  "avatarColor": "#F59E0B",           // assigned from a fixed palette at register
  "createdAt": ISODate, "updatedAt": ISODate, "lastLoginAt": ISODate
}
```

### 4.2 `splits`
```json
{
  "_id": ObjectId,
  "name": "October month split",       // 3-60 chars
  "description": "Flat 4B, Berlin",     // optional
  "emoji": "🏠",                       // optional cover emoji
  "currency": "EUR",                   // ISO 4217, default EUR; fixed after first expense
  "ownerId": ObjectId,
  "members": [                          // embedded: groups are small and bounded
    { "userId": ObjectId, "name": "...", "email": "...", "avatarColor": "...",
      "role": "OWNER" | "MEMBER", "joinedAt": ISODate, "active": true }
  ],
  "inviteCode": "k7Qp2xZr9LmA",         // UNIQUE index; 12 chars base62 from SecureRandom
  "inviteEnabled": true,
  "inviteRegeneratedAt": ISODate,
  "period": { "start": "2026-10-01", "end": "2026-10-31" },   // optional, used by reports
  "status": "OPEN" | "SETTLED" | "ARCHIVED",
  "lastCalculatedAt": ISODate,          // null until first calculate
  "lastExpenseChangeAt": ISODate,       // used to flag a stale calculation
  "createdAt": ISODate, "updatedAt": ISODate
}
```
Indexes: `inviteCode` unique, `members.userId`, `ownerId`.

### 4.3 `categories`
Seeded at startup if empty (idempotent). `splitId: null` = global default; a split may add custom categories.
```json
{ "_id": ObjectId, "key": "groceries", "name": "Groceries", "icon": "shopping-cart",
  "color": "#22C55E", "splitId": null, "isDefault": true, "sortOrder": 2 }
```
Defaults: Rent, Groceries, Utilities (electricity/internet), Dining Out, Transport, Household, Entertainment, Travel, Shopping, Health, Subscriptions, Other.

### 4.4 `expenses`
```json
{
  "_id": ObjectId,
  "splitId": ObjectId,
  "description": "Lidl weekly groceries",
  "amountMinor": 8640,                 // €86.40, long, > 0, max 10,000,000 (=€100k)
  "currency": "EUR",
  "categoryId": ObjectId,
  "paidBy": ObjectId,                  // member userId; defaults to the creator, can be any active member
  "splitType": "EQUAL",                // EQUAL required; EXACT is a stretch goal (§13)
  "participants": [ObjectId, ...],     // who shares it; >= 1; default = all active members
  "shares": [ { "userId": ObjectId, "amountMinor": 2880 } ],  // computed server-side, sums exactly to amountMinor
  "date": "2026-10-12",                // LocalDate
  "notes": "optional",
  "createdBy": ObjectId,
  "deleted": false,                    // soft delete; deleted expenses are excluded from all math
  "createdAt": ISODate, "updatedAt": ISODate
}
```
Indexes: `{splitId: 1, date: -1}`, `{splitId: 1, deleted: 1}`, `{splitId: 1, paidBy: 1}`.

### 4.5 `settlements` (calculation snapshots, one per "Calculate split" click)
```json
{
  "_id": ObjectId,
  "splitId": ObjectId,
  "version": 3,                        // increments per split
  "calculatedBy": ObjectId,
  "calculatedAt": ISODate,
  "currency": "EUR",
  "totalSpendMinor": 124530,
  "expenseCount": 27,
  "expenseIds": [ObjectId, ...],       // exactly which expenses were included
  "memberSummaries": [
    { "userId": ObjectId, "name": "Aarav", "paidMinor": 52000, "shareMinor": 31132,
      "paymentsSentMinor": 0, "paymentsReceivedMinor": 0, "netMinor": 20868 }
  ],
  "transfers": [
    { "fromUserId": ObjectId, "fromName": "Isha", "toUserId": ObjectId, "toName": "Aarav", "amountMinor": 12040 }
  ]
}
```
Index: `{splitId: 1, version: -1}` unique.

### 4.6 `payments` (real-world "I paid you back" records)
```json
{ "_id": ObjectId, "splitId": ObjectId, "fromUserId": ObjectId, "toUserId": ObjectId,
  "amountMinor": 12040, "note": "PayPal", "settlementId": ObjectId | null,
  "recordedBy": ObjectId, "createdAt": ISODate }
```

### 4.7 `reports`
```json
{
  "_id": ObjectId, "splitId": ObjectId, "settlementId": ObjectId,
  "generatedBy": ObjectId, "generatedAt": ISODate,
  "periodStart": "2026-10-01", "periodEnd": "2026-10-31",
  "stats": {
    "totalSpendMinor": 0, "expenseCount": 0, "avgPerExpenseMinor": 0, "avgPerDayMinor": 0,
    "byCategory": [ { "categoryId": "", "name": "", "color": "", "totalMinor": 0, "count": 0, "percent": 0.0 } ],
    "byMember":   [ { "userId": "", "name": "", "paidMinor": 0, "shareMinor": 0, "netMinor": 0, "percentOfPaid": 0.0 } ],
    "dailyTrend": [ { "date": "2026-10-01", "totalMinor": 0 } ],
    "weekdayPattern": [ { "weekday": "MONDAY", "totalMinor": 0 } ],
    "topExpenses": [ { "expenseId": "", "description": "", "amountMinor": 0, "paidByName": "", "date": "" } ],
    "memberCategoryMatrix": [ { "userId": "", "categoryKey": "", "shareMinor": 0 } ]
  },
  "insights": [ "Rent made up 58% of all spending.", "..." ],
  "pdfFileId": ObjectId                // GridFS file id
}
```
PDF bytes live in GridFS (`fs.files` / `fs.chunks`) with metadata `{ reportId, splitId }`.

### 4.8 `activity_logs`
```json
{ "_id": ObjectId, "splitId": ObjectId, "actorId": ObjectId, "actorName": "Aarav",
  "type": "SPLIT_CREATED|MEMBER_JOINED|MEMBER_LEFT|MEMBER_REMOVED|INVITE_REGENERATED|EXPENSE_ADDED|EXPENSE_UPDATED|EXPENSE_DELETED|SPLIT_CALCULATED|PAYMENT_RECORDED|REPORT_GENERATED|SPLIT_SETTLED",
  "message": "Aarav added Lidl weekly groceries (€86.40)",
  "metadata": { }, "createdAt": ISODate }
```
Index: `{splitId: 1, createdAt: -1}`.

**No multi-document transactions** (they need a replica set). Design writes so each operation is safe with single-document atomicity; use `findAndModify`/conditional updates where it matters (e.g. incrementing settlement `version`, joining via invite without duplicate members using `$addToSet`-style guards on `members.userId`).

---

## 5. Authentication and security

- `POST /api/auth/register` {name, email, password} → creates user, logs in.
  Password rules: min 8 chars, at least one letter and one number. Email unique (409 on conflict).
- `POST /api/auth/login` {email, password} → sets JWT in an **httpOnly, SameSite=Lax cookie** (`Secure` in prod), 7-day expiry. Returns the user.
- `POST /api/auth/logout` → clears cookie.
- `GET /api/auth/me` → current user or 401.
- Custom `BearerTokenResolver` that reads the token from the cookie (and also accepts `Authorization: Bearer` for testing with curl/Postman).
- Secret from env `JWT_SECRET` (min 32 bytes); fail fast at startup if missing in prod. `.env.example` documents it.
- CSRF: because auth is a cookie, enable Spring's CSRF with the cookie-to-header pattern (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header) and make the frontend API client send it.
- **Authorization on every split-scoped endpoint:** the caller must be an active member, else return **404** (do not leak existence). Owner-only actions return 403 for non-owner members.
- Global `@RestControllerAdvice` producing consistent errors:
  `{ "timestamp", "status", "error", "message", "path", "fieldErrors": [{field, message}] }`
- Basic in-memory rate limit on login (e.g. 10 attempts / 5 min per IP+email) to show security awareness.
- Never return `passwordHash`. Log no secrets.

---

## 6. The split calculation engine (core logic, must be exact)

Implement as a pure, framework-free Java class, e.g. `SettlementCalculator`, with thorough unit tests.

### 6.1 Money
- `Money` utility: parse user input like `"86.4"`, `"86,40"`, `"1.234,50"` (European formats are common for these users) into minor units; format for display. Reject more than 2 decimal places and non-positive values.

### 6.2 Equal shares for one expense
Given `amountMinor` and `n` participants:
- `base = amountMinor / n`, `remainder = amountMinor % n`
- Sort participants by `userId` (stable, deterministic). The first `remainder` participants get `base + 1`, the rest get `base`.
- Assert the shares sum exactly to `amountMinor`.
- Example: €10.00 among 3 → 334, 333, 333.
- Shares are computed **server-side** when an expense is created/updated and stored on the expense.

### 6.3 Balances (per member)
For all non-deleted expenses in the split plus all payments:
```
paid(u)              = Σ amountMinor of expenses where paidBy == u
share(u)             = Σ shares[u].amountMinor across expenses
paymentsSent(u)      = Σ payments where from == u
paymentsReceived(u)  = Σ payments where to == u
net(u)               = paid(u) - share(u) + paymentsSent(u) - paymentsReceived(u)
```
- `net > 0` → the group owes them (gets back). `net < 0` → they owe.
- Invariant: **Σ net = 0**. Assert it; if it fails, throw (this is a bug, never hide it).
- "Individual spend" shown in the UI = **both** `paid` (money they actually put out) and `share` (what they actually consumed). Label them clearly: *"Paid"* and *"Their share"*.

### 6.4 Who pays whom (minimise number of transfers)
Greedy settlement:
1. Creditors = members with net > 0 (sorted desc), debtors = members with net < 0 (sorted by most negative).
2. Repeatedly match the largest debtor with the largest creditor, transfer `min(|debt|, credit)`, reduce both, drop whoever reaches 0.
3. Tie-break deterministically by `userId` so results are stable across runs.
This produces at most `n - 1` transfers.

### 6.5 Required unit tests (all must pass)
1. **Worked example**, A, B, C:
   - A pays €90.00 groceries shared by A, B, C → 30 each
   - B pays €40.00 dinner shared by B, C → 20 each
   - Expect net: A +60.00, B −10.00, C −50.00; transfers: C→A €50.00, B→A €10.00
2. €10.00 among 3 → shares 334/333/333, sum exact.
3. Payer not among participants (A pays €30 for B and C only) → A +30, B −15, C −15.
4. Payment recorded C→A €50 after test 1 → C net 0, A +10.
5. All balanced → zero transfers, split can be marked settled.
6. Randomised property test: 1,000 random groups/expenses → Σ net == 0, transfers settle every balance to exactly 0, transfer count ≤ n−1.
7. Single-member split → no transfers.
8. Deleted expenses are ignored.

---

## 7. Features and API

All endpoints under `/api`, JSON, validated, documented in `docs/api.md`. Add springdoc OpenAPI (Swagger UI at `/swagger-ui.html`) in dev/demo profiles. It's useful in the viva.

### 7.1 Splits
| Method | Path | Notes |
|---|---|---|
| GET | `/splits` | My splits, with per-split summary: member count, total spend, **my net balance**, last activity, status |
| POST | `/splits` | {name, description?, emoji?, currency?, period?}. Creator becomes OWNER. Generates invite code. Returns split + `inviteUrl` |
| GET | `/splits/{id}` | Full split incl. members, invite URL (members only) |
| PATCH | `/splits/{id}` | Owner: rename, description, emoji, period, archive |
| DELETE | `/splits/{id}` | Owner only, only if no expenses; otherwise archive |
| DELETE | `/splits/{id}/members/{userId}` | Owner removes a member, or a member removes themself (leave). **Blocked if that member's net ≠ 0** or they are on any non-deleted expense (clear 409 message explaining why) |

### 7.2 Invite link flow
- `inviteUrl = ${APP_BASE_URL}/join/{inviteCode}` (`APP_BASE_URL` from config, default `http://localhost:5173` in dev, the app URL in prod).
- `GET /invites/{code}` → **public**, limited preview: split name, emoji, owner name, member count, member first names/avatars. 404 if code unknown or invite disabled.
- `POST /invites/{code}/join` → authenticated; adds caller as MEMBER (idempotent: joining twice is a no-op that returns the split). Logs `MEMBER_JOINED`.
- `POST /splits/{id}/invite/regenerate` (owner) → new code, old link stops working.
- `PATCH /splits/{id}/invite` (owner) → enable/disable link.
- Frontend: if a logged-out user opens `/join/{code}`, show the preview, then send them to login/register and **return them to the join page afterwards** (store the return path), then one click to join.

### 7.3 Expenses
| Method | Path | Notes |
|---|---|---|
| GET | `/splits/{id}/expenses` | Filters: `category`, `paidBy`, `participant`, `from`, `to`, `q` (text search on description), sort, pagination |
| POST | `/splits/{id}/expenses` | {description, amount (string), categoryId, paidBy?, participants?, date, notes?}. Defaults: paidBy = caller, participants = all active members. Server computes shares. Updates `lastExpenseChangeAt`. Logs activity |
| PUT | `/splits/{id}/expenses/{expenseId}` | Allowed for the creator, the payer, or the split owner |
| DELETE | `/splits/{id}/expenses/{expenseId}` | Soft delete, same permission rule |
| GET | `/categories?splitId=` | Defaults + split's custom categories |
| POST | `/splits/{id}/categories` | Custom category (stretch, see §13) |

Validation: participants must all be active members; paidBy must be an active member; amount > 0; description 1–80 chars; date not more than 1 day in the future; split must be OPEN.

### 7.4 Calculate split
- `POST /splits/{id}/calculate` → runs the engine on current data, stores a new `settlements` snapshot (version+1), sets `lastCalculatedAt`, logs `SPLIT_CALCULATED`, returns the snapshot.
- `GET /splits/{id}/settlements/latest` and `GET /splits/{id}/settlements` (history).
- The split detail response includes `calculationStale: true` when `lastExpenseChangeAt > lastCalculatedAt` or a payment was recorded after it, so the UI can show "Expenses changed since last calculation. Recalculate."

### 7.5 Settle up (payments)
- `POST /splits/{id}/payments` {fromUserId, toUserId, amount, note?}. Allowed if the caller is `from` or `to` (or owner). Amount must be > 0 and should not exceed what `from` currently owes `to` per the latest settlement (warn, do not hard-block, with a clear message).
- `GET /splits/{id}/payments`, `DELETE /splits/{id}/payments/{paymentId}` (recorder or owner).
- When every member's net is 0 after a calculation, the UI offers **"Mark split as settled"** → status SETTLED, logs `SPLIT_SETTLED`, confetti.

### 7.6 Spending analysis report
- After a calculation, the results screen shows **"Generate report"**.
- `POST /splits/{id}/reports` {periodStart?, periodEnd?} (defaults to the split's period, else the min/max expense dates) → builds stats with **MongoDB aggregation pipelines via `MongoTemplate`** (this is a showcase; use `$match`, `$group`, `$unwind`, `$lookup`, `$facet`, `$dateToString`, `$dayOfWeek`, `$sort`, `$limit`), generates rule-based insights, renders the PDF, stores it in GridFS, saves the `reports` doc, logs activity, returns the report.
- Insights (generate 4–7 that apply, computed from the data, never hard-coded): top category share; highest-spend day; biggest single expense; who paid the most vs consumed the most; average per person per day; weekend vs weekday spending; if a period is set and a previous split exists for the same members, change vs that previous split.
- `GET /splits/{id}/reports`, `GET /reports/{reportId}`, `GET /reports/{reportId}/pdf` (streams from GridFS, `Content-Disposition: attachment; filename="SplitMate-October-month-split-2026-10.pdf"`).
- `GET /splits/{id}/export.csv` → all expenses as CSV.
- **PDF contents:** cover header (split name, period, members, generated date), KPI row (total spend, # expenses, avg per day, avg per person), category donut/pie chart, daily trend line chart, per-member table (paid / share / net), who-pays-whom list, top 5 expenses, insights, full expense ledger table at the end. Clean, branded, A4, page numbers in the footer.

### 7.7 Activity
- `GET /splits/{id}/activity?limit=` → timeline for the split.

---

## 8. UI and UX: make it genuinely outstanding

This UI is the first thing the examiner sees. It must feel like a polished fintech product, not a template. Avoid the generic look: no default purple-blue gradients, no stock dashboard cards, no Bootstrap feel.

### 8.1 Visual direction
- **Theme:** dark-first ("midnight") with a full light theme and a toggle (respect system preference, remember choice).
  - Dark: background `#0A0C10`, surfaces `#11141A` / `#161A22`, hairline borders `rgba(255,255,255,0.07)`, subtle noise texture overlay at ~3% opacity, soft radial glow behind hero numbers.
  - Accent: **acid lime `#D4FF3F`** for primary CTAs (black text on it). Money colours: gets-back **emerald `#34D399`**, owes **coral `#FB7185`**, neutral slate.
  - Light theme: warm off-white `#FAFAF7`, ink `#0A0C10`, same accent family adjusted for contrast.
- **Typography:** headings in **Bricolage Grotesque** (or Space Grotesk), body in **Inter**, all money figures in **JetBrains Mono** or Inter with `font-variant-numeric: tabular-nums` so columns align. Big, confident money numbers.
- **Signature elements:**
  - `MoneyText` component: colour by sign, animated count-up on change, `+`/`−` prefix, currency via `Intl.NumberFormat`.
  - Member avatars: coloured initial circles; overlapping `AvatarStack` with "+3".
  - Expense list styled like a **receipt**: dashed dividers, category icon chip, payer avatar, "split 3 ways" pill, amount right-aligned in mono.
  - **Settlement graph:** an SVG/animated view where member avatars sit in a circle and curved arrows flow from debtor to creditor with the amount on the arrow. A list view toggle sits beside it.
  - Diverging horizontal bar chart for balances (coral left, emerald right of a zero line).
- **Motion (Framer Motion):** page transitions, staggered list entrances, hover lift on cards, shared-layout animation when opening an expense. **Calculate** plays a ~1.2s "crunching numbers" sequence (shimmering digits), then reveals results with stagger. Confetti when a split is fully settled. Respect `prefers-reduced-motion`.
- **States:** skeleton loaders for every list, thoughtful empty states with an illustration-like icon composition and a single clear CTA, optimistic updates for add/delete expense, toasts for every action, inline field errors.
- **Responsive:** mobile-first. On mobile, add-expense opens as a bottom sheet and a floating "+" button is present. On desktop, a right-side drawer. Nothing breaks at 360px width.
- **Accessibility:** visible focus rings, keyboard navigable dialogs, aria labels, colour is never the only signal (owes/gets-back also use words and +/− signs), WCAG AA contrast.
- **Keyboard shortcuts** on the split page: `N` new expense, `C` calculate, `/` focus search, `?` shortcuts help.

### 8.2 Pages
1. **Landing** (`/`): bold hero ("Split rent, groceries and everything in between. No awkward maths."), animated mock of a settlement graph, three feature blocks, CTA to sign up. Logged-in users go to the dashboard.
2. **Register / Login**: split-screen layout, form on one side, animated brand panel on the other. Password strength meter. Return-to-path support for invite links.
3. **Dashboard** (`/app`): greeting, summary strip ("You are owed €42.10 overall · You owe €18.00"), grid of split cards (emoji, name, period, avatar stack, total spend, my balance chip, "stale calculation" dot), "New split" card. Recent activity across splits.
4. **Create split** dialog: name (placeholder "October month split"), emoji picker (small curated set), currency (default EUR), optional period (month picker that fills start/end). On success, a **share screen**: large invite link in a mono field, **Copy** button with checkmark animation, **Share** (Web Share API when available), "Send on WhatsApp" link (`https://wa.me/?text=...`), QR code of the link, and "Go to split".
5. **Join page** (`/join/:code`): invite card ("Aarav invited you to **October month split**", members' avatars, member count), Join button. Handles invalid/disabled links gracefully.
6. **Split detail** (`/app/splits/:id`): header with emoji, name, period, avatars, total spend, my balance, invite button (opens share screen), owner menu (rename, regenerate link, disable link, archive). Stale-calculation banner when relevant. Tabs:
   - **Expenses:** receipt-style list grouped by date, filters (category chips, payer, member, search), totals for the current filter.
   - **Balances:** latest calculation: diverging bar chart, per-member cards showing **Paid**, **Their share**, **Net**; who-pays-whom as graph + list; personalised line at top ("You owe Isha €23.40" / "Rohan owes you €12.00"); "Record payment" on each transfer.
   - **Report:** generated reports list + "Generate report"; opens the report view.
   - **Activity:** timeline.
   - Primary floating actions: **Add expense** and **Calculate split**.
7. **Add/Edit expense** drawer: huge amount input (accepts `86,40` or `86.40`), description, category chip grid, date (default today), **Paid by** selector (avatars, default me), **Split with** member toggles with "Everyone" / "Only me + …" quick chips, **live preview** of each person's share as you type ("€28.80 each × 3"), notes. Save with `Enter`.
8. **Calculation results** (after hitting Calculate): animated reveal of total spend, per-member Paid / Share / Net, transfers graph, then two CTAs: **Generate spending report** and **Record payments**.
9. **Report view** (`/app/splits/:id/reports/:reportId`): KPI cards, category donut, daily trend area chart, weekday bar chart, per-member paid-vs-share grouped bars, member × category heat-map table, top expenses, insights as callout cards, **Download PDF** and **Export CSV** buttons.
10. **Profile**: name, avatar colour, change password, theme.
11. **404** and error boundary pages that still look designed.

---

## 9. Frontend engineering details
- Typed API client (`fetch` wrapper) handling JSON, CSRF header, 401 → redirect to login with return path, and error-to-toast mapping using the backend error shape.
- TanStack Query keys per resource; invalidate split/expenses/settlement queries after mutations.
- Zod schemas mirror backend validation.
- `lib/money.ts`: parse input to minor units and format with `Intl.NumberFormat(locale, { style: 'currency', currency })`. Never use floats for arithmetic.
- Route guards: `/app/**` requires auth.
- Vite dev proxy: `/api` → `http://localhost:8080`, so cookies work same-origin in dev.

---

## 10. Demo readiness (very important)

1. **Demo seeder** (`seed` package), runs only with profile `demo` (`--spring.profiles.active=demo`), idempotent (does nothing if demo users exist):
   - 4 users, password `Demo@1234`: Aarav Mehta `aarav@demo.com`, Isha Rao `isha@demo.com`, Rohan Nair `rohan@demo.com`, Sneha Iyer `sneha@demo.com`
   - Split **"October month split"** (emoji 🏠, EUR, period 2026-10-01 → 2026-10-31, "Flat 4B, Berlin") with ~30 realistic expenses: rent €1,600 paid by Aarav for all; utilities; internet; weekly Lidl/Aldi/Rewe groceries by different people; a dinner shared by only 2–3 people; Deutschlandticket-style transport for some; a weekend trip to Prague shared by 3; household supplies; a Netflix subscription for 2.
   - One earlier calculation, one recorded payment, one generated report, activity logs for everything.
   - A second split **"September month split"** already SETTLED, so dashboards and report comparisons have history.
2. **`docs/mongodb-queries.md`**: copy-paste `mongosh` commands to demonstrate the database live: list collections; show a user (no password field shown, use projection); find all splits a user belongs to (`members.userId`); expenses of a split sorted by date; aggregation of spend by category with `$lookup` to category names; per-member paid totals; show indexes (`getIndexes()`); latest settlement snapshot; GridFS files (`fs.files`); activity timeline. Each with a one-line explanation of what it proves.
3. **`docs/demo-script.md`**: a 5–7 minute viva walkthrough: register a new user → create split → copy invite link → open it in an incognito window as a second user → join → add expenses with different participants → calculate → show results → record a payment → recalculate → generate report → download PDF → show the same data in MongoDB Compass/mongo-express and with the mongosh queries.
4. **`docs/architecture.md`**: Mermaid diagrams for system architecture, collection relationships, the invite flow (sequence diagram) and the calculate flow.
5. README: what it is, screenshots section (placeholders listed by filename for me to capture), prerequisites, setup, run commands, demo accounts, project structure, API overview link, testing.

---

## 11. Configuration
`application.yml` (with env overrides):
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/splitmate}
      auto-index-creation: true
app:
  base-url: ${APP_BASE_URL:http://localhost:5173}
  jwt:
    secret: ${JWT_SECRET:}
    expiry-days: 7
  cookie:
    secure: ${COOKIE_SECURE:false}
server:
  port: ${PORT:8080}
```
- Profile `dev`: allows a generated dev JWT secret with a loud warning log; Swagger on.
- Profile `demo`: dev + seeder.
- Profile `prod`: requires `JWT_SECRET`, secure cookies, Swagger off.
- `.env.example` lists every variable with comments. Load `.env` in dev (e.g. a small loader or document `export` usage; keep it simple and cross-platform; document Windows PowerShell too).

---

## 12. Build phases (do them in order, verify, commit after each)

**Phase 0: Scaffold.** Environment check (§0.2). Monorepo layout, Spring Boot app with Maven wrapper, React+Vite+TS+Tailwind+shadcn app, docker-compose (mongo:8 + mongo-express on localhost), `.gitignore`, `.env.example`, README skeleton. Health endpoint works, frontend dev server proxies to it. Commit.

**Phase 1: Auth.** User model, register/login/logout/me, JWT cookie, CSRF, BCrypt, validation, global error handler, login rate limit. Frontend auth pages + guards + return-to-path. Tests for auth service. Commit.

**Phase 2: Splits and invites.** Split CRUD, embedded members, invite code generation/regeneration/disable, public preview, join, leave/remove with balance guard, activity logging. Dashboard, create-split dialog with share screen (copy, share, WhatsApp, QR), join page. Commit.

**Phase 3: Categories and expenses.** Category seeding, expense CRUD with server-side share computation, filters, soft delete, permission rules. Expenses tab, add/edit drawer with live share preview. Commit.

**Phase 4: Calculation engine and settlement.** `SettlementCalculator` + **all tests in §6.5 passing**, calculate endpoint with snapshots and stale detection, payments, mark settled. Balances tab, calculation results screen with animated reveal, settlement graph, record-payment flow, confetti. Commit.

**Phase 5: Reports.** Aggregation pipelines, insights, PDF (Thymeleaf + OpenHTMLtoPDF/OpenPDF + JFreeChart), GridFS storage and download, CSV export. Report tab and report view with all charts. Commit.

**Phase 6: UI polish pass.** Go screen by screen against §8: motion, empty/loading/error states, mobile layouts at 360px, light theme, keyboard shortcuts, accessibility. Fix anything that looks generic. Commit.

**Phase 7: Demo and docs.** Demo seeder, `mongodb-queries.md` (run each query yourself against the seeded DB to confirm it works), `demo-script.md`, `architecture.md`, `api.md`, README, Swagger. `prod` profile single-jar build works: `./mvnw -Pprod clean package` then `java -jar backend/target/*.jar` serves the full app on :8080. Commit.

**Phase 8: Final verification.** Run the full acceptance checklist below end to end, fix failures, update BUILD_LOG.md, final commit.

---

## 13. Stretch goals (only after Phase 8 is fully green)
1. `EXACT` and `PERCENT` split types in the expense drawer (validated to sum exactly).
2. Custom categories per split.
3. Receipt image upload stored in GridFS.
4. Multi-currency expenses converted to the split currency with a stored exchange rate.
5. Email/notification stubs for "you were added to an expense".

---

## 14. Acceptance checklist (verify every item yourself before finishing)
- [ ] Fresh clone + prerequisites + README steps → app runs with no manual code edits
- [ ] Register, logout, login; wrong password shows a friendly error; rate limit triggers
- [ ] Create "October month split" → invite link shown, copy works
- [ ] Open the link logged out in another browser profile → preview → register → automatically back on join → joined
- [ ] Non-member hitting a split URL or API gets 404
- [ ] Add 10+ expenses with different payers and different participant subsets
- [ ] Calculate → per member Paid / Their share / Net correct (spot-check one by hand and write it in BUILD_LOG.md); Σ net = 0; transfers ≤ n−1
- [ ] Edit an expense → stale banner → recalculate → new snapshot version stored
- [ ] Record payment → balances update correctly after recalculation
- [ ] Generate report → in-app charts + insights render; PDF downloads, opens and looks professional
- [ ] Every action above is visible in MongoDB (users, splits, expenses, categories, settlements, payments, reports, fs.files/fs.chunks, activity_logs)
- [ ] All `docs/mongodb-queries.md` queries run successfully on demo data
- [ ] `./mvnw test` green; frontend `npm run build` and `npm run lint` clean
- [ ] UI checked at 360px, 768px and 1440px, dark and light themes
- [ ] `prod` single-jar build serves the full app
