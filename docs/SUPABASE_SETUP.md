# Supabase one-time setup (development only)

| Field | Value |
|---|---|
| Document | `docs/SUPABASE_SETUP.md` |
| Version | 0.2 (Phase 12 preflight added) |
| Last updated | 2026-11-22 |
| Status | Setup instructions only — hosted verification remains blocked by missing credentials/access |

This document lists the **only** Supabase-side configuration needed. It is infrastructure setup, not application schema work. For the full run, migration, backup/restore, logging, and monitoring procedure, see `docs/DEPLOYMENT.md`; this document stays development-only.

---

## 1. What Supabase is used for — and what it is not

| Used | Not used |
|---|---|
| PostgreSQL server with PostGIS extension | Supabase Auth (backend owns JWT auth) |
| Private Storage bucket via S3-compatible API | PostgREST / auto-generated REST API |
| Flyway migration target | Edge Functions / database webhooks |
| | Supabase Realtime |
| | Supabase-hosted business logic of any kind |

Spring Boot is the only component with credentials and the only place where business rules run. Switching to a local PostgreSQL + MinIO stack later requires changing `.env` only.

---

## 2. One-time project setup

1. Create a Supabase project (free tier is sufficient for development). Note the **project reference** (`<project-ref>`).
2. In **Project Settings → Database**, copy:
   - the **connection string with the direct host** (`db.<project-ref>.supabase.co`, port **5432**) — used by Flyway and the application in Phase 1;
   - the database password for the `postgres` role.
   - Do **not** use the transaction pooler (port 6543) for Flyway DDL. It may be used later for application queries only.
3. In **SQL Editor**, enable the required extensions (this is the only SQL executed manually, and it creates no application tables):

```sql
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create extension if not exists postgis with schema extensions;
create extension if not exists pg_trgm with schema extensions;
select postgis_version();
```

   `V1__enable_extensions.sql` repeats these statements idempotently so a fresh local PostGIS database also works; Supabase's `extensions` schema is handled there.

4. Create a **private** Storage bucket named `civic-images`. Either use the dashboard (Storage → New bucket, public = off) or run:

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'civic-images', 'civic-images', false, 10485760,
  array['image/jpeg','image/png','image/webp']
)
on conflict (id) do nothing;
```

   The bucket is storage configuration, not an application table. All `complaint_images` metadata is created later by Flyway.

5. In **Project Settings → Storage → S3 Access Keys** (or the Supabase dashboard's S3 compatibility section), create an access key/secret pair for the bucket. Store them only in the local `.env`.

6. If the browser will upload directly with presigned PUT URLs later, add a CORS policy for the bucket allowing `PUT`, `GET`, `HEAD` from `http://localhost:5173` (and the production origin once known). Backend-generated presigned URLs use the S3 API and do not require Supabase Auth.

---

## 3. Credentials — local only

Copy `.env.example` to `.env` in the repository root and fill in:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<db-password>

CIVICSENSE_S3_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
CIVICSENSE_S3_BUCKET=civic-images
CIVICSENSE_S3_ACCESS_KEY=<storage-access-key>
CIVICSENSE_S3_SECRET_KEY=<storage-secret-key>
CIVICSENSE_S3_REGION=us-east-1
CIVICSENSE_S3_PATH_STYLE=true
```

Rules:

- `.env` is git-ignored. `.env.example` contains **placeholders only**.
- Never paste credentials into source files, `application.yml`, migrations, docs, or chat.
- Load them into the current PowerShell session with `.\scripts\dev-env.ps1`; no system/user environment variables are modified.
- Rotate the Supabase DB password and storage keys if they are ever exposed.

---

## 4. Verification required in Phase 1

The following must be executed against Supabase and recorded in the Phase 1 report:

1. Flyway baseline migration applied (`flyway_schema_history` contains `V1`).
2. Plain SQL: `SELECT 1 AS ok;` → returns `1`.
3. PostGIS spatial query, e.g.:

```sql
select st_intersects(
  st_setsrid(st_makepoint(77.5946, 12.9716), 4326),
  st_buffer(st_setsrid(st_makepoint(77.5946, 12.9716), 4326), 0.01)::geography
) as within_area;
```

   Expected: `true`. This proves PostGIS is actually installed and spatial functions execute — a successful TCP connection is not sufficient.

4. S3/storage check: `HeadBucket` on `civic-images` via the configured S3 endpoint succeeds.

---

## 5. Migration to local infrastructure later

When Docker/local services are approved:

1. Install PostgreSQL 16 + PostGIS and MinIO locally (outside this phase).
2. Change `SPRING_DATASOURCE_*` and `CIVICSENSE_S3_*` in `.env`.
3. Run the same Flyway migrations against the local database.
4. No Java code, frontend code, AI logic, or routing logic changes.

> Standing decision: Docker remains excluded. Treat this section as a future
> operator option only; no Dockerfile or compose file is provided.

---

## 6. Phase 12 preflight and evidence boundary

Run the credential-safe static check before attempting hosted verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase12-hosted-preflight.ps1
```

It reads the git-ignored `.env` only to determine whether required values are
present, prints **no values**, makes **no network calls**, and verifies the
Flyway source inventory. At Phase 12 completion it reports the hosted database,
S3, JWT and SMTP checks as **BLOCKED** because the local `.env` still contains
placeholders; it also reports missing `psql`/`pg_dump`/`pg_restore`, `aws` and
`redis-cli` operator CLIs. A READY result would only mean the prerequisites are
present — it is not itself hosted verification.

Before any hosted claim, an operator must execute §4 and `docs/DEPLOYMENT.md`,
then record non-secret evidence for:

- `SELECT 1`, `postgis_version()`, and a real `ST_Intersects` jurisdiction
  routing query against the hosted database;
- all **13** Flyway rows V1–V13, including `V8__phase5_image_analysis.sql`
  (the never-created `V8__super_admin_seed.sql` is not a migration row);
- S3 `HeadBucket`, signed PUT/GET and private-bucket access checks;
- hosted health, report/approval, restart and backup/restore checks.

None of these was executed during Phase 12. See `DEVELOPMENT_ROADMAP.md §16.5`
for the exact missing requirements.
