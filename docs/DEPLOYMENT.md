# CivicSense deployment and operations guide

| Field | Value |
|---|---|
| Document | `docs/DEPLOYMENT.md` |
| Version | 0.2 (Phase 12 hardening added) |
| Last updated | 2026-11-22 |
| Status | Supported-development-path documentation; Phase 12 complete locally, hosted verification blocked |

This is the single entry point for running, migrating, backing up, restoring,
and observing CivicSense. It consolidates the deployment-support items in
`DEVELOPMENT_ROADMAP.md §3` (Phase 11): `.env.example`, migration/backup/restore
docs, storage setup, logging/monitoring, and local run instructions.

---

## 1. Supported path and decision record

| Item | Decision |
|---|---|
| Runtime | Local Java 21 + Maven for the backend; project-local portable Node under `tools/node` for the frontend/Vite. |
| Database | Hosted Supabase PostgreSQL with PostGIS. Application tables are created **only** by Flyway. |
| Object storage | Supabase Storage (`civic-images` private bucket) through the S3-compatible API. |
| Authentication | Entirely owned by Spring Boot (JWT + rotating refresh cookie). Supabase Auth is **not** used. |
| Containers | **No Docker.** Production Dockerfiles/compose are deferred until Docker is reintroduced by an explicit owner decision (`DEVELOPMENT_ROADMAP.md §2`). No Dockerfile or compose file exists in this repository. |
| Environment | Secrets live only in the git-ignored root `.env`, loaded process-scoped by `scripts/dev-env.ps1`. `.env.example` is the tracked placeholder-only template. |
| Provider portability | JDBC/Flyway, AWS SDK S3 v2, and provider-independent AI/geocoding clients mean a later move to local PostgreSQL + MinIO or another S3 provider is a configuration change, not a code change. |

No production hosting account, domain, TLS terminator, reverse proxy, or
monitoring service is created or configured by this phase. The instructions
below describe the supported **development** path and the operator procedure
for a future hosted deployment; they do not claim a production deployment has
been performed.

---

## 2. Prerequisites

| Requirement | Notes |
|---|---|
| Git | Any recent version; clone from the owner's local repository. There is no configured remote or push target. |
| JDK 21 | Verified locally as Java 21.0.9 LTS. No global install is added by these scripts. |
| Maven 3.9+ | Verified with `C:\maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd`. `backend/.mvn/maven.config` pins the project-local repository to `E:/CivicSense/tools/maven-repo`, so no global dependency install is required. |
| Portable Node | `tools/node/node-v26.13.1-win-x64`. `scripts/dev-env.ps1` prepends it to PATH for the current PowerShell process only. |
| Supabase project | Direct Postgres connection (port 5432), PostGIS enabled, and a private Storage bucket. See `docs/SUPABASE_SETUP.md` for one-time setup. |
| Operator secrets | Database password, S3 access key/secret, JWT secret, and optional AI/geocoding keys — supplied only through `.env`. |
| Microsoft Edge | Only required for `npm run test:e2e`, which uses the local `msedge` channel with deterministic API stubs. |

---

## 3. Fresh clone → running stack

All commands run in PowerShell from the repository root. Use **two terminals**
for a local-process stack: one for the backend (:8080) and one for the frontend
(:5173). Environment loading is process-scoped, so load `.env` in each terminal
that needs it.

### 3.1 One-time setup

```powershell
cd E:\CivicSense

# 1. Create the local environment file (never commit it).
copy .env.example .env
#    Edit .env: replace every <placeholder> with operator-supplied values.
#    Keep SPRING_DATASOURCE_URL on the DIRECT port 5432 (never 6543 for DDL).

# 2. Load secrets and portable Node into THIS terminal only.
.\scripts\dev-env.ps1
```

`dev-env.ps1` prints key names with masked values and never echoes secrets. If
`.env` is absent it warns and continues; the backend will then fail on the
required datasource properties, which is expected.

### 3.2 Backend (applies Flyway migrations on startup)

```powershell
# Terminal 1 — from the repository root
cd E:\CivicSense
.\scripts\run-backend.ps1
```

