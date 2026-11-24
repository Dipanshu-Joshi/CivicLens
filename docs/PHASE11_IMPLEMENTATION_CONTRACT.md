# Phase 11 implementation contract (approved)

All paths are relative to the repository root unless stated otherwise. Phase 10
was approved and checkpointed locally as commit `d49e9a8` / tag
`phase10-approved`. All earlier approved tags through `phase9-approved` are
preserved. This document defines the historical Phase 11 boundary before
implementation files were changed. Its statement that Phase 12 does not exist
was true at the Phase 11 gate and is now **superseded**: the owner subsequently
authorized Phase 12 as the final planned phase, which was implemented and
checkpointed as `phase12-approved`. See `DEVELOPMENT_ROADMAP.md §16` and
`docs/PHASE12_IMPLEMENTATION_CONTRACT.md §11`.

## 1. Exact roadmap scope

`DEVELOPMENT_ROADMAP.md:241-247` is the only authoritative Phase 11 definition.

**Title:** Phase 11 — Deployment

**Scope (verbatim intent):**
- Production Dockerfiles/compose **only when Docker is reintroduced by an
  explicit decision**.
- `.env.example`.
- Migration documentation.
- Backup/restore documentation.
- Storage setup.
- Logging/monitoring.
- Local run instructions.
- Until Docker is reintroduced, document the current **Supabase + local-process
  setup as the supported development path**.

**Exit criteria:**
1. Fresh clone → documented commands → full stack running with migrations
   applied.
2. Backup/restore procedure documented and tested once.

The dependency graph (`DEVELOPMENT_ROADMAP.md:267`) requires Phase 10 before
Phase 11; that gate is satisfied by the owner approval and checkpoint above.

## 2. Boundaries and standing decisions

- **No Docker.** The roadmap makes Dockerfiles/compose conditional on an
  explicit decision. No such decision is recorded; the standing owner decision
  remains no Docker Desktop / no Docker configuration
  (`DEVELOPMENT_ROADMAP.md:53-72`, `ARCHITECTURE.md:29-53`). Phase 11 will
  therefore document the no-Docker, Supabase + local-process path and will not
  create Dockerfiles or compose files.
- **No production infrastructure changes.** This phase documents deployment
  support; it does not create a hosting account, domain, TLS configuration,
  reverse proxy, or monitoring service.
- **No external-integration claim.** Hosted Supabase/PostGIS, real Flyway
  V1–V13 execution on PostgreSQL, production object storage, real AI/geocoding
  providers, and real email/SMS/push remain unverified unless actually
  exercised. Phase 1 §1.1 stays open.
- **No Phase 2–10 regression.** Existing RBAC, audit, department isolation,
  complaint lifecycle, outbox/notification reliability, V1–V13 migrations, H2
  test double, Spring Boot 4.2.0 stack, and all APIs remain unchanged.
- **No secrets in tracked files.** `.env` stays ignored. Only placeholder values
  may be added to `.env.example`; the repeatable secret scan must stay clean.
- **No Phase 12 work.** Historical Phase 11 boundary only; superseded by the
  owner-authorized final Phase 12 described in the current roadmap and
  `docs/PHASE12_IMPLEMENTATION_CONTRACT.md`.

## 3. Existing functionality to reuse

- `scripts/dev-env.ps1`: process-scoped `.env` loading and portable Node PATH.
- `scripts/run-backend.ps1`, `scripts/run-frontend.ps1`: local-process launchers.
- `scripts/dependency-audit.ps1`, `scripts/secret-scan.ps1`: security audits.
- `backend/pom.xml` on Spring Boot 4.2.0 with modular starters and springdoc
  3.2.1.
- `backend/src/main/resources/application.yml`: full configuration surface,
  including Phase 10 CSP, rate-limit, reset, and retention properties.
- `backend/src/main/resources/db/migration/V1__…V13__…sql`: forward-only Flyway
  history; V13 is the Phase 10 reset-token table.
- `docs/SUPABASE_SETUP.md`: one-time Supabase project, extensions, private
  `civic-images` bucket, S3 keys, and verification checklist.
- `ARCHITECTURE.md §6`: supported Supabase + local-process topology.
- `frontend/package.json`: dev/build/test/lint/preview/E2E scripts.
- Actuator `/actuator/health` and API `/meta/health` health surfaces.

