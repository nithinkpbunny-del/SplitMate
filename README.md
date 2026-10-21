# SplitMate

Split rent, groceries and everything in between. No awkward maths.

SplitMate is a web app for flatmates to track shared expenses, split them fairly and see who owes whom.
Java 21 + Spring Boot 4 backend, React + TypeScript frontend, MongoDB database. Money is always stored as
integer cents.

> Work in progress. `docs/BUILD_LOG.md` records what is built and what is still to do.

## Prerequisites

- Java 21, Node 20+ and npm
- MongoDB reachable at `mongodb://localhost:27017` (a local install, or `docker compose up -d` which also
  starts mongo-express on http://localhost:8081, login `admin` / `splitmate`)

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

Open http://localhost:5173 and log in with **aarav@demo.com** / **Demo@1234**. The other demo accounts are
`isha@demo.com`, `rohan@demo.com` and `sneha@demo.com`, all with the same password.

The demo seeder only runs with the `demo` profile and does nothing if the demo users already exist. To start
over: `mongosh splitmate --eval 'db.dropDatabase()'`, then restart the backend.

## Ports and configuration

| What | Default |
|---|---|
| Backend (Spring Boot) | `8090` (`PORT`; 8080 is commonly taken, for example by Jenkins) |
| Frontend (Vite dev server) | `5173`, proxies `/api` to `BACKEND_URL` (default `http://localhost:8090`) |
| MongoDB | `mongodb://localhost:27017/splitmate` (`MONGODB_URI`) |

Everything is optional for local development. To override, copy `.env.example` to `.env` in the repository
root; both the backend and the Vite proxy read it. If you change `PORT`, change `BACKEND_URL` to match.

Without `JWT_SECRET` the dev and demo profiles generate a random signing secret on every start (with a
warning), which logs everybody out when the backend restarts. Set `JWT_SECRET` in `.env` to keep logins.

## Tests

```bash
cd backend
./mvnw test                  # unit tests (engine, money, auth)
./mvnw -Pintegration test    # also the end-to-end tests against local MongoDB (database splitmate_test)
```
