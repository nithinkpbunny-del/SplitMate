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
