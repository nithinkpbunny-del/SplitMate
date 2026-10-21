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
