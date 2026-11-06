# Watchmark

A TV series tracker: discover shows, build a personal library and track every
episode you watch. *Watchmark* is a provisional working name.

> Personal, non-commercial project. Show metadata will come from TMDB under its
> non-commercial API terms — see [docs/tmdb.md](docs/tmdb.md).

**Status:** Milestone 9 (hardening). See [docs/roadmap.md](docs/roadmap.md).

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite 8, React Router 8, TanStack Query 5, React Hook Form + Zod, Tailwind CSS 4, Lucide |
| Backend | Java 25 (LTS), Spring Boot 4.1, Spring Web MVC, Spring Security 7, Spring Session JDBC, Spring Data JPA, Bean Validation, Flyway |
| Database | MySQL 8.4 LTS |
| Tests | JUnit 5, Testcontainers (real MySQL), Vitest, React Testing Library, Playwright + axe-core |
| CI | GitHub Actions |

## Prerequisites

- JDK 25, Node.js 24+, Docker Desktop (with Compose v2).
- Maven is **not** required: use the bundled wrapper `backend/mvnw`.

## Run locally

```sh
# 1. Configuration (once). Edit the passwords in .env; never commit it.
cp .env.example .env

# 2. Database — MySQL 8.4 on 127.0.0.1:3307
docker compose up -d

# 3. Backend — http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows PowerShell: .\mvnw.cmd spring-boot:run

# 4. Frontend — http://localhost:5173 (in a second terminal)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and create an account. Sessions are stored in MySQL,
so restarting the backend does not sign you out.

Without `TMDB_READ_ACCESS_TOKEN` the app runs on **mock data**: a handful of
fictional shows, clearly labelled in the UI. To use real data, get a token as
described in [docs/tmdb.md](docs/tmdb.md) and put it in `.env`.

Optional: set `API_DOCS_ENABLED=true` in `.env` for the OpenAPI document at
`/v3/api-docs` and Swagger UI at `/swagger-ui.html` on the backend port.

**Port 8080 already taken** (for example by another project's container)? Run
the backend on another port and point Vite at it:

```sh
SERVER_PORT=8081 ./mvnw spring-boot:run                    # backend
WATCHMARK_BACKEND_URL=http://localhost:8081 npm run dev    # frontend
```

## Tests

```sh
cd backend  && ./mvnw verify    # needs Docker running (Testcontainers)
cd frontend && npm test && npm run lint && npm run typecheck
```

Browser end-to-end and accessibility tests (Playwright, Chromium) run against
the real backend with the mock catalogue and the production frontend build.
They need the Compose database running (`docker compose up -d`); Playwright
starts the backend on port 8081 and the frontend preview on 5173 itself (or
reuses ones already running there):

```sh
cd frontend
npx playwright install chromium   # once
npm run e2e
```

They cover registering, adding a show, marking an episode watched, undo, the
dashboard, history and statistics; axe-core WCAG 2.2 A/AA checks on every page
in light and dark mode; and no sideways scrolling at 375 px. Automated
accessibility checks find only part of the problems.

## Repository layout

```
Watchmark/
├── backend/            Spring Boot API (Maven)
│   └── src/main/resources/db/migration/   Flyway migrations (MySQL)
├── frontend/           React SPA (Vite)
├── docs/               Architecture, data model, API, rules, roadmap
├── docker-compose.yml  Local MySQL
├── .env.example        Template for local secrets (.env is git-ignored)
└── .github/            CI workflow and Dependabot
```

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Set DB_PASSWORD in .env` from compose | `.env` missing — copy `.env.example`. |
| `Communications link failure` on backend start | MySQL not running or not healthy yet: `docker compose ps`. |
| `Access denied for user 'watchmark'` | `.env` passwords changed after the volume was created. MySQL only reads them on first start; reset with `docker compose down -v` (**deletes local data**). |
| Port 3307 in use | Set another `DB_PORT` in `.env`. |
| Backend tests fail with `Could not find a valid Docker environment` | Start Docker Desktop. |
| Frontend shows "Watchmark is unavailable right now" | Backend not running on :8080 (or on the port in `WATCHMARK_BACKEND_URL`). |
| `Web server failed to start. Port 8080 was already in use` | Another process owns 8080; use `SERVER_PORT` as shown above. |
| `watchmark.metadata.provider=tmdb but TMDB_READ_ACCESS_TOKEN is not set` | Add the token to `.env`, or use `METADATA_PROVIDER=auto` locally. |
| Shows load but the banner says "Mock data" | No TMDB token configured; see [docs/tmdb.md](docs/tmdb.md). |
| `403 Invalid CSRF token` when calling the API by hand | Call `GET /api/v1/auth/csrf` first and send the `XSRF-TOKEN` cookie value as `X-XSRF-TOKEN`. |
| Signed out immediately in production | `SESSION_COOKIE_SECURE=true` requires HTTPS; over plain HTTP the browser drops the cookie. |

## Documentation

- [Architecture](docs/architecture.md)
- [Data model and ER diagram](docs/data-model.md)
- [API contract](docs/api.md)
- [Tracking rules](docs/tracking-rules.md)
- [Security](docs/security.md)
- [TMDB usage and attribution](docs/tmdb.md)
- [Roadmap](docs/roadmap.md)
