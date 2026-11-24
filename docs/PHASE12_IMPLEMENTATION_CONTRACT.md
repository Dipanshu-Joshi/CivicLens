# Phase 12 implementation contract — Production Readiness & Infrastructure Hardening

| Field | Value |
|---|---|
| Document | `docs/PHASE12_IMPLEMENTATION_CONTRACT.md` |
| Phase | 12 — Production Readiness & Infrastructure Hardening |
| Status | Contract written before implementation; completed locally with final results in §11; final phase, no Phase 13 |
| Roadmap definition | `DEVELOPMENT_ROADMAP.md §3`, added from owner authorization on 2026-11-22 |
| Approved checkpoint | `083a0e6` / `phase11-approved`; all earlier approved tags preserved |
| Last updated | 2026-11-22 |

This contract records the complete Phase 12 boundary before implementation code
is changed. Phase 12 was not present in the original roadmap; the owner
authorized this definition and explicitly stated that it is the final planned
development phase. The scope below comes from that authorization plus the
documented deferred work and open verification gates in `DEVELOPMENT_ROADMAP.md`
§§1.1, 10, 11, 14 and 15.

---

## 1. Exact roadmap scope

The roadmap defines seven workstreams:

| ID | Workstream | Required work |
|---|---|---|
| A | Database and Supabase | Connect to the hosted project; verify PostgreSQL connectivity; execute/validate Flyway V1–V13, ordering, schema compatibility, indexes and constraints; verify PostGIS and real spatial queries; test jurisdiction routing on real PostGIS; document/resolve incompatibilities. |
| B | Object storage | Configure real production storage; verify secure upload, confirmation, retrieval and access control; test invalid files, ownership restrictions and failures; ensure storage failures never lose complaint data. |
| C | Notifications | Implement real password-reset email delivery; verify one-time token handling and expiration; test delivery failures, retries and duplicate prevention; keep email/push disabled without providers. |
| D | Distributed infrastructure | Evaluate Redis or another shared rate-limiting mechanism; implement it only if justified; preserve existing limits/security behavior; test across instances where possible. |
| E | Observability | Implement structured JSON logging and a global correlation-ID filter; propagate IDs through relevant operations; prevent secrets/PII/sensitive complaint data in logs; document monitoring/troubleshooting. |
| F | Security review | Review authorization/database access; evaluate RLS and enable only after role-pattern tests; evaluate Argon2id and, if justified, implement gradual upgrade without forced resets; preserve all auth guarantees. |
| G | Production deployment verification | Perform a production-like no-Docker deployment; verify health, SPA, DB, migrations, storage and providers; test actual backup/restore where access exists; verify restart recovery; document operations and rollback. |

**Dependencies:** Phase 11 checkpoint `083a0e6` / `phase11-approved`; existing
Java 21 / Spring Boot 4.2.0 backend, React/TypeScript/Vite frontend, Flyway
V1–V13, RBAC, audit history, department isolation, complaint lifecycle, durable
outbox and deployment documentation. Real A/B/G verification requires
operator-supplied Supabase and S3 credentials in the git-ignored `.env`; real
email requires an SMTP provider. Docker remains excluded. No Phase 13 exists.

**Exit criteria:** all locally verifiable work implemented and tested; full
verification executed with actual results; every unavailable external item
named as blocked and never claimed as verified; documentation accurate;
`.env.example` complete; no secrets or obsolete configuration committed; final
commit and local `phase12-approved` tag created; no push.

---

## 2. Existing functionality and remaining gaps

### 2.1 Existing functionality to reuse

- **Authentication/authorization:** Spring Security with JWT access tokens,
  rotating hashed refresh cookies, role-based controller rules, service-layer
  ownership checks, live department verification, account lockout, BCrypt
  strength 12, password reset with SHA-256 token hashes and single-use tokens.
- **Data and workflow:** Flyway V1–V13 (`V8__phase5_image_analysis.sql` is
  present; only the originally planned `V8__super_admin_seed.sql` is absent),
  entities and
  repositories for complaints, routing, AI history, notifications, audit and
  outbox; H2 `schema-test.sql` for local tests; `ddl-auto=validate`.
