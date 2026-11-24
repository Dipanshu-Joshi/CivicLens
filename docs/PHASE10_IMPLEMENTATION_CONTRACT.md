# Phase 10 implementation contract (working document)

All paths are relative to `/api/v1`. Phase 9 was the approved checkpoint
`1614c79` / tag `phase9-approved`. This document defines the Phase 10 boundary
before any implementation code was changed. Phase 11 was gated on owner approval
of Phase 10 and started only after the local `phase10-approved` checkpoint.
This is a historical contract; statements about deferred email, Argon2id,
logging or correlation are superseded by Phases 11–12 as recorded in
`DEVELOPMENT_ROADMAP.md §§15–16`.

## 1. Exact roadmap scope

`DEVELOPMENT_ROADMAP.md §229–235` defines Phase 10 as **Testing & security**:

- Unit, integration, API, frontend component, authorization, file-upload, AI,
  routing, and E2E tests (Playwright).
- Security review: RLS defence in depth, Argon2id evaluation, CSP, dependency
  audit, rate-limit verification, secret scanning.
- Exit criteria: no critical/high vulnerabilities in the dependency scan; all
  tests green; documented fixes for issues found.

Supporting planned items explicitly labelled Phase 10 elsewhere in the approved
documentation are also in scope:

- `API_DOCUMENTATION.md §2`: tokenised `POST /auth/forgot-password` and
  `POST /auth/reset-password` via outbox.
- `DATABASE_DESIGN.md §7`: documented retention cleanup for `audit_logs` and
  `ai_analyses`.

Out of scope: Phase 11 deployment/Docker/.env.example/backup work, production
infrastructure changes, real email/SMS/push providers, real AI provider calls,
and hosted Supabase verification.

## 2. Preserve existing contracts

Do not break, rename, or narrow any Phase 2–9 surface, including:

- All auth, citizen, department, admin, upload, health/meta, and notification
  endpoints from `API_DOCUMENTATION.md §2–§18`.
- JWT access + rotating refresh cookie, BCrypt hashes, role checks, department
  isolation, audited actor identity, and append-only history/audit streams.
- Complaint lifecycle, outbox reliability, notification dedupe/retry, owner
  checks, and role-aware frontend navigation.
- V1–V12 Flyway migrations and the H2 `schema-test.sql` test double.

No production auth bypass, fake production data, client-supplied actor, or
client-supplied department scope may be introduced.

## 3. Existing implementation assessment

Already present and must be preserved/reused:

- Backend unit/integration/API tests: auth, lifecycle, notifications, Phase 8
  governance, uploads, AI analysis, routing, and authorization.
- Frontend component tests: citizen/department/admin pages and Phase 9
  notification workflows.
- `RateLimitService` (Bucket4j) with login rate limiting and `Retry-After`.
- Spring Security filter chain and `@PreAuthorize` method security.
- Durable `outbox_events` dispatcher/processor with retry, dedupe, and lease.
- Storage/AI/geocoding abstractions with local test doubles.
- Shared UI: `AppLayout`, `PageHeader`, `Card`, `Badge`, `StatePanel`, `Button`,
  `Field`, `inputClass`, status/timeline components, and query/mutation hooks.

Gaps to close:

1. No CSP response-header configuration or verification.
2. Rate-limit verification only unit-tests `RateLimitService`; documented
   global/submit/presign limits are not implemented or API-verified.
3. No Playwright browser-level E2E coverage.
4. No password reset endpoints, token table, reset UI, or outbox event.
5. No retention cleanup job for old audit/AI rows.
6. No repeatable dependency audit or secret-scanning procedure/scripts.
7. RLS and Argon2id decisions are documented only as deferred; Phase 10 must
   record an explicit, evidence-based security decision.

## 4. Backend implementation plan

### 4.1 Security headers

- Add configurable CSP via `SecurityConfig.headers(...)`. Default policy:
  `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://tile.openstreetmap.org; connect-src 'self' http://localhost:8080 https://tile.openstreetmap.org; font-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'`.
- Keep Spring Security's existing frame/clickjacking/content-type protections.
- Add MockMvc assertions for CSP and other security headers. Do not add a
  browser meta CSP that would break Vite dev/HMR.

### 4.2 Rate limiting

- Extend `RateLimitService` with a keyed `tryConsume(key, capacity, window)`
  method while preserving the existing login behavior.
- Add an `ApiRateLimitFilter` after `JwtAuthenticationFilter` for:
  - global per-IP requests: default 120/min/IP (health/docs/static excluded);
  - `POST /citizen/complaints`: default 3/min/user (IP fallback);
  - `POST /uploads/presign`: default 10/min/user (IP fallback).