`run-backend.ps1` dot-sources `dev-env.ps1`, changes to `backend\`, and runs
`mvn -B spring-boot:run`. At startup Flyway applies all pending migrations from
`classpath:db/migration` before the web server accepts traffic. Hibernate runs
with `ddl-auto=validate`, so a schema that does not match the entities fails
fast rather than being silently altered.

Expected startup markers: `Migrating schema ... to version ...`, `Successfully
applied ... migrations`, and `Started CivicSenseApplication`. If the database is
unreachable, the application fails to start — this is the intended gate; there
is no offline fallback in the `dev` profile.

### 3.3 Frontend

```powershell
# Terminal 2 — from the repository root
cd E:\CivicSense
.\scripts\run-frontend.ps1
```

This loads `.env` and portable Node, then runs `npm run dev` from `frontend\`.
The SPA is served at `http://localhost:5173` and calls
`VITE_API_BASE_URL` (default `http://localhost:8080/api/v1`).

### 3.4 Health verification

```powershell
# Hosted path — all three checks must read UP for Phase 1 §1.1 to close.
Invoke-RestMethod http://localhost:8080/api/v1/meta/health
Invoke-RestMethod http://localhost:8080/api/v1/actuator/health
Invoke-WebRequest http://localhost:5173/ | Select-Object -ExpandProperty StatusCode
```

`GET /api/v1/meta/health` returns
`{ status, version, time, database, postgis, objectStorage }` and HTTP 200 only
when all three dependencies are UP; otherwise it returns HTTP 503 with the same
shape. `GET /api/v1/actuator/health` exposes `health,info` with details shown.
A bare TCP connection is not sufficient: Phase 1 §1.1 also requires a real
`SELECT 1`, a real PostGIS `ST_Intersects` query, and an S3 `HeadBucket`.

> **Current evidence:** the root `.env` contains placeholders, so the hosted
> path has not been executed. Section 9 records the local smoke check that was
> executed instead and states exactly what it proves.

### 3.5 Other frontend commands

```powershell
cd E:\CivicSense\frontend
npm run test        # Vitest unit/component tests
npm run lint        # oxlint
npm run build       # tsc -b && vite build -> dist/
npm run preview     # serve the production build locally
npm run test:e2e    # Playwright, local Edge, stubbed APIs
```

---

## 4. Migrations (Flyway V1–V13)

| Rule | Detail |
|---|---|
| Source of truth | `backend/src/main/resources/db/migration/`. `flyway_schema_history` records what has actually been applied. |
| Inventory | V1 extensions; V2 identity/departments; V3 complaints; V4 collaboration; V5 governance; V6 indexes/constraints (RLS scaffolding commented out); V7 reference seeds; **V8 phase-5 image analysis**; V9 routing; V10 department dashboard; V11 admin governance; V12 notification delivery; V13 password reset. The never-created `V8__super_admin_seed.sql` is not a migration row. See `DATABASE_DESIGN.md §6`. |
| Forward-only | Migrations are never edited after being applied. Rollback SQL is documented in each migration's comment header for review, but Flyway does not auto-rollback. To undo a release, restore the last known-good backup (§6). |
| Connection | Direct PostgreSQL port **5432**. The Supabase transaction pooler on 6543 must not be used for DDL. |
| Schema ownership | `ddl-auto=validate`. Never create application tables manually in the Supabase dashboard. |
| Supabase specifics | Extensions are created in the `extensions` schema and migrations set `search_path = public, extensions`. No Supabase Auth/PostgREST/Realtime is used. |
| Test double | Automated tests use H2 `schema-test.sql` with Flyway disabled. It omits geometry/jsonb/inet columns, triggers, and function-based/partial indexes; it does **not** prove PostGIS behavior. |