- **Notifications:** transactional outbox with atomic claim, lease, attempts,
  retry backoff, in-app channel, recipient-scoped preferences, dedupe by
  `outboxEventId`, durable `PROCESSED/FAILED` state, and a disabled external
  channel placeholder.
- **Rate limiting:** `RateLimitService` Bucket4j-style in-memory buckets for
  global, login, complaint submit, presign and password-reset flows; filter-level
  429 responses with `Retry-After`.
- **Errors/audit:** RFC 7807 problem responses with `correlationId`, audit rows
  carrying correlation metadata, and append-only history.
- **Deployment/docs:** `docs/DEPLOYMENT.md`, `docs/SUPABASE_SETUP.md`,
  `.env.example`, repeatable secret/dependency audits, and an H2 backup/restore
  dry run.
- **Frontend:** complete citizen, department-admin and super-admin React flows,
  tests, lint/build and local Edge Playwright E2E.

### 2.2 Documented gaps this phase must address

1. Hosted Supabase/PostGIS, real Flyway V1–V13, real S3 `HeadBucket`, hosted
   health and hosted backup/restore have never been executed; root `.env` still
   contains placeholders. Local `psql`, `pg_dump`, `pg_restore`, `aws`, `redis-cli`
   and `docker` are not installed.
2. `auth.password_reset_requested` outbox events are currently consumed as
   non-notification events and never sent by email; no mail dependency, config
   or channel exists.
3. Terminal `FAILED` outbox rows have `nextAttemptAt = null`, but both
   `findDueIds` and `claim` treat null as due, so they can be reclaimed forever.
4. There is no structured JSON log encoder and no global request correlation
   filter; only error bodies and health responses currently expose correlation
   IDs.
5. Rate limiting is in-memory and single-instance; no Redis/shared-store code or
   dependency exists.
6. RLS scaffolding in V6 remains commented out; BCrypt remains the only hash.
   Both were deferred in Phase 10 pending evidence or need.
7. Real object-storage failure and cross-provider deployment verification cannot
   be exercised locally without credentials.

### 2.3 Evaluation decisions (evidence-based)

| Item | Decision |
|---|---|
| Distributed rate limiting | **Not implemented unless a shared store is actually available.** The deployed architecture is a single local process, no Redis service/dependency exists, and current limits must be preserved. Document the horizontal-scaling prerequisite rather than adding an unused infrastructure dependency. |
| PostgreSQL RLS | **Not enabled.** Application-layer authorization is the enforced boundary; no least-privilege DB role or hosted PostgreSQL test matrix exists, and enabling RLS could break background delivery/cleanup and table-owner operations. Document the evaluated policy patterns and exact prerequisites. |
| Argon2id migration | **Initially evaluate only; superseded during Phase 12 by owner-directed implementation.** A gradual BCrypt → Argon2id path was implemented with Bouncy Castle 1.85, prefix `{argon2id}`, legacy BCrypt verification, and transparent rehash after successful login. No schema migration was required. |
| Real email delivery | **Implement when a mail provider can be configured**, using the existing outbox event and an SMTP channel gated by explicit configuration. Without a provider, email remains disabled; no fake delivery is claimed. Add safe local tests with an in-memory fake transport if the dependency can be resolved. |
| Hosted integrations | **Blocked, not fabricated.** Missing credentials/access are named precisely; local tests and mocked integrations are labelled as such. |

---

## 3. Planned implementation by workstream

### A. Database and Supabase (local portion)

- Perform static consistency review of V1–V13 ordering, expected 13 applied
  rows (`V8__phase5_image_analysis.sql` present), `ddl-auto=validate`
  compatibility, indexes/constraints, and the H2 test-double boundary.
- Add an operator verification procedure/script that refuses placeholder
  credentials and records exact SQL checks for connectivity, Flyway history,
  PostGIS, spatial routing and schema constraints when access is supplied.
- Hosted execution itself remains blocked until the exact credentials in §7 are
  provided.

### B. Object storage (local portion)

- Review/reuse existing presign, confirmation, magic-byte verification,
  ownership checks and short-lived GET paths; add or strengthen automated tests
  for invalid files, cross-user ownership and storage failure handling where
  gaps exist.
- Confirm via code/test evidence that S3 failures do not roll back or delete
  complaint data.