## 4. Documentation and configuration plan

### 4.1 `.env.example` refresh

- Keep all existing placeholder-only values.
- Correct the bootstrap-admin variable name to the implemented
  `CIVICSENSE_SUPERADMIN_EMAIL` / `CIVICSENSE_SUPERADMIN_PASSWORD`.
- Add the missing Phase 10 configuration keys with safe defaults/placeholders:
  `CIVICSENSE_CSP`, `CIVICSENSE_RATE_*`, `CIVICSENSE_RESET_*`,
  `CIVICSENSE_RETENTION_*`.
- Add explicit comments for direct-port Flyway usage (5432, never 6543 for DDL)
  and for never committing real values.
- Do not add a real host, credential, token, or project reference.

### 4.2 `docs/DEPLOYMENT.md` (new consolidated guide)

Create a single authoritative deployment-support document containing:

1. **Supported path and decision record** — Supabase-hosted Postgres/PostGIS +
   S3-compatible Storage, local Java 21/Maven backend, local portable-Node
   frontend/Vite; no Docker.
2. **Prerequisites** — Java 21, Maven (or the project-local Maven repo config),
   portable Node under `tools/node`, Git, a Supabase project, and operator
   credentials supplied only via git-ignored `.env`.
3. **Fresh clone → running stack** — exact commands to clone, copy
   `.env.example` to `.env`, load `scripts/dev-env.ps1`, run backend and
   frontend, and verify `/api/v1/meta/health`, `/api/v1/actuator/health`, and
   the SPA at `http://localhost:5173`. Document separate terminals and the
   process-scoped nature of environment loading.
4. **Migrations** — Flyway V1–V13 are forward-only; use direct PostgreSQL port
   5432; `flyway_schema_history` is the source of truth; `ddl-auto=validate`;
   rollback SQL exists only as reviewed comments and is not an automatic
   rollback; no manual application-table creation. Include the command to start
   the backend so Flyway applies migrations on startup.
5. **Storage setup** — link to and summarise `docs/SUPABASE_SETUP.md`: private
   `civic-images` bucket, allowed MIME types/size, S3 keys, CORS origins, and
   that production origin/provider values remain operator-supplied.
6. **Backup/restore** — scope (Postgres/PostGIS application data plus Storage
   bucket objects), Supabase platform backups, operator-run `pg_dump`/`psql`
   and S3 sync/restore commands, restore into a disposable schema/project
   first, verification queries, and retention/rotation responsibilities. State
   that true hosted backup/restore is not exercised in this local environment.
7. **Logging/monitoring** — current Logback JSON-to-stdout policy, Actuator
   health/info exposure, log levels, correlation IDs, and explicit note that
   aggregation/alerting are operator-provided future work, not implemented by
   Phase 11. *(Historical Phase 11 wording; Phase 12 subsequently enabled Boot
   ECS JSON console logging and the global correlation filter. Aggregation and
   alerting remain operator-provided.)*
8. **Security notes** — environment-only secrets, CSP/rate-limit/retention
   defaults, disabled RLS, single-instance rate limits, and secret scanning.
9. **Verification evidence and limitations** — record exactly what was run and
   what remains pending under Phase 1 §1.1.

### 4.3 Cross-document updates

- `DEVELOPMENT_ROADMAP.md`: add a Phase 11 completion report, update header,
  status table, and gate.
- `ARCHITECTURE.md`: point deployment/logging/monitoring sections at
  `docs/DEPLOYMENT.md`; keep topology and security decisions consistent.
- `API_DOCUMENTATION.md`: add a short deployment/health verification subsection
  without changing API contracts.
- `DATABASE_DESIGN.md`: add a deployment/backup note without changing schema
  facts; keep V1–V13 inventory authoritative.
- `docs/SUPABASE_SETUP.md`: cross-link the consolidated deployment guide and
  clarify its development-only status.
- `README.md`: if present, add a short “Start here” link to the deployment
  guide; otherwise no README is created.

No Java/React source, API endpoint, Flyway migration, or H2 test schema changes
are planned.

## 5. Backup/restore test approach