Operator verification after startup:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
-- Expect 13 successful rows: V1-V13, including V8__phase5_image_analysis.sql.
SELECT 1 AS ok;
SELECT postgis_version();
SELECT ST_Intersects(
  ST_SetSRID(ST_MakePoint(77.5946, 12.9716), 4326),
  ST_Buffer(ST_SetSRID(ST_MakePoint(77.5946, 12.9716), 4326), 0.01)::geography
) AS within_area;  -- expect true
```

---

## 5. Storage setup

One-time setup is specified in `docs/SUPABASE_SETUP.md`:

1. Enable the `postgis`, `pgcrypto`, and `pg_trgm` extensions (V1 also declares them).
2. Create the **private** bucket `civic-images` with the intended MIME/size limits.
3. Create S3 access keys and put them only in `.env` (`CIVICSENSE_S3_*`).
4. Add bucket CORS allowing `PUT`, `GET`, `HEAD` from `http://localhost:5173`
   and, when known, the production origin.
5. Verify with `HeadBucket` and the health endpoint; backend presigned URLs do
   not use Supabase Auth.

Production bucket name, origin, and any paid geocoding provider are
operator-supplied values. The current configuration reads the S3 endpoint,
bucket, keys, region, and path-style flag from `.env`; AI and geocoding
provider keys in `.env.example` are retained for portability but are not read
by the current Spring Boot configuration.

---

## 6. Backup and restore

### 6.1 Scope

A complete backup has **two independent parts**. Backing up only the database
loses complaint images; backing up only the bucket loses all metadata, audit
history, and outbox state.

| Part | Contains |
|---|---|
| PostgreSQL/PostGIS | All Flyway-managed tables including append-only `audit_logs`, AI history, routing decisions, outbox events, and `flyway_schema_history`. |
| `civic-images` bucket | Original and thumbnail image objects referenced by `complaint_images.storage_key`. |

### 6.2 Preferred: Supabase platform backups

Confirm the retention window and point-in-time-recovery capability available on
the project's current plan in the Supabase dashboard (**Project Settings →
Backups**). Do not assume a retention period; record it as an operator
responsibility. Platform backups do not replace an export of the S3 bucket.

### 6.3 Portable operator procedure (run from a trusted local shell)

Credentials are read from `.env` in the current PowerShell session via
`.\scripts\dev-env.ps1`; never paste them into commands, docs, or source.

```powershell
# Backup — database (custom-format dump, direct port 5432)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
pg_dump --host=db.<project-ref>.supabase.co --port=5432 --username=postgres `
        --dbname=postgres --format=custom --no-owner --no-privileges `
        --file="civicsense-db-$stamp.dump"

# Backup — Storage bucket objects
aws --endpoint-url https://<project-ref>.storage.supabase.co/storage/v1/s3 `
    s3 sync s3://civic-images "civic-images-$stamp/" --no-progress
```

`pg_dump` does not create extensions. When restoring into a **brand-new**
database, first apply `V1__enable_extensions.sql`'s `CREATE EXTENSION`
statements (in the `extensions` schema on Supabase), then restore the dump.

```powershell
# Restore — ALWAYS into a disposable project/schema first, never straight over live data.
$env:PGPASSWORD = '<target-database-password-from-.env-only>'
pg_restore --clean --if-exists --no-owner --no-privileges `
           --dbname="<target-direct-connection-url>" "civicsense-db-$stamp.dump"

# Restore — objects into a separate disposable bucket for verification.
aws --endpoint-url https://<project-ref>.storage.supabase.co/storage/v1/s3 `
    s3 sync "civic-images-$stamp/" s3://civic-images-restore-test/ --no-progress
```

Verification on the disposable target:

1. `SELECT count(*) FROM flyway_schema_history;` → 13 successful rows (V1–V13).
2. Re-run the plain SQL, PostGIS, and S3 checks in §3.4 / §4.
3. Compare a few known row counts and image object keys with the source.
4. Boot the application against the disposable target with `run-backend.ps1`
   and confirm `/api/v1/meta/health` is `UP`.

Only after all checks pass should a restore be considered for the live target.
Store dumps off-site, encrypt them at rest, rotate them on a defined schedule,
and never commit them to the repository. Enabling `CIVICSENSE_RETENTION_ENABLED`
is only safe after a verified backup exists, because retention deletion is
permanent.

### 6.4 Local dry run (executed evidence)

`scripts/backup-restore-dry-run.ps1` exercises the documented step order with
the H2 test double already present under `tools/maven-repo`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-restore-dry-run.ps1
```