- Hosted bucket configuration and `HeadBucket` remain blocked until credentials
  are supplied.

### C. Notifications

- Add a provider-gated SMTP delivery path for
  `auth.password_reset_requested` events; never write raw tokens to in-app
  notifications or logs.
- Preserve token hashing, expiration, single-use behavior, generic API response,
  rate limits and refresh revocation.
- Fix the terminal-`FAILED` outbox retry predicate so terminal failures are not
  reclaimed indefinitely.
- Add tests for successful delivery, disabled provider, transport failure with
  retry, retry exhaustion, and no duplicate sends.
- Add mail configuration to `application.yml`, `application-test.yml` and
  `.env.example`; keep email/push preferences disabled unless a provider is
  configured.

### D. Distributed infrastructure

- Document the evaluation and preserve existing limits and behavior.
- Add no Redis dependency or untested alternative while no shared store or
  multi-instance deployment exists.

### E. Observability

- Add a request-boundary correlation filter that accepts a valid inbound
  `X-Correlation-Id`, generates one otherwise, sets a response header, and
  exposes it to the SPA via CORS.
- Add structured JSON console logging using already-available libraries where
  possible; profile-controlled and with no tokens, passwords, raw reset tokens,
  full coordinates or complaint content in log output.
- Propagate the correlation ID into existing error responses and audit metadata
  through the established path rather than duplicating it.
- Add tests for generated/propagated headers, CORS exposure and sensitive-data
  redaction; document monitoring and troubleshooting commands.

### F. Security review

- Re-run authorization/isolation tests; keep all controller/service checks.
- Document RLS and Argon2id evaluations with the evidence above; make no
  unverified production security change.
- Run secret scan and dependency audit; fix regressions introduced here.

### G. Production deployment verification (local portion)

- Run a production-like no-Docker local-process smoke check and restart check.
- Record exact health/SPA outcomes and clearly label H2/test-profile results.
- Document startup, health verification, backup/restore and rollback steps;
  hosted deployment/backup execution remains blocked until credentials exist.

---

## 4. Backend changes

Anticipated classes/files: a correlation filter and registration, JSON logging
configuration, SMTP properties/configuration, an email outbox processor/channel,
an outbox retry-predicate fix, new tests, and configuration/template updates.
Reuse `PasswordEncoder`, `AuthService`, `OutboxEvent*`, `NotificationDeliveryChannel`,
`GlobalExceptionHandler`, audit services and `RateLimitService`; do not replace
them. No auth bypass, fake data, or new product endpoint is permitted.

## 5. Frontend changes

No new user-facing feature is planned. Only if observability requires it, expose
`X-Correlation-Id` through CORS and verify no frontend contract changes. Keep
React/TypeScript, existing routes, guards, notification UX and design system
unchanged.

## 6. APIs and database migrations

- **Public API:** no new endpoints; password-reset endpoints remain unchanged.
- **Headers:** `X-Correlation-Id` may be echoed on responses and exposed via
  CORS; this is additive.
- **Outbox:** internal event handling changes only; raw token remains confined
  to the outbox payload and outbound email.
- **Migrations:** no new Flyway migration is planned. V1–V13 remain unchanged.
  RLS is not enabled. If evidence later justifies Argon2id, a forward-only hash
  prefix strategy must be designed before any migration is added; existing
  `varchar(255)` is already sufficient for prefixed hashes.

## 7. Security and reliability requirements

- Preserve RBAC, department isolation, audit history, complaint lifecycle,
  notification reliability and deployment configuration.
- Never log or expose secrets, JWTs, refresh tokens, raw reset tokens, full
  coordinates or sensitive complaint content.
- Email must fail safely: delivery errors stay in outbox state with bounded
  retries and never roll back the password-reset transaction or leak token
  values.
- Duplicate prevention must be enforced by outbox state and a provider-side
  idempotency key where applicable.
- Terminal outbox failures must be visibly retained but not reclaimed forever.
- Configuration must default to disabled external providers.
- No test weakening, fake production data, auth bypass, or unverified hosted
  claim.

## 8. Testing and acceptance criteria

