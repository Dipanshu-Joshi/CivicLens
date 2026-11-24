# CivicLens

CivicLens (CivicSense) is a role-based civic issue reporting and management platform. Citizens can submit issues with photos, description, and location; the backend handles AI-assisted analysis, deterministic routing, complaint lifecycle management, notifications, and admin governance.

> Status: Phase 12 hardening is complete locally. Hosted Supabase/PostGIS, production storage, email delivery, and deployment verification remain blocked by missing operator credentials/access. See [`DEVELOPMENT_ROADMAP.md`](./DEVELOPMENT_ROADMAP.md) and [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md).

## Features

- **Citizen**: register/login, report issues, track complaints, comment, reopen, and submit feedback.
- **Department Admin**: manage department queue, accept/reject/request info, update status, attach evidence, manage profile/capabilities/service areas.
- **Super Admin**: verify departments, manage users, review global complaints, inspect AI/routing decisions, override classification/reroute, and view audit logs.
- **Platform**: JWT auth with refresh rotation, RBAC and ownership checks, RFC 7807 errors, Flyway migrations, S3-compatible storage abstraction, AI provider abstraction, PostGIS-ready routing, outbox-based notifications.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.2.0, Maven, Spring Security, JPA, Flyway, Springdoc OpenAPI |
| Frontend | React 19, TypeScript, Vite 8, Tailwind CSS 4, TanStack Query, React Router, Vitest, Playwright |
| Data/Infra (supported dev path) | PostgreSQL + PostGIS (Supabase hosted), S3-compatible object storage (Supabase Storage), JWT auth in Spring Boot |
| Testing | JUnit 5, Mockito, H2 test double for backend integration tests; Vitest/RTL/MSW and Playwright for frontend |

## Repository Structure

```text
.
├── backend/                 # Spring Boot API, Flyway migrations, backend tests
├── frontend/                # React SPA, unit tests, Playwright e2e
├── docs/                    # Deployment, setup, and phase contracts
├── scripts/                 # Local dev, smoke, audit, backup/restore helpers
├── .env.example             # Placeholder-only environment template
├── ARCHITECTURE.md
├── API_DOCUMENTATION.md
├── DATABASE_DESIGN.md
└── DEVELOPMENT_ROADMAP.md
```

## Local Development

The supported development path is local Java/Maven + project-local Node, with hosted Supabase for Postgres/PostGIS and S3-compatible storage. **No Docker is required or provided.**

### Prerequisites

- Git
- JDK 21
- Maven 3.9+
- Portable Node installed under `tools/node` (loaded by `scripts/dev-env.ps1`)
- Supabase project configured for direct Postgres access and private S3-compatible storage

### 1. Configure environment

```powershell
cd E:\CivicSense
Copy-Item .env.example .env
# Edit .env with operator-supplied values. Do not commit .env.
```

### 2. Run backend

```powershell
# Terminal 1
cd E:\CivicSense
.\scripts\run-backend.ps1
```

Backend default: `http://localhost:8080/api/v1`

### 3. Run frontend

```powershell
# Terminal 2
cd E:\CivicSense
.\scripts\run-frontend.ps1
```

Frontend default: `http://localhost:5173`

## Useful Commands

### Backend

```powershell
cd E:\CivicSense\backend
mvn -B test
mvn -B package
```

### Frontend

```powershell
cd E:\CivicSense\frontend
npm run dev
npm run test
npm run lint
npm run build
npm run test:e2e
```

### Checks / Helpers

```powershell
cd E:\CivicSense
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\secret-scan.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dependency-audit.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-local-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-hosted-preflight.ps1
```

## Environment & Secrets

- `.env` is git-ignored; keep credentials local only.
- `.env.example` is the tracked placeholder template.
- Do not use Supabase Auth/PostgREST/Realtime for application business logic; Spring Boot owns auth and business rules.
- For Supabase setup details, see [`docs/SUPABASE_SETUP.md`](./docs/SUPABASE_SETUP.md).

## Documentation

- [Architecture](./ARCHITECTURE.md)
- [API Documentation](./API_DOCUMENTATION.md)
- [Database Design](./DATABASE_DESIGN.md)
- [Development Roadmap](./DEVELOPMENT_ROADMAP.md)
- [Deployment Guide](./docs/DEPLOYMENT.md)
- [Supabase Setup](./docs/SUPABASE_SETUP.md)

## Verification Notes

- Local automated tests and smoke checks have been run in the project history; see roadmap/deployment docs for evidence and boundaries.
- Hosted verification (real Supabase/PostGIS, real storage, SMTP, production deployment) is not claimed as complete until the Phase 1 §1.1 checklist is executed with operator credentials.