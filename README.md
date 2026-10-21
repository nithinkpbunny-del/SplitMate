# SplitMate

**Split rent, groceries and everything in between. No awkward maths.**

SplitMate is a web app for friends who live together (for example, a flat share abroad) to track shared expenses,
split them fairly and see exactly who owes whom. Create a **split** for the month, send an **invite link**, add
expenses and choose who shares each one, press **Calculate** to see each person's Paid / Their share / Net and the
fewest payments that settle everyone, then generate a **spending report** (in the app and as a PDF).

- **Java 21 + Spring Boot 4** backend with a layered design (controller, service, repository) and a framework-free
  calculation engine. **MongoDB** stores everything (aggregation pipelines for the analysis, GridFS for the PDFs).
- **React + TypeScript + Vite + Tailwind** frontend with a midnight dark theme and a light theme (it follows your system
  setting by default; the toggle in the header, the account menu and the profile page overrides it and is remembered).
- Money is **never** a floating point number: every amount is an integer count of cents.

## Screenshots

Capture these (browser at about 1440px wide, dark theme unless noted) and save them in `docs/screenshots/`:

| File | What to show |
|---|---|
| `01-landing.png` | Landing page with the animated settlement graph |
| `02-register.png` | Register page with the password strength meter |
| `03-dashboard.png` | Dashboard as Aarav: summary strip, split cards, recent activity |
| `04-create-split-share.png` | The share screen after creating a split: invite link, Copy, WhatsApp, QR code |
| `05-join-invite.png` | The public invite page (logged out, in a private window) |
| `06-expenses.png` | Split page, Expenses tab (receipt-style list grouped by day) |
| `07-add-expense.png` | Add expense drawer with the live per-person share preview |
| `08-calculating.png` | The "crunching numbers" moment right after pressing Calculate |
| `09-results.png` | Calculation results: total, Paid / Their share / Net, settlement graph, transfers |
| `10-balances.png` | Balances tab: diverging balance bars, per-person cards, payments |
| `11-report.png` | Report page: KPIs, insights, donut, charts, heat-map |
| `12-report-pdf.png` | The first page of the downloaded PDF |
| `13-mobile-split.png` | Split page at phone width (about 390px) |
| `14-light-theme.png` | Any screen in the light theme |
| `15-mongodb.png` | MongoDB Compass or `mongosh` showing the data (for example `splits` or `fs.files`) |

## Prerequisites

- **Java 21** (`java -version`)
- **Node.js 20 or newer** and npm (`node -v`)
- **MongoDB** reachable at `mongodb://localhost:27017`. Any of these works:
  - a local install (for example `brew install mongodb-community` on macOS),
  - Docker: `docker compose up -d` starts MongoDB and, as a browser viewer, mongo-express on http://localhost:8081
    (login `admin` / `splitmate`), both bound to localhost only,
  - MongoDB Atlas: set `MONGODB_URI` (see below).
- Optional: `mongosh` (to run the queries in `docs/mongodb-queries.md`).

No other installation is needed: the Maven Wrapper (`./mvnw`) downloads Maven itself.

## Run it with the demo data

Two terminals, from the repository root:

```bash
# Terminal 1: backend on http://localhost:8090 (fills MongoDB with demo data on first start)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Terminal 2: frontend on http://localhost:5173 (proxies /api to the backend)
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

### Demo accounts

All accounts use the password **`Demo@1234`**.

| Account | Notes |
|---|---|
| `aarav@demo.com` | Owner of both demo splits. Start here. |
| `isha@demo.com` | Member |
| `rohan@demo.com` | Member |
| `sneha@demo.com` | Member |

The demo data contains an open **October month split** (30 expenses, a calculation, a payment and a report) and a
**September month split** that is already settled. The seeder only runs with the `demo` profile and does nothing if the
demo users already exist.

### Start over: wipe and reseed

```bash
./scripts/reseed-demo.sh              # wipes the database, then starts the backend (demo profile) in the foreground
./scripts/reseed-demo.sh --seed-only  # wipes and seeds, then stops again (about 7 seconds)
./scripts/reseed-demo.sh --yes        # skips the confirmation question
```

**Stop the backend first.** Indexes and the default categories are created when the backend starts, so dropping the
database under a running backend would leave it without them. The script checks the backend port and refuses to run
while a backend answers there. It also asks before deleting, and refuses a non-localhost `MONGODB_URI` unless you pass
`--allow-remote`. It needs `mongosh` and Java 21, and reads `MONGODB_URI` and `PORT` from `.env`.

By hand, the same thing is `mongosh splitmate --eval 'db.dropDatabase()'` (backend stopped) followed by starting the backend
with the demo profile. On Windows, use that route (or run the script from Git Bash or WSL).

## Ports and configuration

| What | Default |
|---|---|
| Backend (Spring Boot) | `8090` (`PORT`; 8080 is often taken, for example by Jenkins) |
| Frontend (Vite dev server) | `5173`, proxies `/api` to `BACKEND_URL` (default `http://localhost:8090`) |
| MongoDB | `mongodb://localhost:27017/splitmate` (`MONGODB_URI`) |

Everything is optional for local development. To override, copy `.env.example` to `.env` in the repository root; both
the backend and the Vite proxy read it. If you change `PORT`, change `BACKEND_URL` to match.

