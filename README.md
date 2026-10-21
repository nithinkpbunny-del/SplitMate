# SplitMate

Split rent, groceries and everything in between. No awkward maths.

SplitMate is a web app for flatmates to track shared expenses, split them fairly and see who owes whom.
Java (Spring Boot) backend, React frontend, MongoDB database.

> This README is filled in during the build. See `docs/BUILD_LOG.md` for progress.

## Quick start (development)

```bash
# 1. MongoDB running on mongodb://localhost:27017 (local install or `docker compose up -d`)
# 2. Backend
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# 3. Frontend (second terminal)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173.