Because no hosted credentials and no `pg_dump`, `psql`, or `aws` CLI are
available locally, the hosted procedure cannot be executed here. Phase 11 will:

1. Document the real Supabase/PostgreSQL + S3 command sequence for an operator.
2. Exercise a **local, clearly-labelled dry run** of the documented procedure
   shape against the H2 test double / repository state, proving that the
   documented steps are ordered and repeatable.
3. Record the dry run and explicitly state that hosted backup/restore was not
   executed and that Phase 1 §1.1 remains open.

This is a local verification of documentation mechanics, not a claim that real
Supabase backup/restore was tested.

## 6. Verification plan

Run and record:

```powershell
cd E:\CivicSense\backend
mvn -B test
mvn -B package

cd E:\CivicSense\frontend
$env:PATH = 'E:\CivicSense\tools\node\node-v26.13.1-win-x64;' + $env:PATH
npm run test
npm run lint
npm run build
npm run test:e2e

cd E:\CivicSense
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\secret-scan.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dependency-audit.ps1 -Maven 'C:\maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd' -Npm 'npm'
```

Additional Phase 11 checks:
- Local-process smoke check: start the backend with the `test` profile (H2 test
  double, Flyway disabled), start the Vite dev server, and probe health/SPA
  routes. This verifies local run instructions but **not** hosted migrations.
- Configuration consistency check: every environment variable in
  `.env.example` that the backend consumes is present; placeholder values are
  never real credentials.
- Backup/restore dry run as described in §5.

## 7. Acceptance criteria evidence

| Criterion | Phase 11 evidence | Honest boundary |
|---|---|---|
| Fresh clone → documented commands → full stack running with migrations applied | Consolidated fresh-clone instructions; local-process smoke check with H2 test double; documented Supabase Flyway V1–V13 procedure | Hosted Supabase migrations cannot be applied locally because credentials are placeholders; Phase 1 §1.1 remains open. |
| Backup/restore procedure documented and tested once | `docs/DEPLOYMENT.md` backup/restore procedure plus recorded local dry run | Hosted Supabase Postgres/Storage backup/restore was not executed. |

## 8. Completion gate

Update all relevant documentation with executed results, verify Git state, and
stop at the Phase 11 completion gate for owner review. The Phase 10 checkpoint
`d49e9a8` / `phase10-approved` remains intact. No push and no Phase 12 work.

## 9. Executed outcome (2026-11-22)

- `.env.example` refreshed with CSP, rate-limit, reset, and retention keys; all
  values remain placeholders. `.env` was not modified.
- `docs/DEPLOYMENT.md` created as the consolidated operations guide;
  `scripts/backup-restore-dry-run.ps1` added as repeatable verification evidence.
- No Java/React source, API, Flyway migration, H2 test schema, or Docker
  artifact was added or changed.
- Backup/restore dry run passed twice with identical snapshots
  `users=1|audit_logs=1|settings=1|email=dryrun@example.test`; hosted
  Supabase/PostgreSQL/S3 backup and restore were not executed.
- Local smoke check: backend health endpoints served (HTTP 503 expected under
  the H2 test profile with `database=UP`), SPA served HTTP 200; both processes
  stopped afterward.
- Backend 127/127 tests and package success; frontend 39/39 tests, lint 0,
  build success, 5/5 E2E; dependency audit clean; secret scan clean;
  `git diff --check` clean.
- Full results and limitations are recorded in `DEVELOPMENT_ROADMAP.md §15` and
  `docs/DEPLOYMENT.md §9`. Owner approval was received and Phase 11 was
  checkpointed locally as `phase11-approved`; no push was performed.

## 10. Superseding Phase 12 note

This Phase 11 contract is retained as historical evidence. The owner later
authorized Phase 12 — Production Readiness & Infrastructure Hardening — as the
final planned phase. It added structured logging, global correlation,
provider-gated SMTP reset delivery, password-hash upgrading, storage/notification
hardening, repeatable hosted preflight and local smoke helpers, and final
verification. Earlier statements that Phase 12 did not exist, that structured
JSON/correlation were future work, or that no email channel existed are
superseded by `DEVELOPMENT_ROADMAP.md §16` and
`docs/PHASE12_IMPLEMENTATION_CONTRACT.md §11`. Hosted integrations remain
blocked, not verified.