- Return RFC-shaped 429 responses with `Retry-After`. Keep test-profile global
  capacity high so existing suites are deterministic; add a dedicated
  low-capacity integration test for 429 responses.
- Document that the limiter is single-instance/in-memory; Redis remains a
  scale-out prerequisite.

### 4.3 Password reset

- Flyway `V13__phase10_password_reset.sql`: `password_reset_tokens` with
  `id`, `user_id` FK, `token_hash` unique, `expires_at`, `used_at`, `created_at`;
  mirror in `schema-test.sql`.
- New entity/repository and DTOs. Raw tokens are never stored in the token
  table; only SHA-256 hashes. Tokens expire after 30 minutes and are one-time.
- `POST /auth/forgot-password` `{ "email": "..." }` always returns the same
  generic `200` message. For an active known account, generate a 256-bit token,
  persist its hash, and write an `auth.password_reset_requested` outbox row
  containing the one-time link token for the future email channel. Email stays
  disabled; the notification dispatcher must ignore non-`notification.*` event
  types. No raw token is exposed through in-app notifications or APIs.
- `POST /auth/reset-password` `{ "token": "...", "password": "..." }`
  validates the same password rules as registration, rejects used/expired/
  unknown tokens with `400`, updates the hash, clears lockout counters, marks
  the token used, and revokes all refresh-token families.
- Both endpoints are rate-limited by IP. Tests cover success, reuse, expiry,
  unknown email non-enumeration, weak password, invalid token, refresh revocation,
  and outbox/dispatcher isolation.

### 4.4 Retention cleanup

- Add a configurable scheduled `DataRetentionService` using existing
  repositories; default retention 24 months, disabled in tests unless invoked
  directly. Delete only rows older than the cutoff from `audit_logs`,
  `ai_analyses`, and `ai_analysis_runs`.
- Expose no deletion API. Add a unit/integration test for cutoff behavior and
  confirm recent rows survive.

### 4.5 Security review decisions

- **RLS defence in depth:** evaluate the disabled V6 scaffolding against the
  application's table-owner connection, background `REQUIRES_NEW` jobs, and
  H2-only test environment. Record whether it is enabled; if not, document
  why app-layer isolation remains authoritative and add explicit authorization
  tests. Do not claim RLS enforcement that was not exercised.
- **Argon2id:** compare BCrypt strength 12 with Argon2id for this project's
  Java 21/no-native-dependency constraint and existing hash migration needs.
  Record the decision and any future trigger. Do not silently change password
  hashing algorithms.
- **Dependency audit:** run frontend and backend dependency audits. If a
  high/critical finding is actionable, upgrade/patch and re-run; otherwise
  document the exact command, result, and rationale.
- **Secret scanning:** add a repeatable script that scans tracked files for
  common credential formats and accidental env/secret files; run it and record
  results. Ignore `.env`, build output, and the portable toolchain by Git design.

## 5. Frontend implementation plan

- Add API client functions/types for `forgotPassword` and `resetPassword`.
- Add public `/forgot-password` and `/reset-password` routes/pages using the
  CivicSense design system, with loading, success, error/retry, validation, and
  responsive states. Link "Forgot password?" from the login screen.
- Reset token is read from the URL query string only and sent in the request
  body; never persist it in browser storage or logs.
- Add Vitest coverage for request success/failure and form behavior.
- Add Playwright E2E configuration using the locally installed Microsoft Edge
  channel and `Vite` preview/dev server. Use deterministic API route stubs for
  browser-level workflow verification because hosted Supabase/PostGIS and real
  providers are unavailable. Cover:
  1. citizen sign-in → notification → complaint detail → info reply;
  2. role-aware notification navigation and forbidden-role behavior;
  3. forgot/reset password form flow.
  Report clearly that E2E uses browser automation with stubbed APIs, not hosted
  production integrations.

## 6. Database and configuration changes

- `V13__phase10_password_reset.sql` and matching H2 test schema.
- New properties: CSP policy, global/submit/presign rate limits, password reset
  token TTL, retention months/enabled/cron. All environment-overridable and
  defaulted so existing local/test behavior remains deterministic.
- No migration may drop or rewrite complaint, audit, assignment, AI, routing,
  or notification history tables.