| Area | Evidence required |
|---|---|
| Backend | Full `mvn -B test`; 0 failures/errors; meaningful new tests for email delivery, retry/dedupe, correlation filtering and redaction; `mvn -B package`. |
| Frontend | `npm run test`, `npm run lint`, TypeScript check via `npm run build`, and `npm run test:e2e` with existing local Edge stubs. |
| Database | Static V1–V13 review; H2 tests. Real PostgreSQL/PostGIS checks only if credentials are supplied. |
| Storage | Local mocked presign/confirm/ownership/invalid-file/failure tests; hosted `HeadBucket` and transfer only if credentials are supplied. |
| Notifications | Outbox success/failure/retry/exhaustion/no-duplicate tests; provider-disabled default. |
| Rate limits | Existing global/login/reset/submit/presign tests remain green. |
| Security | Secret scan, dependency audit, authorization/isolation tests, RLS/Argon2id evaluation record. |
| Deployment | Local-process smoke and restart check; documented rollback; hosted checks marked blocked if credentials are missing. |
| Git | `git diff --check`, clean staged review, final commit and local `phase12-approved` tag, no push. |

### Explicit external evidence boundary

Results must be classified as **local**, **mocked**, or **actual hosted**. No
hosted integration is claimed unless actually executed with operator-supplied
credentials and recorded with non-secret evidence.

---

## 9. External access currently missing

The following are required for the hosted portions of A, B and G and for real
email delivery in C. They are not present in the repository and must never be
invented:

1. Real Supabase direct PostgreSQL host/port 5432, database name/user and
   password in the git-ignored `.env`.
2. Real Supabase Storage S3 endpoint, private `civic-images` bucket, access key
   and secret in `.env`.
3. Network access to those endpoints from this environment.
4. `psql`/`pg_dump`/`pg_restore` or equivalent trusted PostgreSQL client and an
   S3 client for operator backup/restore evidence.
5. SMTP host, port, security mode, username/password and sender address for real
   password-reset email; otherwise email must stay disabled.
6. A shared Redis-compatible endpoint and operator decision before distributed
   rate limiting can be implemented or cross-instance tested.

---

## 10. Completion gate

After implementation and verification, update `DEVELOPMENT_ROADMAP.md`,
`ARCHITECTURE.md`, `API_DOCUMENTATION.md`, `DATABASE_DESIGN.md`,
`AI_ROUTING_DESIGN.md` where relevant, `docs/DEPLOYMENT.md`,
`docs/SUPABASE_SETUP.md` and this contract with actual results. Prepare the
final handover report covering all 12 phases, features, architecture, security,
database, deployment, tests, actual external integrations, limitations,
production-readiness assessment and Git state. Stop at the Phase 12 gate; no
Phase 13 and no push.

---

## 11. Final results (executed 2026-11-22)

Sections 1–10 remain the pre-implementation contract and are retained for
auditability. The results below supersede any planning-time “evaluate only” or
“future work” wording in those sections.

### 11.1 Workstream outcomes