| Variable | Purpose |
|---|---|
| `MONGODB_URI` | MongoDB connection string (local, Docker or Atlas `mongodb+srv://...`) |
| `JWT_SECRET` | Signing secret for login tokens, at least 32 bytes (`openssl rand -base64 48`) |
| `APP_BASE_URL` | Public URL of the frontend, used to build invite links (`/join/{code}`) |
| `COOKIE_SECURE` | Mark the login cookie `Secure` (HTTPS only) |
| `PORT`, `BACKEND_URL` | Backend port, and where the Vite proxy sends `/api` |
| `CORS_ALLOWED_ORIGINS` | Extra origins allowed to call the API (not needed in the normal setups) |

Without `JWT_SECRET` the dev and demo profiles generate a random signing secret on every start (with a loud warning),
which logs everybody out whenever the backend restarts. Set `JWT_SECRET` in `.env` to keep logins across restarts.
On Windows PowerShell, set a variable for one run with `$env:PORT = "8090"` before the command.

## Production build: one jar, one port

```bash
cd backend
./mvnw -Pprod clean package          # builds the React app into the jar (downloads Node on the first run, about 40 s)
export JWT_SECRET=$(openssl rand -base64 48)
java -jar target/splitmate-1.0.0.jar # the whole app on http://localhost:8090
```

The `prod` Maven profile installs Node, runs `npm ci` and the Vite build, and copies `frontend/dist` into the jar's static
resources. Spring serves it, and any non-`/api` path that is not a real file answers with `index.html`, so client-side routes such as
`/app/splits/...` or `/join/...` work on a hard refresh. A jar built with `-Pprod` starts with the **prod profile** by default:

- **`JWT_SECRET` is required** (at least 32 bytes). Without it, startup stops with a short "APPLICATION FAILED TO START" message
  that says what to do; production never generates a secret.
- The login cookie is **`Secure`** (set `COOKIE_SECURE=false` only when serving plain HTTP on a real hostname; Chrome and Firefox
  accept Secure cookies on `http://localhost`, Safari does not).
- Swagger UI and `/api/docs` are off (they answer 404), and the demo seeder does not run.
- Set `MONGODB_URI` and, when it is not `http://localhost:8090`, `APP_BASE_URL` (used to build invite links). The jar reads a `.env`
  file in the directory you start it from, like the dev setup.

## API documentation (Swagger UI)

With the `dev` or `demo` profile the backend serves interactive API docs:

- **http://localhost:8090/swagger-ui.html**: browse and try every endpoint
- http://localhost:8090/api/docs: the OpenAPI 3.1 JSON

To call protected endpoints from Swagger UI: run **GET /api/auth/csrf** once, then **POST /api/auth/login** with a demo account
(`{"email": "aarav@demo.com", "password": "Demo@1234"}`). The login cookie is stored by your browser and Swagger UI adds the CSRF
header to writes automatically, so everything else works. In the `prod` profile Swagger is switched off and these URLs answer 404.
The written reference with real response examples is in [docs/api.md](docs/api.md).

## Project structure

```
splitmate/
├── README.md
├── SPEC.md                       the original build specification
├── docker-compose.yml            optional MongoDB + mongo-express (localhost only)
├── .env.example                  every setting, documented
├── docs/
│   ├── BUILD_LOG.md              what each phase delivered, decisions, results
│   ├── api.md                    every endpoint with request and response examples
│   ├── architecture.md           Mermaid diagrams: system, collections, invite flow, calculate flow
│   ├── mongodb-queries.md        mongosh queries for the live demo
│   └── demo-script.md            a timed 7-minute walkthrough
├── scripts/
│   └── reseed-demo.sh            wipe the database and reseed the demo data
├── backend/                      Spring Boot 4, Java 21, Maven (use ./mvnw)
│   └── src/main/java/com/splitmate/
│       ├── auth/                 register, login, JWT cookie, CSRF, login rate limit
│       ├── user/                 profile, password change
│       ├── split/                splits, embedded members, invite links, join/leave
│       ├── expense/              expenses, server-side shares, filters, soft delete
│       ├── category/             default categories (seeded)
│       ├── settlement/           the calculation engine (pure Java) and snapshots
│       ├── payment/              "I paid you back" records
│       ├── report/               aggregation pipelines, insights, PDF, GridFS, CSV
│       ├── activity/             the per-split timeline
│       ├── seed/                 the demo data seeder (profile demo only)
│       ├── config/               security, CORS, MongoDB, web configuration
│       └── common/               Money, errors, id helpers, security helpers
└── frontend/                     React + TypeScript + Vite + Tailwind
    └── src/
        ├── api/                  typed client and one module per resource
        ├── components/           shared components (MoneyText, SettlementGraph, ...)
        ├── features/             auth, dashboard, splits, expenses, settlement, reports, ...
        ├── hooks/, lib/          theme, hotkeys, money and date helpers
        └── index.css             design tokens (dark and light)
```

## Tests

```bash
cd backend
./mvnw test                  # unit tests: calculation engine, money parsing, auth, insights
./mvnw -Pintegration test    # everything, including end-to-end tests against a local MongoDB
```

The integration tests use a separate database (`splitmate_test`) and need MongoDB running; Docker is not required.
The frontend checks are `npm run build` (type-checks and builds) and `npm run lint`, run in `frontend/`.

## Documentation

- [docs/mongodb-queries.md](docs/mongodb-queries.md): queries to show that every action is stored in MongoDB
- [docs/architecture.md](docs/architecture.md): Mermaid diagrams of the system, the collections, the invite flow and the calculate flow
- [docs/demo-script.md](docs/demo-script.md): a timed walkthrough for a live demo
- [docs/BUILD_LOG.md](docs/BUILD_LOG.md): what was built, decisions made and verification results
- [docs/api.md](docs/api.md): every endpoint with a request and a real response example