## 7. Verification commands

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
```

Also execute the documented dependency audit and secret-scan commands and
record their exact results.

## 8. Completion gate

Update `DEVELOPMENT_ROADMAP.md`, `API_DOCUMENTATION.md`, `ARCHITECTURE.md`,
`DATABASE_DESIGN.md`, `AI_ROUTING_DESIGN.md` (only if AI behavior changes),
and this contract with the implemented Phase 10 result. Report only tests
actually executed, distinguish local test doubles from real integrations, fix
or document every security finding, and stop for explicit owner approval before
Phase 11.

---

## 9. Executed Phase 10 results (2026-11-22)

Phase 10 was implemented and locally verified, then approved and checkpointed
locally as tag `phase10-approved`. The Phase 9 checkpoint `1614c79` / tag
`phase9-approved` is preserved. No Phase 11 work was included in that checkpoint
and no push was performed.

### 9.1 Implemented scope against the plan

- **§4.1 Security headers:** configurable `CIVICSENSE_CSP` added in
  `SecurityConfig`; Spring Security frame/content-type protections retained. No
  browser meta CSP. Inline styles are allowed only for Vite runtime injection.
- **§4.2 Rate limiting:** `RateLimitService.tryConsume(...)` plus
  `ApiRateLimitFilter` enforce global 120/min/IP, complaint submit 3/min/user,
  and presign 10/min/user. Login and both password-reset endpoints use
  `RateLimitService` (5/15 min/IP). All rejections are RFC 7807-shaped `429`
  responses with `Retry-After`. The limiter is in-memory/single-instance.
- **§4.3 Password reset:** public `/auth/forgot-password` and
  `/auth/reset-password`; SHA-256 token hashes, 30-minute one-time tokens,
  generic non-enumerating response, registration password policy, lockout
  clearing, refresh-family revocation, and an `auth.password_reset_requested`
  outbox row. Raw tokens are never returned by APIs or written to in-app
  notifications. The dispatcher ignores non-`notification.*` event types.
- **§4.4 Retention cleanup:** `DataRetentionService` deletes rows older than
  24 months from `audit_logs`, `ai_analyses`, and `ai_analysis_runs`. It is
  disabled by default (`CIVICSENSE_RETENTION_ENABLED=false`, cron `-`) and has
  no API.
- **§4.5 Security review decisions:** RLS remains disabled (V6 scaffolding is
  commented out); app-layer authorization is authoritative and no RLS
  enforcement is claimed. BCrypt strength 12 is retained; Argon2id is an
  evidence-based deferral with explicit future triggers. Dependency remediation
  moved the application from Boot 3.5.16 to Boot 4.2.0/springdoc 3.2.1 after an
  initial audit found 22 high/critical Maven advisories. `scripts/secret-scan.ps1`
  provides repeatable tracked-file scanning.
- **§5 Frontend:** `/forgot-password` and `/reset-password` public pages, login
  link, API functions, and reset token read from the query string only.
  Vitest is scoped to `src/**/*.{test,spec}.{ts,tsx}` to avoid loading Playwright
  specs. Five Playwright tests use local Microsoft Edge with deterministic API
  stubs.
- **§6 Database/configuration:** V13 and the H2 test schema add
  `password_reset_tokens` plus its active-token index. V1–V12 are unchanged.

### 9.2 Verification evidence

| Check | Result |
|---|---|
| `mvn -B test` | 127 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B package` | `BUILD SUCCESS`, repackaged JAR produced |
| `npm run test` | 6 files, 39 tests passed |
| `npm run lint` | exit 0; three pre-existing `react(set-state-in-effect)` warnings |
| `npm run build` | TypeScript + Vite build succeeded; non-blocking 500 kB chunk advisory |
| `npm run test:e2e` | 5 Playwright tests passed with local Microsoft Edge and stubbed APIs |
| `git diff --check` | no whitespace errors (LF→CRLF warnings only) |
| `scripts/dependency-audit.ps1` | npm: 0 vulnerabilities; OSV: 0 advisories across 212 Maven coordinates |
| `scripts/secret-scan.ps1` | 284 tracked files scanned; no credential-like patterns or secret files |

The dependency audit initially reported 22 high/critical Maven advisories on
Boot 3.5.16. The coherent Boot 4.2.0 migration (with springdoc 3.2.1 and Boot 4
modular starters) resolved them; compatibility changes were limited to
`HealthIndicator`/`AutoConfigureMockMvc` package moves and POM starter names.

### 9.3 Boundaries and limitations

Browser E2E verifies local UI workflows with stubbed APIs; it does not verify
hosted Supabase/PostGIS, real Flyway V1–V13 on PostgreSQL, production storage,
email/push providers, or real AI-provider calls. Phase 1 §1.1 remains open.
Password reset emits an outbox event only; no email is sent until a real email
channel exists. Rate limiting is single-instance, RLS is disabled, and
retention scheduling is disabled by default. No production auth bypass, fake
production data, external integration claim, or Phase 11 deliverable was added.

### 9.4 Completion gate

Phase 10 was completed and submitted for explicit owner review. Owner approval
was received on 2026-11-22; the implementation is checkpointed locally as
`phase10-approved` before Phase 11 starts. No push was performed.