It applies `schema-test.sql`, inserts a deterministic fixture, captures a
snapshot, writes a zip archive, deletes the live database file, restores into a
fresh disposable database, and compares the snapshots. It proves ordering and
repeatability of the **procedure shape only**. It does not test Supabase,
PostgreSQL/PostGIS, `pg_dump`/`pg_restore`, or the S3 bucket. Results are in
§9.

---

## 7. Logging and monitoring

| Area | Current state |
|---|---|
| Application logs | Boot 4.2 built-in ECS structured JSON console logging is enabled with `logging.structured.format.console: ecs` (`json` is not a valid Boot 4.2 format value). Log levels remain configured in `application.yml`; no custom `logback.xml` is required. |
| Sensitive data | Never log tokens, passwords, raw reset tokens, secrets, PII, raw image bytes, or full citizen coordinates. Treat console output as sensitive because health failures can include exception details. |
| Correlation | `CorrelationIdFilter` generates or echoes `X-Correlation-Id` at the request boundary, exposes it via CORS, and populates MDC. It is propagated into async tasks, scheduled cleanup, RFC 7807 error bodies, health responses and audit rows. |
| Health | `/api/v1/meta/health` (UP/DOWN with per-dependency detail) and `/api/v1/actuator/health` (health,info exposed, details always shown). These are the endpoints an operator or uptime probe should poll. |
| Aggregation and alerting | **Not implemented by the application.** Capture JSON stdout with the host's log driver or a log shipper, and wire health probes into the operator's alerting system. |

Never expose Actuator beyond the trusted network without authentication. The
health detail includes infrastructure failure information.

---

## 8. Security and configuration notes

- Secrets are environment-only. `.env` is git-ignored; `.env.example` contains
  placeholders only. Run `scripts/secret-scan.ps1` before any commit.
- Set `CIVICSENSE_REFRESH_COOKIE_SECURE=true` and a real `CIVICSENSE_CSP` with
  the deployed origin whenever TLS is used. Update
  `CIVICSENSE_ALLOWED_ORIGINS`, `CIVICSENSE_RESET_URL`, and the bucket CORS list
  together.
- API rate limiting is in-memory and single-instance. Redis/shared state is a
  prerequisite for horizontal scale-out; no Redis dependency or service was
  added in Phase 12.
- RLS policy scaffolding is disabled; application-layer RBAC, ownership checks,
  and department-scoped repository queries are the only enforced isolation
  boundary. Enabling RLS requires a least-privilege DB role and a real
  PostgreSQL integration matrix first.
- New/reset passwords use prefixed Argon2id; legacy BCrypt hashes verify and
  transparently upgrade after successful login. The provider is Bouncy Castle
  1.85. No schema migration was required.
- SMTP password-reset delivery is disabled unless `CIVICSENSE_MAIL_ENABLED=true`
  and all SMTP values are supplied. When disabled, no delivery is claimed;
  push remains unavailable.
- Retention cleanup is disabled by default (`CIVICSENSE_RETENTION_ENABLED=false`,
  `CIVICSENSE_RETENTION_CRON=-`). It deletes only rows older than the configured
  cutoff from `audit_logs`, `ai_analyses`, and `ai_analysis_runs`, with no API.
- Re-run `scripts/dependency-audit.ps1` when dependencies change; the last
  recorded result was zero npm vulnerabilities and zero OSV Maven advisories.
- Phase 1 §1.1 remains open until the hosted verification checklist in
  `DEVELOPMENT_ROADMAP.md` is executed and approved.

---

## 9. Verification evidence and limitations

Executed on 2026-11-22 from `E:\CivicSense` with Java 21.0.9 LTS, Maven 3.9.16,
and portable Node 26.13.1.