| ID | Outcome |
|---|---|
| A — Database and Supabase | Static V1–V13 inventory is consistent: 13 migration files including `V8__phase5_image_analysis.sql`; the unused `V8__super_admin_seed.sql` is still absent. H2 tests passed. Hosted PostgreSQL/PostGIS, real Flyway application, `SELECT 1`, `postgis_version()`, `ST_Intersects` jurisdiction routing, hosted health/report/approval and hosted backup/restore remain **blocked** because the direct DB credentials and network access were not supplied. `scripts/phase12-hosted-preflight.ps1` reports the exact missing keys without printing values. |
| B — Object storage | Existing presign/confirm/retrieval controls were retained and hardened locally: explicit 403 on cross-user discard, rollback of a pending upload when presigning fails, missing/oversized object rejection, PUT/GET TTL assertions, storage-failure handling that preserves the complaint, and direct ownership checks. Real S3 `HeadBucket`/signed transfer and hosted bucket policy verification remain **blocked** by placeholder S3 credentials and absent `aws` CLI. |
| C — Notifications | Provider-gated SMTP password-reset channel added using the existing transactional outbox. It is disabled unless `CIVICSENSE_MAIL_ENABLED=true` and all SMTP settings are present. Deterministic Message-ID provides provider-side best-effort dedupe; raw reset tokens are never logged; transport exceptions are sanitised and never persisted as `lastError`. Terminal `FAILED` rows are no longer reclaimed forever while new `PENDING` rows remain due. Fake-transport success/failure/retry/no-duplicate tests pass. Real delivery remains **blocked** until SMTP host, port, security mode, username, password and sender are supplied. |
| D — Distributed infrastructure | Evaluated and deliberately **not implemented**. No Redis dependency, service, CLI or configuration was added. The existing in-memory Bucket4j limits and 429/`Retry-After` behaviour are preserved. Cross-instance testing is **blocked** by the absence of a shared store and by the single-process deployment decision; horizontal scale-out is documented as a prerequisite. |
| E — Observability | Boot 4.2 built-in ECS structured JSON console logging enabled with `logging.structured.format.console: ecs` (`json` is invalid in Boot 4.2). A global `X-Correlation-Id` filter generates or echoes IDs, exposes the header through CORS, and propagates IDs into MDC, async workers, scheduled cleanup, error bodies, health responses and audit rows. Structured-logging, propagation, redaction and integration tests pass. Log aggregation/alerting remain operator-provided. |
| F — Security/authz | Authorization/isolation regression tests pass. PostgreSQL RLS remains **not enabled**: there is no least-privilege DB role or hosted PostgreSQL test matrix, and background delivery/cleanup could break; policy patterns and prerequisites are documented. Argon2id was implemented as a gradual upgrade with Bouncy Castle 1.85, `{argon2id}` prefix, legacy BCrypt verification and transparent rehash after successful login. Legacy hashes remain valid. |
| G — Production-like verification | Local no-Docker smoke passed against the packaged jar with the H2 test double plus the built SPA: health, actuator, SPA root and restart recovery all reached, with correlation headers present; processes were cleaned up. `scripts/backup-restore-dry-run.ps1` passed again. Hosted deployment, hosted health, hosted backup/restore and real provider checks remain **blocked** by absent hosting account, external access and operator clients (`psql`, `pg_dump`, `pg_restore`, `aws`). |

### 11.2 Verification evidence

| Check | Result |
|---|---|
| Backend `mvn -B test` | **149 tests, 0 failures, 0 errors, 0 skips** |
| Backend `mvn -B package` | **BUILD SUCCESS**; executable jar produced |
| Focused Phase 12 suite | **16/16 passed**; broader focused suite also passed after final fixes |
| Frontend `npm run test` | **39/39 tests in 6 files passed** |
| Frontend `npm run lint` | Exit 0; only the pre-existing `DepartmentProfilePage.tsx` React warnings |
| Frontend `npm run build` | `tsc -b && vite build` succeeded; non-blocking 500 kB chunk advisory remains |
| Frontend `npm run test:e2e` | **5/5 Playwright tests passed** using local Edge and stubbed APIs |
| Dependency audit | npm: **0 vulnerabilities**; Maven OSV: **0 advisories** after upgrading Bouncy Castle 1.84 → 1.85 |
| Secret scan | **307 tracked files scanned; no findings** |
| Local no-Docker smoke/restart | **PASSED**; health/actuator/SPA/recovery reachable, processes stopped |
| Backup/restore dry run | **PASSED** with H2 test double only; hosted path not exercised |
| Hosted preflight | Static checks run; hosted items reported **BLOCKED** with exact keys/tools named; no network calls and no values printed |
| `git diff --check` | Clean at the final pre-commit review; no whitespace errors |

### 11.3 Evidence boundary and production-readiness assessment

All results above are **local** or **mocked** except the repository audits and
static inventory. No hosted Supabase/PostGIS, S3, SMTP, deployment, backup or
cross-instance result is claimed. Phase 1 §1.1 remains open. The code is
locally hardened and verified, but the project is **not** represented as
production-ready until an operator supplies the exact requirements in §9 and
executes the hosted checklist.

### 11.4 Git gate

The Phase 12 changes were reviewed, committed locally, and tagged
`phase12-approved` after verification. Earlier commits and tags
(`phase4-approved` through `phase11-approved`) remain intact. No remote was
added and no push was performed. Phase 12 is the final planned development
phase; Phase 13 was not created.
