# Phase 8 implementation contract (working document)

All paths are relative to `/api/v1`. Every route below is enforced server-side with
`@PreAuthorize("hasRole('SUPER_ADMIN')")` in addition to the `/admin/**` URL rule.

## 1. Preserve existing contracts

Do not break or rename these existing endpoints:

- `GET /admin/departments`
- `POST /admin/departments/{id}/verify`
- `GET /admin/complaints`
- `GET /admin/complaints/unassigned`
- `GET /admin/complaints/{id}`
- `GET /admin/complaints/{id}/routing-decisions`
- `POST /admin/complaints/{id}/reroute`
- `POST /admin/complaints/{id}/assign`
- `POST /admin/complaints/{id}/unassign`
- `GET /admin/reassignment-requests`
- `POST /admin/reassignment-requests/{id}/review`
- `POST /admin/reference/categories`
- `PATCH /admin/reference/categories/{id}`
- `POST /admin/reference/jurisdictions`

The rich global queue is new and must be `/admin/complaints/queue` so it cannot
conflict with `/admin/complaints/{id}`.

## 2. New/expanded backend endpoints

### Platform insight

- `GET /admin/overview?periodDays=30`
- `GET /admin/analytics?periodDays=30`
- `GET /admin/activity?limit=20`

Response shape (all numbers come from persisted rows):

```json
{
  "periodDays": 30,
  "stats": {
    "total": 0, "resolved": 0, "pending": 0, "unassigned": 0, "overdue": 0,
    "failedAi": 0, "averageResolutionHours": 0.0, "slaCompliancePercent": 0.0
  },
  "governance": {
    "pendingDepartments": 0, "suspendedDepartments": 0, "ambiguous": 0,
    "lowConfidence": 0, "failedAi": 0, "reassignmentRequests": 0
  },
  "byStatus": [{"key":"ASSIGNED","label":"Assigned","count":0}],
  "byCategory": [{"key":"ROAD","label":"Roads","count":0}],
  "byDepartment": [{"key":"uuid","label":"Roads Department","count":0}],
  "byJurisdiction": [{"key":"uuid","label":"Central","count":0}],
  "activity": [{"complaintId":"uuid","complaintNumber":"CIV-...","fromStatus":"SUBMITTED","toStatus":"ASSIGNED","actorRole":"SYSTEM","reason":"...","createdAt":"..."}],
  "audit": [{"id":"uuid","actorName":"...","actorRole":"SUPER_ADMIN","action":"DEPARTMENT_VERIFY","entityType":"department","entityId":"uuid","reason":"...","createdAt":"...","correlationId":"..."}]
}
```

`overview` includes `activity` and `audit`; `analytics` returns the same object
without those two arrays. `periodDays` is clamped to 1–365.

### Department governance

- `GET /admin/departments?verificationStatus=&isActive=&departmentType=&search=`
  — returns the existing `DepartmentSummary[]`, extended where needed by detail endpoint.
- `GET /admin/departments/{id}` — `AdminDepartmentDetail`.
- `PATCH /admin/departments/{id}/status` — `{ "status": "VERIFIED|REJECTED|SUSPENDED", "reason": "..." }`.
- `PATCH /admin/departments/{id}/operations` — `{ "routingPriority": 0..10000, "maxOpenComplaints": 1..10000|null, "autoAssignEnabled": boolean, "reason": "..." }`.

Rules: every status/operations change requires a 10–1000 character reason,
is audited with that reason, and takes effect immediately through the DB-backed
guards. `PENDING_VERIFICATION` is not a valid manual target. Reactivating uses
`VERIFIED`, not a separate endpoint.

### User management

- `GET /admin/users?role=&status=&departmentId=&search=&page=0&size=20`
- `PATCH /admin/users/{id}/status` — `{ "status": "ACTIVE|SUSPENDED|DISABLED", "reason": "..." }`
- `PATCH /admin/users/{id}/role` — `{ "role": "CITIZEN|DEPARTMENT_ADMIN|SUPER_ADMIN", "reason": "..." }`

Rules: admin cannot change own status/role; cannot remove the last active
super admin; reason is mandatory (10–1000) and audited. Suspending/disabling a
user revokes refresh-token families. `DepartmentSummary` is reused for filters.