| Check | Executed result |
|---|---|
| Backup/restore local dry run | **Passed** with identical snapshots `users=1|audit_logs=1|settings=1|email=dryrun@example.test`. H2 2.5.252 from `tools/maven-repo`; schema + fixture survived dump → file loss → restore into a disposable database. Hosted PostgreSQL/S3 was **not** exercised. |
| Local-process smoke/restart | **Passed** using `scripts/phase12-local-smoke.ps1`: packaged backend, actuator, built SPA root and restart recovery were reached with `X-Correlation-Id` present; child processes were stopped. H2 test double only. |
| Hosted preflight | **Executed, hosted items BLOCKED.** `scripts/phase12-hosted-preflight.ps1` named the exact missing env keys/operator CLIs, printed no values and made no network calls. Flyway source inventory READY: 13 files V1–V13. |
| Backend `mvn -B test` / `mvn -B package` | **149/149 tests passed** (0 failures, 0 errors, 0 skips); package BUILD SUCCESS with Bouncy Castle 1.85. |
| Frontend test / lint / build / E2E | **39/39 tests in 6 files; lint exit 0; build success; 5/5 Playwright tests passed** with local Edge and stubbed APIs. |
| Dependency audit | `found 0 vulnerabilities` (npm); `No OSV advisories reported for resolved Maven dependencies`; `Dependency audit passed` after upgrading Bouncy Castle 1.84 → 1.85. |
| Secret scan | 307 tracked files scanned; no credential-like patterns or accidental secret files. |
| `git diff --check` | Clean at the final pre-commit review (no whitespace errors). |

### 9.1 Local-process smoke command and responses

Run from `backend\` (the H2 driver and schema come from test scope):

```powershell
mvn -B spring-boot:run -Dspring-boot.run.profiles=test `
    -Dspring-boot.run.useTestClasspath=true `
    '-Dspring-boot.run.jvmArguments=-Dspring.sql.init.schema-locations=file:E:/CivicSense/backend/src/test/resources/schema-test.sql'
```

Observed responses:

```text
GET /api/v1/meta/health      -> HTTP 503
{"status":"DOWN","version":"test","time":"2026-11-21T19:11:31.458832800Z",
 "database":"UP","postgis":"DOWN","objectStorage":"DOWN"}

GET /api/v1/actuator/health  -> HTTP 503
db UP, diskSpace UP, livenessState UP, postgis/objectStorage DOWN

GET http://localhost:5173/   -> HTTP 200 (Vite-served SPA)
```

HTTP 503 is the **expected** result under the test profile: `database=UP` comes
from H2, while PostGIS and object storage are unavailable by design (Flyway is
disabled and the S3 client points at a non-existent local endpoint). This proves
local process startup, routing, and SPA serving only. It is not the hosted
health gate.

### 9.2 Repeatable Phase 12 helpers

Two repository scripts make the local/hosted boundary explicit:

```powershell
# Packaged-jar + built-SPA smoke and restart check (no Docker, no network providers).
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-local-smoke.ps1

# Credential-safe static hosted readiness report (no values printed, no network calls).
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-hosted-preflight.ps1
```

The smoke helper injects the test-scope H2 jar into the packaged jar with
Spring Boot's `PropertiesLauncher`; it checks `/api/v1/meta/health`,
`/api/v1/actuator/health`, the built SPA document and restart recovery, then
stops all child processes. The preflight helper reports exact missing keys and
CLIs and validates the Flyway source inventory. Neither script constitutes
hosted verification.

### Known limitations

- Hosted Supabase/PostGIS, real Flyway V1–V13 on PostgreSQL, production object
  storage, real AI/geocoding providers and real SMTP delivery have not been
  exercised. The Phase 12 SMTP channel is implemented but provider-gated and
  unconfigured; push remains unavailable. Phase 1 §1.1 remains open.
- The E2E suite uses local Microsoft Edge with deterministic API stubs, not
  hosted services.
- No Docker image, compose file, reverse proxy, TLS configuration, or
  monitoring stack is provided.
- `docs/SUPABASE_SETUP.md` remains explicitly development-only; production
  bucket/origin values are operator-supplied.
