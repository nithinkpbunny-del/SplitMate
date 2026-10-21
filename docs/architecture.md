# Architecture

SplitMate is one Spring Boot application (Java 21) that serves a JSON API, plus a React single-page app, on top of one
MongoDB database. In development the two run as separate processes; in a production build the React app is bundled into the
jar and everything is served from one port.

Contents: [system](#1-system-architecture) · [collections](#2-collection-relationships) ·
[invite flow](#3-invite-flow) · [calculate flow](#4-calculate-flow) · [report flow](#5-report-flow) ·
[design decisions](#design-decisions)

---

## 1. System architecture

```mermaid
flowchart LR
    subgraph Browser["Browser: React + TypeScript SPA"]
        UI["Pages and components<br/>(features/*)"]
        RQ["TanStack Query cache"]
        CLIENT["Typed API client<br/>CSRF header, 401 handling"]
        UI --> RQ --> CLIENT
    end

    subgraph Spring["Spring Boot 4 (Java 21)"]
        direction TB
        SEC["Security filter chain<br/>CORS · CSRF (cookie to header)<br/>JWT from httpOnly cookie"]
        subgraph Web["Controllers (/api/**)"]
            AUTHC["auth · user"]
            SPLITC["split · invite"]
            EXPC["expense · category"]
            SETC["settlement · payment"]
            REPC["report · activity"]
        end
        subgraph Services["Services"]
            SVC["AuthService · SplitService · ExpenseService<br/>SettlementService · PaymentService<br/>ReportService · ActivityService"]
            ENGINE["SettlementCalculator + EqualSplit<br/>(pure Java, no framework)"]
            PDF["PdfReportRenderer<br/>Thymeleaf + OpenHTMLtoPDF"]
        end
        subgraph Data["Data access"]
            REPO["Spring Data repositories"]
            TEMPLATE["MongoTemplate<br/>aggregation pipelines"]
            GRID["GridFsTemplate"]
        end
        SEC --> Web --> SVC
        SVC --> ENGINE
        SVC --> PDF
        SVC --> REPO
        SVC --> TEMPLATE
        SVC --> GRID
    end

    subgraph Mongo["MongoDB (database: splitmate)"]
        COLL[("users · splits · categories<br/>expenses · settlements · payments<br/>reports · activity_logs")]
        FS[("fs.files · fs.chunks<br/>(report PDFs)")]
    end

    CLIENT -- "JSON over HTTP<br/>same origin (Vite proxy in dev)" --> SEC
    REPO --> COLL
    TEMPLATE --> COLL
    GRID --> FS
```

**How a request travels.** The browser calls `/api/...` on its own origin (in development the Vite dev server proxies `/api` to
port 8090, so the cookie is same-origin). The security filter chain reads the JWT from the `SM_TOKEN` cookie (or an
`Authorization: Bearer` header), checks the CSRF header on writes, and sets the caller. Controllers validate the request and
delegate to services. Every split-scoped service first calls `SplitAccess.requireMember`, which answers **404** (not 403) to
non-members so the existence of a split is never leaked. Services use Spring Data repositories for simple reads and writes,
`MongoTemplate` for atomic conditional updates and aggregation pipelines, and `GridFsTemplate` for PDFs.

**Layers and where the maths lives.** All money arithmetic is on `long` cents and lives in
`settlement/engine` (`EqualSplit`, `SettlementCalculator`), which has no Spring or MongoDB imports and is unit tested on its own,
including a 1,000-case randomised property test.

---

## 2. Collection relationships

Ids are `ObjectId`s. Members are **embedded** in the split (groups are small and bounded). Everything else refers to a split by
`splitId`. There are no multi-document transactions; each write is a single-document operation, with conditional updates where
concurrency matters (joining, the settlement version counter).

```mermaid
erDiagram
    USERS ||--o{ SPLITS : "owns (ownerId)"
    USERS }o--o{ SPLITS : "member of (members.userId)"
    SPLITS ||--o{ EXPENSES : "splitId"
    SPLITS ||--o{ SETTLEMENTS : "splitId, version"
    SPLITS ||--o{ PAYMENTS : "splitId"
    SPLITS ||--o{ REPORTS : "splitId"
    SPLITS ||--o{ ACTIVITY_LOGS : "splitId"
    CATEGORIES ||--o{ EXPENSES : "categoryId"
    USERS ||--o{ EXPENSES : "paidBy, participants, shares.userId"
    SETTLEMENTS ||--o{ PAYMENTS : "settlementId (optional)"
    SETTLEMENTS ||--o{ REPORTS : "settlementId"
    REPORTS ||--|| FS_FILES : "pdfFileId"
    FS_FILES ||--o{ FS_CHUNKS : "files_id"

    USERS {
        ObjectId _id PK
        string name
        string email UK "lowercase, unique index"
        string passwordHash "BCrypt"
        string avatarColor
        date lastLoginAt
    }
    SPLITS {
        ObjectId _id PK
        string name
        string currency "fixed after the first expense"
        ObjectId ownerId "index"
        array members "EMBEDDED: userId, name, role, active"
        string inviteCode UK "12 chars, unique index"
        bool inviteEnabled
        object period "start, end"
        string status "OPEN, SETTLED, ARCHIVED"
        date lastCalculatedAt
        date lastExpenseChangeAt
        date lastPaymentChangeAt
        int settlementVersion "findAndModify $inc"
    }
    CATEGORIES {
        ObjectId _id PK
        string key "unique with splitId"
        string name
        ObjectId splitId "null = global default"
    }
    EXPENSES {
        ObjectId _id PK
        ObjectId splitId FK
        long amountMinor "cents"
        ObjectId categoryId FK
        ObjectId paidBy FK
        array participants "ObjectIds"
        array shares "userId, amountMinor (sum = amount)"
        date date
        bool deleted "soft delete"
    }
    SETTLEMENTS {
        ObjectId _id PK
        ObjectId splitId FK
        int version "unique with splitId"
        array expenseIds "exactly what was included"
        array memberSummaries "paid, share, net per person"
        array transfers "who pays whom"
    }
    PAYMENTS {
        ObjectId _id PK
        ObjectId splitId FK
        ObjectId fromUserId FK
        ObjectId toUserId FK
        long amountMinor
        ObjectId settlementId FK
    }
    REPORTS {
        ObjectId _id PK
        ObjectId splitId FK
        ObjectId settlementId FK
        object stats "aggregation results"
        array insights
        ObjectId pdfFileId FK
    }
    ACTIVITY_LOGS {
        ObjectId _id PK
        ObjectId splitId FK
        ObjectId actorId FK
        string type
        string message
        date createdAt "index with splitId"
    }
    FS_FILES {
        ObjectId _id PK
        string filename
        object metadata "reportId, splitId"
    }
    FS_CHUNKS {
        ObjectId files_id FK
        int n
        binary data
    }
```

Indexes created by the application at startup (`@Indexed`, `@CompoundIndex`): `users.email` (unique), `splits.inviteCode` (unique),
`splits.members.userId`, `splits.ownerId`, `expenses {splitId, date}`, `{splitId, deleted}`, `{splitId, paidBy}`,
`settlements {splitId, version}` (unique), `activity_logs {splitId, createdAt}`, `categories {splitId, key}` (unique) and
`reports {splitId, generatedAt}`. See query 17 in [mongodb-queries.md](mongodb-queries.md).

---

## 3. Invite flow

A split gets an unguessable 12-character base62 code from `SecureRandom`. The public preview shows only first names. Joining is
idempotent and safe under concurrency because the member is added with a single guarded update.

```mermaid
sequenceDiagram
    autonumber
    actor Owner
    actor Friend
    participant SPA as React app
    participant API as Spring Boot
    participant DB as MongoDB

    Owner->>SPA: New split
    SPA->>API: POST /api/splits
    API->>DB: insert split (owner as member)<br/>inviteCode from SecureRandom, unique index
    API-->>SPA: split + inviteUrl (APP_BASE_URL/join/code)
    SPA-->>Owner: share screen: Copy, Share, WhatsApp, QR

    Owner-->>Friend: sends the link

    Friend->>SPA: opens /join/code (logged out)
    SPA->>API: GET /api/invites/code (public)
    API->>DB: find split by inviteCode where inviteEnabled
    API-->>SPA: preview: name, owner, member count, first names
    Friend->>SPA: "Create an account to join"
    SPA->>SPA: go to /register?next=/join/code
    Friend->>SPA: registers
    SPA->>API: POST /api/auth/register
    API-->>SPA: user + httpOnly SM_TOKEN cookie
    SPA->>SPA: return to /join/code (from ?next)

    Friend->>SPA: "Join split"
    SPA->>API: POST /api/invites/code/join
    alt already an active member
        API-->>SPA: split (no change, idempotent)
    else new member
        API->>DB: updateFirst where _id, inviteCode, inviteEnabled,<br/>members.userId not equal caller<br/>$push member
        API->>DB: insert activity MEMBER_JOINED
        API-->>SPA: split with the new member list
    end
    SPA-->>Friend: opens the split

    Owner->>API: PATCH /api/splits/id/invite {enabled:false}<br/>or POST .../invite/regenerate
    Note over API,DB: The old code stops working at once:<br/>the lookup no longer matches, so preview and join answer 404
```

---

## 4. Calculate flow

Pressing **Calculate split** stores an immutable, versioned snapshot. The stored `lastCalculatedAt` is compared with
`lastExpenseChangeAt` and `lastPaymentChangeAt` to flag a **stale** calculation ("Expenses changed since the last calculation.
Recalculate.").

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as React app
    participant API as SettlementService
    participant L as LedgerService
    participant ENG as SettlementCalculator<br/>(pure Java)
    participant DB as MongoDB

    User->>SPA: Calculate split (button or C)
    SPA->>SPA: show "crunching numbers" (about 1.2 s minimum)
    SPA->>API: POST /api/splits/id/calculate
    API->>DB: load split, check caller is an active member (else 404)
    API->>API: calculatedAt = now (taken before reading the data)
    API->>L: calculateLive(split)
    L->>DB: expenses where deleted = false
    L->>DB: payments of the split
    L->>ENG: calculate(memberIds, expenses, payments)
    Note over ENG: paid(u) = sum of amounts paid by u<br/>share(u) = sum of u's shares<br/>net(u) = paid - share + sent - received<br/>assert: sum of all net = 0
    Note over ENG: greedy settlement: match the largest debtor<br/>with the largest creditor, ties by user id<br/>at most n - 1 transfers
    ENG-->>L: balances + transfers + totals
    L-->>API: SettlementResult
    API->>DB: findAndModify split: $inc settlementVersion (atomic)
    API->>DB: insert settlement snapshot (version, expenseIds,<br/>memberSummaries, transfers)
    API->>DB: update split.lastCalculatedAt
    API->>DB: insert activity SPLIT_CALCULATED
    API-->>SPA: snapshot
    SPA->>SPA: reveal: total, Paid / Their share / Net,<br/>settlement graph, transfers
    SPA-->>User: next: Generate spending report, Record payments

    Note over User,DB: Later: an expense edit or a payment sets lastExpenseChangeAt<br/>or lastPaymentChangeAt after lastCalculatedAt,<br/>so GET /api/splits/id reports calculationStale = true
```

The same formula is used for the live balance on the dashboard (`BalanceQueries`, a `$facet` aggregation), and an integration test
checks the two agree.

---

## 5. Report flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as React app
    participant R as ReportService
    participant AGG as ReportAggregations
    participant DB as MongoDB
    participant PDF as PdfReportRenderer

    User->>SPA: Generate report
    SPA->>R: POST /api/splits/id/reports
    R->>DB: latest settlement (409 if none), expense date range
    R->>AGG: compute(split, start, end)
    AGG->>DB: one $facet pipeline over expenses:<br/>totals, by category ($lookup), daily ($dateToString),<br/>weekday ($dayOfWeek), top 5, paid, shares ($unwind), member x category
    DB-->>AGG: facet result
    AGG-->>R: ReportStats
    R->>R: rule-based insights, plus the change versus the previous split
    R->>DB: insert report
    R->>PDF: render(split, report, settlement, ledger)
    PDF-->>R: PDF bytes (Thymeleaf template, then OpenHTMLtoPDF)
    R->>DB: GridFS store (metadata reportId, splitId)
    R->>DB: update report.pdfFileId, insert activity REPORT_GENERATED
    R-->>SPA: report
    User->>SPA: Download PDF
    SPA->>R: GET /api/reports/id/pdf
    R->>DB: stream the file from GridFS
```

---

## Design decisions

| Decision | Why |
|---|---|
| Money is a `long` of cents everywhere | No floating point near money. Parsing accepts `86,40`, `86.40`, `1.234,50`; display uses exact decimal strings. |
| Shares are computed on the server and stored on the expense | The base `amount / n` goes to everyone and the remainder cents to the first participants by id, so shares always sum to the amount exactly. |
| Embedded members, referenced everything else | A split has a handful of members (read with every request); expenses and snapshots grow, so they are separate collections. |
| No transactions | Works on a standalone `mongod` (no replica set). Each operation is one document, with guarded updates for joins and a `findAndModify` counter for snapshot versions. |
| Soft-deleted expenses | The document stays visible in the database and in the activity log but is excluded from all calculations. |
| Settlement snapshots are immutable | "What did the app say at the time" stays answerable; each carries the exact expense ids it used. |
| 404 for non-members | Split ids cannot be probed. Owner-only actions return 403 to other members. |
| Auth cookie + CSRF | An httpOnly, SameSite=Lax cookie holds the JWT (invisible to scripts); CSRF uses the cookie-to-header pattern. Requests that authenticate with an explicit `Authorization` header skip CSRF, since a browser cannot forge that header. |
| Dates as UTC `LocalDate` | The driver's java.time codecs store `2026-10-12` as midnight UTC, so `$dateToString` and `$dayOfWeek` land on the right day. |