### Complaint queue and AI/routing governance

- `GET /admin/complaints/queue` — accepts the Phase 7 queue parameters plus
  `departmentId`, `routingOutcome`, and `aiStatus`; returns the existing
  `PagedResponse<ComplaintSummary>` envelope.
- `GET /admin/complaints/{id}/analyses` — returns append-only AI history:
  `{ "analyses": [{ "id","status","source","isCivicIssue","categoryCode","subcategoryCode","severity","description","confidence","requiresHumanReview","adminOverrideReason","createdAt" }] }`.
  `source` is `AI` or `ADMIN_OVERRIDE`; original AI rows are never mutated.
- `PATCH /admin/complaints/{id}/classification` — existing endpoint extended with
  optional `severity` and `description`; still requires `reason` and appends an
  `ADMIN_OVERRIDE` analysis, then forces reroute.
- Existing `POST /admin/complaints/{id}/reroute`, `/assign`, `/unassign`, and
  reassignment `/review` remain the only mutation paths. Every one records a
  reason and audit entry.
- `GET /uploads/{imageId}/url` must also authorize an authenticated `SUPER_ADMIN`
  so evidence can be inspected.

### Reference / service-area oversight

- `GET /admin/categories?includeInactive=true` — reuse `ReferenceService.categoryTree`.
- `GET /admin/jurisdictions?includeInactive=true` — reuse `ReferenceService.jurisdictions`.
- `GET /admin/service-areas?departmentId=&active=` — platform-wide read model using
  existing `ServiceAreaResponse`. No jurisdiction polygon write API is added;
  lack of a WKT geometry column remains a documented limitation.

### Audit exploration

- `GET /admin/audit-logs?action=&entityType=&entityId=&actorId=&from=&to=&page=0&size=20`
  — returns `PagedResponse<AuditLogEntry>` with actor, role, action, target,
  reason, correlation id, and timestamp. New `audit_logs.reason` column in
  migration V11; H2 test schema mirrors it.

## 3. Security and integrity

- Backend role checks are mandatory; frontend guards are convenience only.
- No client-supplied actor, department id for dept-scoped operations, or audit fields.
- Administrative changes require server-side validation and a reason.
- Preserve AI analyses, routing decisions, assignment history, and status history.
- AI output remains advisory; only human/admin actions can assign departments.
- No secrets, raw provider responses, storage keys, or auth tokens in admin DTOs.

## 4. Frontend routes

All routes are wrapped in `RequireRole(['SUPER_ADMIN'])`:

- `/admin` — platform dashboard
- `/admin/departments` — verification/suspension/operations
- `/admin/users` — user management
- `/admin/complaints` — global queue
- `/admin/complaints/:id` — AI/routing governance detail
- `/admin/platform` — categories, jurisdictions, service areas
- `/admin/audit` — audit exploration
- `/admin/*` — redirect to `/admin`

Use existing shared UI (`AppLayout`, `PageHeader`, `StatCard`, `Card`, `Badge`,
`StatePanel`, `StatusBadge`, `StatusTimeline`, `Button`, `Field`, `inputClass`).
AI rows must show a `Generated by AI` badge; override rows must show
`Administrator override` plus actor/reason. Routing candidate reasons must be
visible. Destructive actions require confirmation. All screens need loading,
empty, error/retry, and responsive table/card states. No hardcoded stats.

## 5. Verification commands

```powershell
cd E:\CivicSense\backend
mvn -B test
mvn -B package

cd E:\CivicSense\frontend
$env:PATH = 'E:\CivicSense\tools\node\node-v26.13.1-win-x64;' + $env:PATH
npm run test
npm run lint
npm run build
```

## 6. Delivery status (2026-11-21)

Implemented and locally verified. Backend: 107 tests passed in the full suite, including 14 Phase 8 tests; `mvn -B package` succeeded. Frontend: 30 tests passed in 4 files; lint exited 0 with only pre-existing warnings; production build succeeded. `git diff --check` found no whitespace errors. Hosted Supabase/PostGIS, Flyway V1–V11 on real PostgreSQL, production object storage, real AI-provider calls, and browser E2E remain unverified. See `DEVELOPMENT_ROADMAP.md §12` and `API_DOCUMENTATION.md §17` for the full report.
