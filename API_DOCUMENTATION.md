# CivicSense — REST API Documentation

| Field | Value |
|---|---|
| Document | `API_DOCUMENTATION.md` |
| Version | 0.9 (Phase 12 hardening documented) |
| Status | Phase 0 design contract; implemented subsets marked in §2.1, §5, §11–§14, §16–§21 |
| Last updated | 2026-11-22 |
| Base URL (dev) | `http://localhost:8080/api/v1` |
| Spec generation | Springdoc OpenAPI 3 at `/api/v1/swagger-ui.html`, JSON at `/api/v1/v3/api-docs` |

---

## 1. Conventions

### 1.1 Authentication
All endpoints except those marked **public** require:

```
Authorization: Bearer <access_token>
```

Access tokens expire in 15 minutes (configurable). Refresh via `POST /auth/refresh` using the `httpOnly` refresh cookie (7-day lifetime, rotating on every refresh). Tokens carry `sub` (user id), `email`, `role`, and `deptId` (for `DEPARTMENT_ADMIN`) claims. Department verification is **re-read from the database on every request** (never trusted from the JWT), so verification takes effect immediately without re-login. All authentication and authorization is performed by Spring Boot; Supabase Auth is not used.

### 1.2 Content types
- Requests/responses: `application/json; charset=utf-8`
- File upload: `multipart/form-data` only where explicitly noted; binary upload goes directly to S3 with a presigned URL.

### 1.3 Pagination & sorting
List endpoints accept:

```
?page=0&size=20&sort=createdAt,desc
```

Response envelope:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "last": false
}
```

### 1.4 Errors (RFC 7807)

```json
{
  "type": "https://civicsense.app/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body contains invalid fields",
  "instance": "/api/v1/citizen/complaints",
  "correlationId": "9f1c…",
  "errors": [
    { "field": "description", "message": "must be between 10 and 2000 characters" }
  ]
}
```

| Status | Meaning |
|---|---|
| 400 | Validation / malformed request |
| 401 | Missing, expired, or invalid access token |
| 403 | Authenticated but not permitted (wrong role, wrong department, unverified department) |
| 404 | Resource not found **or** outside the caller's department scope (intentionally indistinguishable) |
| 409 | Illegal state transition / business conflict |
| 400 | File too large or unsupported type (presign stage) |
| 422 | Semantic validation failure (e.g. invalid GeoJSON polygon) |
| 429 | Rate limit exceeded (`Retry-After` header) |
| 500 | Unexpected server error (correlation ID always returned) |
| 503 | External provider unavailable — note: complaint submission still succeeds; only the analysis is deferred |

### 1.5 Correlation IDs
Send `X-Correlation-Id` (UUID) optionally; it is echoed in the response and written to audit/AI logs. If absent, the server generates one.

### 1.6 Role legend
- **PUB** = public, **CIT** = citizen, **DEPT** = department admin (verified), **SUPER** = super administrator, **ANY** = any authenticated user.

### 1.7 Contract synchronisation
Frontend TypeScript types in `frontend/src/lib/api/` are hand-maintained against the implemented contracts in §11–§13. `openapi-typescript` generation is deferred because the generator's TypeScript peer range excludes this project's TypeScript version.

---

## 2. Authentication & account

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/register/citizen` | PUB | Create citizen account |
| POST | `/auth/register/department` | PUB | Create department + owner admin (`PENDING_VERIFICATION`) |
| POST | `/auth/login` | PUB | Issue access token + refresh cookie |
| POST | `/auth/refresh` | PUB | Rotate refresh token, issue new access token |
| POST | `/auth/logout` | ANY | Revoke refresh token, clear cookie |
| GET | `/auth/me` | ANY | Current user, role, verification status, department context |
| POST | `/auth/forgot-password` | PUB | Phase 10: tokenised reset email via outbox |
| POST | `/auth/reset-password` | PUB | Phase 10 |

### `POST /auth/register/citizen`
```json
{
  "fullName": "Asha Raman",
  "email": "asha@example.com",
  "password": "Str0ng!Passw0rd",
  "phone": "+919876543210",
  "locale": "en"
}
```
`201` → `{ "userId": "…", "message": "Registration successful" }`. Password: ≥10 chars, upper+lower+digit+special (Zod + Bean Validation).

### `POST /auth/register/department`
```json
{
  "department": {
    "name": "Road Maintenance Department",
    "departmentType": "ROAD_MAINTENANCE",
    "officialEmail": "roads@city.gov.example",
    "contactNumber": "+912233445566",
    "description": "Maintains city roads and footpaths.",
    "officeAddress": "12 Civic Plaza",
    "city": "Bengaluru", "state": "Karnataka", "country": "India",
    "responsibleCategoryCodes": ["ROAD_MAINTENANCE", "POTHOLE", "DAMAGED_FOOTPATH"],
    "serviceAreas": [
      {
        "name": "Ward 12",
        "jurisdictionCode": "WARD-12",
        "boundary": { "type": "MultiPolygon", "coordinates": [ ... ] }
      }
    ]
  },
  "administrator": {
    "fullName": "R. Kumar",
    "email": "r.kumar@city.gov.example",
    "password": "Str0ng!Passw0rd",
    "phone": "+919900001111"
  }
}
```
`201` → `{ "departmentId": "…", "verificationStatus": "PENDING_VERIFICATION", "message": "Application submitted for verification" }`.
Department email must differ from personal email domains of existing accounts where possible; duplicate official email → `409`. GeoJSON validated with `ST_IsValid` → `422` on invalid polygon.

### `POST /auth/login`
```json
{ "email": "asha@example.com", "password": "…" }
```
`200` →
```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "…", "fullName": "Asha Raman", "role": "CITIZEN",
    "departmentId": null, "verificationStatus": null
  }
}
```
Refresh token is set as `httpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. Failed attempts increment lockout counters; after 5 failures → 15-minute lock (429).

### `GET /auth/me`
```json
{
  "id": "…", "email": "asha@example.com", "fullName": "Asha Raman",
  "role": "CITIZEN", "accountStatus": "ACTIVE", "verificationStatus": null,
  "departmentId": null, "permissions": ["COMPLAINT_SUBMIT", "COMPLAINT_VIEW_OWN"]
}
```

### 2.1 Phase 2 implemented subset (2026-11-21)

The endpoints below are **implemented and covered by automated tests** (H2 test double). Fields not listed here remain part of the Phase 0 design contract but are not yet accepted by the server — they arrive in Phase 3.

| Method | Path | Access | Implemented behaviour |
|---|---|---|---|
| POST | `/auth/register/citizen` | PUB | Creates `CITIZEN` with `ACTIVE` status; `201` → `{ "message": "Registration successful" }` |
| POST | `/auth/register/department` | PUB | Creates department (`PENDING_VERIFICATION`) + owner `DEPARTMENT_ADMIN`; `201` → `{ "departmentId", "verificationStatus", "message" }` |
| POST | `/auth/login` | PUB | `200` → `TokenResponse`; sets rotating `civicsense_refresh` cookie. 5 failed attempts → 15-minute lock (429). |
| POST | `/auth/refresh` | PUB | Rotates refresh token; reuse of a rotated token revokes the **entire token family** (401). |
| POST | `/auth/logout` | ANY | Revokes the presented refresh token and clears the cookie; `204`. |
| GET | `/auth/me` | ANY | `MeResponse` incl. live `verificationStatus` + `permissions`. |
| GET | `/department/me` | DEPT + verified | `DepartmentContextResponse`; `403` while the department is pending/rejected. |
| GET | `/admin/departments` | SUPER | List of `DepartmentSummary`. |
| POST | `/admin/departments/{id}/verify` | SUPER | `{ "decision": "VERIFIED" \| "REJECTED", "reason"?: string }`; `VERIFIED` also sets `acceptsComplaints=true`. |

Notes on the differences from the Phase 0 contract above:

- Citizen registration returns only `message` (no `userId`) in Phase 2.
- Department registration accepts identity fields only; `responsibleCategoryCodes` and `serviceAreas` (GeoJSON) are added with PostGIS support in Phase 3. Unknown fields are ignored by Jackson.
- Verification uses the field `reason` (not `note`) and accepts only `VERIFIED` / `REJECTED`; `SUSPENDED` arrives with status management in Phase 8.
- `GET /department/me` began as a Phase 2 scope probe; the full department dashboard is now documented in §6 and §16.
- `POST /auth/logout` is `anyRequest().authenticated()`: send the bearer header along with the cookie.
- Refresh cookie flags: `HttpOnly; SameSite=Strict; Path=/api/v1/auth`; `Secure` is enabled when `CIVICSENSE_REFRESH_COOKIE_SECURE=true`.
- Errors use the RFC 7807 shape from §1.4 with a `correlationId` on every failure.

---

## 3. Public metadata

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/meta/categories` | PUB | Active category tree for the report form |
| GET | `/meta/config` | PUB | Map provider, tile URL, max upload size, allowed image types, supported locales |
| GET | `/meta/health` | PUB | Liveness/ping (full health via Actuator `/actuator/health`) |

`GET /meta/categories` →
```json
[
  {
    "code": "ROAD_MAINTENANCE", "name": "Road Maintenance", "icon": "road",
    "children": [
      { "code": "POTHOLE", "name": "Pothole" },
      { "code": "DAMAGED_FOOTPATH", "name": "Damaged footpath" }
    ]
  }
]
```

---

## 4. Uploads & images (Phase 5 — implemented)

All paths are relative to `/api/v1`. Every endpoint requires a bearer token; the `complaint_images` row is created and owned by the authenticated user at presign time. Supabase Storage is accessed only through the S3-compatible `ObjectStorage` abstraction.

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/uploads/presign` | ANY authenticated user | Validates type/size/count, creates a `PENDING` image row, returns a short-lived presigned PUT URL. |
| PUT | `<uploadUrl>` | Capability URL | Browser sends bytes directly to S3-compatible storage. No CivicSense bearer token is sent. |
| POST | `/uploads/{imageId}/confirm` | Upload owner only | Reads the stored object, verifies magic bytes/dimensions, stores SHA-256 + 8×8 perceptual hash, sets `VERIFIED`. |
| DELETE | `/uploads/{imageId}` | Upload owner only | Deletes an unattached image from storage and removes its row. Attached images are rejected with `400`. |
| GET | `/uploads/{imageId}/url` | Upload owner or complaint owner | Returns a short-lived presigned GET URL for a `VERIFIED` image. |

Allowed `contentType`: `image/jpeg`, `image/png`, `image/webp`. Maximum 10 MB per image and 5 staged images per user (`CIVICSENSE_AI_MAX_IMAGES`). The declared type is not trusted: confirm re-reads the object and compares magic bytes, rejects more than 10 MB, decodes the image, and computes metadata server-side.

### `POST /uploads/presign`
```json
{
  "fileName": "pothole.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 1843200,
  "imageKind": "CITIZEN"
}
```
`imageKind`: `CITIZEN`, `BEFORE`, `AFTER`, `EVIDENCE`. `200` →
```json
{
  "imageId": "6d8e…",
  "uploadUrl": "https://…/civic-images/uploads/<userId>/<uuid>.jpg?X-Amz-…",
  "storageKey": "uploads/<userId>/<uuid>.jpg",
  "contentType": "image/jpeg",
  "expiresAt": "2026-11-21T12:05:00Z",
  "headers": { "Content-Type": "image/jpeg" }
}
```

### `POST /uploads/{imageId}/confirm`
`200` →
```json
{
  "imageId": "6d8e…",
  "complaintId": null,
  "storageKey": "uploads/<userId>/<uuid>.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 1843200,
  "width": 4032,
  "height": 3024,
  "uploadStatus": "VERIFIED",
  "createdAt": "2026-11-21T11:15:30Z"
}
```
`400` → `Invalid upload` when bytes do not match the declared type or the image is undecodable; `404` if the object was never uploaded; `503` `Storage unavailable` for storage failures. A failed confirmation leaves the row `PENDING`.

### `GET /uploads/{imageId}/url`
`200` → `{ "imageId": "6d8e…", "url": "https://…?X-Amz-…", "expiresAt": "…" }`. URLs are capability URLs with a short TTL (`CIVICSENSE_UPLOAD_VIEW_TTL_SECONDS`, default 300). Authorization is checked **before** a URL is generated; no permanent public URL is returned.

---

## 5. Citizen API

All require `ROLE_CITIZEN`.

| Method | Path | Description |
|---|---|---|
| GET | `/citizen/overview` | Own counts + category/status distribution |
| POST | `/citizen/complaints` | Submit a complaint |
| GET | `/citizen/complaints` | Own complaint list with filters |
| GET | `/citizen/complaints/{id}` | Full detail incl. AI analysis, assignment, department name |
| GET | `/citizen/complaints/{id}/timeline` | Status history + comments |
| POST | `/citizen/complaints/{id}/comments` | Add public comment / reply to info request |
| POST | `/citizen/complaints/{id}/reopen` | Dispute resolution (reason required) |
| POST | `/citizen/complaints/{id}/feedback` | Rating + confirmation after resolution |
| GET | `/citizen/notifications` | In-app notification list |
| PATCH | `/citizen/notifications/{id}/read` | Mark one read |
| POST | `/citizen/notifications/read-all` | Mark all read |
| GET | `/citizen/profile` | Profile + preferences |
| PATCH | `/citizen/profile` | Update profile/default location/preferences |
| POST | `/geo/reverse-geocode` | Coordinates → address components (cached, rate-limited) |

### `POST /citizen/complaints` (Phase 5 request shape)
```json
{
  "description": "Large pothole near the main market. Vehicles are having difficulty passing through.",
  "additionalInfo": "It has grown after the rains.",
  "latitude": 12.9716,
  "longitude": 77.5946,
  "locationSource": "GPS",
  "addressLine": "Main Road",
  "city": "Bengaluru",
  "imageIds": ["6d8e…", "9c21…"]
}
```
`locationSource`: `GPS`, `MANUAL` or `GEOCODED`. `imageIds` may contain only `VERIFIED` uploads owned by the caller and not already attached. Ownership, status and the 5-image limit are re-checked before any complaint row is written; a rejection returns `400`/`403` and creates no complaint.

`200` → the full `ComplaintDetail` (the same shape as `GET /citizen/complaints/{id}`), including:
```json
{
  "id": "…",
  "complaintNumber": "CIV-2026-000123",
  "status": "AI_ANALYSIS_PENDING",
  "images": [
    {
      "id": "6d8e…", "storageKey": "uploads/…", "contentType": "image/jpeg",
      "sizeBytes": 1843200, "imageKind": "CITIZEN", "uploadStatus": "VERIFIED",
      "createdAt": "2026-11-21T11:15:30Z"
    }
  ],
  "analysis": {
    "id": "…", "status": "PENDING", "requiresHumanReview": false,
    "errorMessage": null, "createdAt": "2026-11-21T11:15:30Z"
  }
}
```
Without images, status is `SUBMITTED`, `images` is empty and `analysis` is `null`. With images, the complaint is committed first and the analysis event is published after commit; provider failure can never lose the complaint. The original Phase 0 `possibleDuplicate` response field and `duplicate_of_id` auto-flagging are **not** implemented in Phase 5; the perceptual hash is stored for later duplicate detection.

### `GET /citizen/complaints` filters
`status`, `categoryCode`, `from`, `to`, `search` (complaint number/text), plus pagination/sort.

### `POST /citizen/complaints/{id}/reopen`
```json
{ "reason": "The pothole is still present after the work was marked complete." }
```
Requires status `RESOLVED`. → `200` with status `REOPENED`. Mandatory reason (min 20 chars).

### `POST /citizen/complaints/{id}/feedback`
```json
{ "rating": 4, "comment": "Quick response.", "resolutionConfirmed": true }
```
Requires status `RESOLVED`. `resolutionConfirmed: true` → `CLOSED`; `false` requires `disputeReason` → `REOPENED`.

### `POST /geo/reverse-geocode`
```json
{ "latitude": 12.9716, "longitude": 77.5946 }
```
→
```json
{
  "addressLine": "12, Market Road", "city": "Bengaluru",
  "state": "Karnataka", "postalCode": "560001", "country": "India",
  "ward": "WARD-12", "zone": "ZONE-3", "provider": "nominatim"
}
```

---

## 6. Department API

All routes below are guarded by `@PreAuthorize("@departmentGuard.isVerified(authentication)")`. They require `ROLE_DEPARTMENT_ADMIN`, an `ACTIVE` admin account, and a `VERIFIED`, active department. Verification is re-read from the database per request. The department ID is always taken from the JWT `deptId`; no route accepts a department ID from the client.

| Method | Path | Description |
|---|---|---|
| GET | `/department/me` | Department context carried by the current identity |
| GET | `/department/overview?periodDays=30` | `{ context, analytics, activity }` dashboard payload |
| GET | `/department/analytics?periodDays=30` | Real persisted aggregate metrics |
| GET | `/department/activity?limit=20` | Recent status-history entries for own department |
| GET | `/department/complaints` | **Legacy** plain array; kept for Phase 2–6 clients |
| GET | `/department/complaints/queue` | Paged/filtered/sorted complaint queue |
| GET | `/department/complaints/{id}` | Detail: photos, AI analysis, map point, routing decision, history, private notes |
| POST | `/department/complaints/{id}/accept` | `ASSIGNED` → `ACKNOWLEDGED` |
| POST | `/department/complaints/{id}/reject` | Reason-required rejection |
| POST | `/department/complaints/{id}/request-info` | Citizen-visible comment + notification; status unchanged |
| POST | `/department/complaints/{id}/reassign-request` | Ask super admin to reassign |
| PATCH | `/department/complaints/{id}/status` | Guarded status transition |
| POST | `/department/complaints/{id}/resolve` | Resolution summary + evidence gate |
| POST | `/department/complaints/{id}/evidence` | Attach a verified uploaded image as evidence |
| GET/POST | `/department/complaints/{id}/notes` | List/add department-private internal notes |
| GET/PATCH | `/department/profile` | Read/update own department profile and intake settings |
| GET/PUT | `/department/capabilities` | Read/replace category capabilities |
| GET | `/department/team` | List own department admins/operators |
| PATCH | `/department/team/{userId}` | Change own member's level/primary flag |
| GET/POST | `/department/service-areas` | List/create own service areas |
| PATCH/DELETE | `/department/service-areas/{id}` | Update/deactivate own service area |
| GET | `/department/notifications` | Department-prefixed alias of recipient-scoped notification list |

Unknown/foreign complaint IDs return `404`, deliberately indistinguishable from missing resources. Unverified, inactive or wrong-role callers receive `403`. Illegal transitions receive `409`; a missing reason or missing resolution evidence receives `422`.

### `GET /department/complaints/queue`

Default: `page=0`, `size=20`, `sort=createdAt,desc`. `size` is clamped to 1–100. Supported sort properties: `createdAt`, `updatedAt`, `status`, `severity`, `complaintNumber`, `resolutionDueAt`; unknown properties fall back to `createdAt,desc`.

Supported filters:

| Parameter | Type / notes |
|---|---|
| `status` | Repeatable `ComplaintStatus` |
| `categoryCode`, `ward`, `zone` | Exact persisted dimension values |
| `severity` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `search` | Case-insensitive match on number, description, address, ward, city |
| `overdue` | `true` returns active complaints past `resolutionDueAt`; `false` excludes them |
| `aiAssigned` | `true` requires an active `AUTO` assignment row; `false` excludes it |
| `from`, `to` | ISO-8601 instants applied to `createdAt` |

Response:

```json
{
  "content": [{ "id": "…", "complaintNumber": "CIV-2026-000015", "description": "…", "status": "ASSIGNED", "severity": "HIGH", "categoryCode": "ROAD", "ward": "WARD-12", "city": "New Delhi", "departmentId": "…", "createdAt": "…" }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

### `GET /department/analytics`

Response fields (all derived from own-department rows):

```json
{
  "total": 412,
  "byStatus": { "ASSIGNED": 38, "IN_PROGRESS": 73, "RESOLVED": 301 },
  "resolved": 301, "overdue": 12, "unassigned": 0,
  "averageResolutionHours": 19.4,
  "periodDays": 30,
  "receivedInPeriod": 46, "resolvedInPeriod": 41,
  "pending": 0, "accepted": 38, "inProgress": 73, "rejected": 4, "closed": 301,
  "byCategory": [{ "code": "ROAD", "name": "Roads", "count": 156 }],
  "byWard": [{ "ward": "WARD-12", "count": 44 }],
  "trend": [{ "period": "2026-11-21", "received": 7, "resolved": 5 }],
  "averageOpenAgeHours": 42.1, "slaCompliancePercent": 91.2
}
```

`periodDays` is clamped to 1–365 (default 30). Trend buckets are daily for periods ≤31 days, weekly otherwise; only dates inside the selected period are counted.

### `GET /department/activity`

Returns `ActivityEntry[]`:

```json
[{
  "complaintId": "…", "complaintNumber": "CIV-2026-000015",
  "fromStatus": "SUBMITTED", "toStatus": "ASSIGNED",
  "actorRole": "SUPER_ADMIN", "reason": "Routed to Roads Department",
  "createdAt": "…"
}]
```

### Action request bodies

```json
// POST /reject  — reason 10..1000
{ "reason": "This falls under the Water Supply Department's jurisdiction." }

// POST /request-info — body 1..2000
{ "body": "Please upload a photo of the damaged section." }

// PATCH /status — reason optional but policy-dependent
{ "status": "IN_PROGRESS", "reason": "Repair crew dispatched" }

// POST /resolve — summary 10..2000; requires AFTER or EVIDENCE already attached
{ "summary": "Pothole filled and road swept; inspection photo attached." }

// POST /notes — body 1..2000, department-private
{ "body": "Site inspection scheduled for Tuesday." }

// POST /evidence — confirmed upload owned by the acting admin
{ "imageId": "…" }

// POST /reassign-request — reason 10..1000
{ "toDepartmentId": "…", "reason": "Blocked drain requires the Drainage Department." }
```

`POST /evidence` responds `{ "status": "ATTACHED", "imageId": "…" }`; `POST /reassign-request` responds `{ "status": "PENDING" }`. The frontend performs `POST /uploads/presign` → direct `PUT` → `POST /uploads/{imageId}/confirm` before calling `/evidence`; a failed attach discards the staged upload.

### Profile, capabilities, team and service areas

```json
// PATCH /department/profile
{
  "description": "Maintains roads and footpaths.",
  "contactNumber": "022-12345678",
  "officeAddress": "Civic Centre",
  "acceptsComplaints": true,
  "autoAssignEnabled": true
}

// PUT /department/capabilities — exactly one primary, active categories only
{
  "capabilities": [
    { "categoryId": "…", "primary": true, "priority": 1 },
    { "categoryId": "…", "primary": false, "priority": 2 }
  ]
}

// PATCH /department/team/{userId}
{ "adminLevel": "OPERATOR", "primaryAdmin": false }

// POST /department/service-areas
{
  "name": "North Ward", "boundaryWkt": "MULTIPOLYGON (((...)))",
  "coversWard": "WARD-12", "coversZone": "NORTH"
}

// PATCH /department/service-areas/{id} — full replace, boundaryWkt required
{
  "name": "North Ward", "boundaryWkt": "MULTIPOLYGON (((...)))",
  "coversWard": "WARD-12", "coversZone": "NORTH"
}
```

`GET /department/service-areas` returns the existing `ServiceAreaResponse` shape and intentionally omits `boundaryWkt`; editing a boundary requires resubmitting the WKT. `DELETE` marks the area `INACTIVE` and responds `{ "status": "INACTIVE" }`.

---

## 7. Super administrator API

All require `ROLE_SUPER_ADMIN`.

| Method | Path | Description |
|---|---|---|
| GET | `/admin/overview?periodDays=30` | Platform KPIs, governance counts, breakdowns, activity and audit from persisted rows |
| GET | `/admin/analytics?periodDays=30` | Same aggregate payload as overview without `activity` and `audit` |
| GET | `/admin/activity?limit=20` | Recent status-history entries |
| GET | `/admin/departments` | Filter by `verificationStatus`, `isActive`, `departmentType`, `search` |
| GET | `/admin/departments/{id}` | Full application incl. service areas and operational counters |
| POST | `/admin/departments/{id}/verify` | Approve/reject application (`reason` required for REJECTED/SUSPENDED) |
| PATCH | `/admin/departments/{id}/status` | `VERIFIED|REJECTED|SUSPENDED`; suspension takes effect on existing sessions |
| PATCH | `/admin/departments/{id}/operations` | Routing priority, capacity, auto-assign flag; reason required |
| GET | `/admin/users` | Paged citizens + department admins, filter by role/status/department/search |
| PATCH | `/admin/users/{id}/status` | Suspend/enable/disable user; reason required |
| PATCH | `/admin/users/{id}/role` | Controlled role change; reason required, audited |
| GET | `/admin/complaints` | Legacy global complaint list (unchanged) |
| GET | `/admin/complaints/unassigned` | Unassigned/pending-assignment review queue (unchanged) |
| GET | `/admin/complaints/queue` | New paged platform queue with routing/AI/department filters |
| GET | `/admin/complaints/{id}` | Full detail incl. AI analyses + routing decisions |
| GET | `/admin/complaints/{id}/analyses` | Append-only AI/admin-override history (no raw payloads) |
| POST | `/admin/complaints/{id}/assign` | Manual (re)assignment with reason |
| POST | `/admin/complaints/{id}/unassign` | Return to queue with reason |
| PATCH | `/admin/complaints/{id}/classification` | Override category/subcategory plus optional severity/description; appends `ADMIN_OVERRIDE` analysis and reroutes |
| POST | `/admin/complaints/{id}/reroute` | Force a new routing decision with a mandatory reason |
| GET | `/admin/complaints/{id}/routing-decisions` | Decision audit trail |
| GET | `/admin/complaints/{id}/duplicates` | Flagged duplicates |
| POST | `/admin/complaints/{id}/dismiss-duplicate` | Clear duplicate flag |
| GET | `/admin/reassignment-requests` | Queue (`?status=PENDING`) |
| POST | `/admin/reassignment-requests/{id}/review` | Approve or reject with reason; history preserved |
| GET | `/admin/categories?includeInactive=` | Platform read of the reference category tree |
| GET | `/admin/jurisdictions?includeInactive=` | Platform read of wards/zones |
| GET | `/admin/service-areas?departmentId=&active=` | Platform-wide service-area read model |
| GET/POST | `/admin/reference/categories` | Existing category administration |
| PATCH/DELETE | `/admin/categories/{id}` | Update / deactivate |
| GET/POST | `/admin/reference/jurisdictions` | Existing jurisdiction administration |
| PATCH/DELETE | `/admin/jurisdictions/{id}` | Update / deactivate |
| POST | `/admin/jurisdictions/{id}/boundary` | Upload/update polygon (design contract; not added in Phase 8) |
| GET | `/admin/audit-logs` | Paged audit exploration by actor, action, entity and date |
| GET/PATCH | `/admin/settings` | Platform settings (thresholds, SLA, map precision) |

### `POST /admin/departments/{id}/verify`
```json
{ "decision": "VERIFIED", "reason": "Documents confirmed during onboarding review." }
```
`decision`: `VERIFIED` | `REJECTED` | `SUSPENDED`. The Phase 8 request uses `reason` (the older `note` wording is superseded). On `VERIFIED`: `accepts_complaints=true`, `account_status=ACTIVE`, notification to owner admin, audit log. On `REJECTED`/`SUSPENDED`: `reason` is mandatory (10–1000 characters) and audited. `PENDING_VERIFICATION` is not a manual target.

### `PATCH /admin/complaints/{id}/classification` (AI override)
```json
{
  "categoryCode": "WATER",
  "subcategoryCode": "LEAK",
  "severity": "HIGH",
  "description": "Field inspection confirmed a leaking water main.",
  "reason": "Supervisor confirmed the reported issue is a water leak."
}
```
`severity` and `description` are optional Phase 8 extensions; `description` is 10–2000 characters when supplied. Validates that both codes exist, are active and that the subcategory belongs to the category. Stores `admin_override_by_user_id`, `admin_override_at`, `admin_override_reason`, appends a new `ai_analyses` row with `status=SUCCESS` and `source=ADMIN_OVERRIDE` (original AI row kept), then forces routing. Reason mandatory (10–1000 chars). Audited as `COMPLAINT_CLASSIFICATION_OVERRIDE`. Raw provider payloads are never returned by the AI-history endpoint.

### `POST /admin/complaints/{id}/reroute`
```json
{ "reason": "Supervisor reassigned after field review." }
```
Runs `routing-v1` again regardless of whether a decision already exists. The previous current decision is retained with `current=false`; the new decision is `current=true`. A successful assignment uses `AssignmentType.ADMIN_CORRECTION`. Reason mandatory (min 10 chars). Audited as `COMPLAINT_REROUTE`.

### `POST /admin/complaints/{id}/assign`
```json
{ "departmentId": "…", "reason": "Manual correction after classification override." }
```
Creates an `ADMIN_CORRECTION` assignment, closes the previous active one, moves status to `ASSIGNED`, notifies new department + citizen.

### `GET /admin/complaints/{id}/routing-decisions`

Returns newest-first decisions. Each row has this shape:

```json
{
  "id": "…",
  "rulesetVersion": "routing-v1",
  "aiCategoryCode": "ROAD",
  "confidence": 0.92,
  "locationPointWkt": "POINT(77.209 28.6139)",
  "matchedJurisdictionId": null,
  "candidates": [
    {
      "departmentId": "…",
      "departmentName": "Road Maintenance",
      "geoScore": 1000,
      "geoMatch": "POLYGON",
      "catScore": 300,
      "catMatch": "SUBCATEGORY",
      "priority": 100,
      "total": 1400,
      "atCapacity": false,
      "reasons": ["service-area polygon contains point", "category capability 300"]
    }
  ],
  "selectedDepartmentId": "…",
  "outcome": "ASSIGNED",
  "reason": "Single highest-scoring eligible department (total 1400).",
  "current": true,
  "createdAt": "2026-11-21T12:00:00Z"
}
```

`outcome` is one of `ASSIGNED`, `UNASSIGNED`, `AMBIGUOUS`, `LOW_CONFIDENCE`, `NON_CIVIC`, `NO_CAPACITY`. Only one row per complaint has `current=true`; every run is retained for audit.

### `GET /admin/analytics` (real data)
```json
{
  "totalCitizens": 1823, "verifiedDepartments": 9, "totalComplaints": 6421,
  "resolutionPercentage": 87.3, "averageResolutionHours": 22.8,
  "byGeography": [{ "ward": "WARD-12", "count": 512 }],
  "mostFrequentIssues": [{ "code": "POTHOLE", "count": 1104 }],
  "departmentComparison": [
    { "departmentId": "…", "name": "Road Maintenance Department",
      "received": 1820, "resolved": 1621, "averageHours": 18.2 }
  ]
}
```

---

## 8. Notification types

| `type` | Recipient | Trigger |
|---|---|---|
| `COMPLAINT_SUBMITTED` | Citizen | Complaint committed |
| `COMPLAINT_ASSIGNED` | Citizen + dept admins | Routing selects department |
| `COMPLAINT_ACCEPTED` | Citizen | Dept accepts |
| `INFO_REQUESTED` | Citizen | Dept requests more info |
| `STATUS_CHANGED` | Citizen | Any guarded status change |
| `COMPLAINT_RESOLVED` | Citizen | Marked resolved |
| `COMPLAINT_REJECTED` | Citizen | Dept/super admin rejects |
| `COMPLAINT_REOPENED` | Dept admins + super admin | Citizen disputes |
| `DEPARTMENT_VERIFIED` | Dept owner | Super admin approves |
| `DEPARTMENT_REJECTED` | Dept owner | Super admin rejects |
| `DEPARTMENT_APPROVAL_REQUIRED` | Super admins | New registration |
| `UNASSIGNED_COMPLAINT` | Super admins | No eligible department |
| `REASSIGNMENT_REQUESTED` | Super admins | Dept requests reassignment |
| `INFO_RESPONDED` | Owning dept admins, or super admins if unassigned | Citizen answers an information request |
| `EVIDENCE_ATTACHED` | Citizen | Dept attaches verified evidence |
| `FEEDBACK_RECEIVED` | Owning dept admins + super admins | Citizen submits feedback |
| `COMPLAINT_CLOSED` | Citizen | Confirmed feedback closes the complaint |
| `REASSIGNMENT_APPROVED` | Destination dept admins | Super admin approves transfer |
| `REASSIGNMENT_REJECTED` | Requesting dept admins | Super admin rejects transfer |

Notification payload contract (Phase 9); `deliveryStatus` and `sentAt` are added to the existing response:
```json
{
  "id": "…", "type": "STATUS_CHANGED", "title": "Complaint is in progress",
  "body": "CIV-2026-000123 is now IN_PROGRESS.",
  "complaintId": "…", "complaintNumber": "CIV-2026-000123",
  "channel": "IN_APP", "deliveryStatus": "SENT", "readAt": null,
  "createdAt": "2026-11-21T11:02:00Z", "sentAt": "2026-11-21T11:02:01Z"
}
```

---

## 9. Authorization matrix (summary)

| Capability | CIT | DEPT | SUPER |
|---|:---:|:---:|:---:|
| Submit complaint | ✅ | — | — |
| View own complaints | ✅ | — | — |
| View dept complaints | — | ✅ (own dept only) | ✅ (all) |
| Accept/reject/status | — | ✅ (own dept only) | ✅ |
| Internal notes | — | ✅ (own dept only) | ✅ |
| Upload AFTER evidence | — | ✅ (own dept only) | ✅ |
| Verify departments | — | — | ✅ |
| Override AI / reassign | — | — | ✅ |
| Manage categories/jurisdictions | — | — | ✅ |
| View audit logs | — | — | ✅ |
| View other dept's complaints | ❌ | ❌ (404) | ✅ |

---

## 10. Phase 1 minimal surface (health check)

To verify the stack before auth exists (approved Phase 1 infrastructure: local Java 21 + portable Node, hosted Supabase for Postgres/PostGIS and S3-compatible storage; **no Docker**):

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/meta/health` | `{ "status": "UP", "version": "0.1.0", "time": "…", "database": "UP", "postgis": "UP", "objectStorage": "UP" }` |
| GET | `/api/v1/actuator/health` | `{ "status": "UP", "components": { "db": {"status":"UP"}, "postgis": {"status":"UP"}, "objectStorage": {"status":"UP"} } }` |
| GET | `/` (frontend :5173) | Landing / sign-in shell (Phase 2 adds login, register, and role-guarded pages) |

> **Status:** these hosted-service checks are **not verified yet**. Supabase credentials have not been configured; no real DB/PostGIS/S3 query has been executed. The paths above are the contract to verify in Phase 1 §1.1 of `DEVELOPMENT_ROADMAP.md`. The exact commands, migration sequence, backup/restore procedure, and logging guidance are consolidated in `docs/DEPLOYMENT.md` (Phase 11).

Phase 1 database verification requires a real `SELECT 1` and a real PostGIS spatial query (not just a TCP connection). Credentials come only from the root `.env` / session environment; placeholders live in `.env.example`. The same API contract applies unchanged after a future move to local PostgreSQL/PostGIS + MinIO.

---

## 11. Phase 3 implemented subset (2026-11-21)

All paths below are relative to the backend context path `/api/v1` (for example, `/citizen/complaints` is `GET /api/v1/citizen/complaints`). These endpoints are implemented and covered by local H2 integration tests. They have **not** been exercised against hosted Supabase/PostGIS.

### 11.1 Public metadata and reference administration

| Method | Path | Access | Behaviour |
|---|---|---|---|
| GET | `/meta/categories` | PUB | Returns the active category tree; `?includeInactive=true` adds inactive categories. |
| GET | `/meta/jurisdictions` | PUB | Returns active jurisdictions; `?includeInactive=true` adds inactive jurisdictions. |
| POST | `/admin/reference/categories` | SUPER | Creates a category from `{code, name, description?, parentCode?, icon?, color?, displayOrder}`. |
| PATCH | `/admin/reference/categories/{id}` | SUPER | Partially updates a category. Deactivating a category still used by a complaint returns `409`. |
| POST | `/admin/reference/jurisdictions` | SUPER | Creates a jurisdiction from `{code, name, level, city?, state?, country?, parentCode?}`. |

### 11.2 Citizen complaint API

| Method | Path | Behaviour |
|---|---|---|
| POST | `/citizen/complaints` | Creates a complaint. Required: `description` (10–2000), `latitude`, `longitude`, `locationSource`; optional address/category fields. Returns `ComplaintDetail` with `id`, `complaintNumber`, status `SUBMITTED`, SLA due date, and initial timeline entry. |
| GET | `/citizen/complaints` | Lists only the caller's complaints. Filters: `status`, `categoryId`, `from`, `to`, `search`. |
| GET | `/citizen/complaints/{id}` | Own-complaint detail; `404` for another citizen's complaint. |
| GET | `/citizen/complaints/{id}/timeline` | Status history ordered oldest first. |
| GET | `/citizen/complaints/{id}/comments` | Public comments on the caller's complaint. |
| POST | `/citizen/complaints/{id}/comments` | Adds `{body}`. |
| POST | `/citizen/complaints/{id}/reopen` | Requires `{reason}` (20–1000); only valid from `RESOLVED`. |
| POST | `/citizen/complaints/{id}/feedback` | `{rating: 1–5, comment?, resolutionConfirmed, disputeReason?}`. Confirmed feedback closes the complaint; disputed feedback reopens it and requires `disputeReason`. |

### 11.3 Department complaint API

All queries are scoped by the department id from the verified JWT. A client cannot pass a department id to widen scope. Cross-department reads and writes return `404`.

| Method | Path | Behaviour |
|---|---|---|
| GET | `/department/complaints` | Own-department list. Filters: `status`, `from`, `to`. |
| GET | `/department/complaints/{id}` | Own-department detail. |
| POST | `/department/complaints/{id}/accept` | `ASSIGNED → ACKNOWLEDGED`. |
| POST | `/department/complaints/{id}/reject` | Requires `{reason}` (10–1000). |
| PATCH | `/department/complaints/{id}/status` | `{status, reason?}`. Guards are enforced by `StatusTransitionPolicy`; `RESOLVED` requires a reason plus a verified `AFTER`/`EVIDENCE` image. |
| POST | `/department/complaints/{id}/request-info` | Adds a citizen-visible comment asking for more information. |
| GET | `/department/complaints/{id}/notes` | Internal notes; never exposed through citizen endpoints. |
| POST | `/department/complaints/{id}/notes` | Adds `{body}` internal note. |
| POST | `/department/complaints/{id}/reassign-request` | `{toDepartmentId, reason}`; creates a `PENDING` request for super-admin review. |
| GET | `/department/analytics` | Real aggregates from persisted rows: `total`, `byStatus`, `resolved`, `overdue`, `unassigned`, `averageResolutionHours`. |
| GET | `/department/service-areas` | Own-department service areas. |
| POST | `/department/service-areas` | `{name, boundaryWkt, coversWard?, coversZone?, jurisdictionId?}`. WKT is structurally validated; PostGIS remains authoritative. |
| DELETE | `/department/service-areas/{id}` | Deactivates an own-department service area. Cross-department deletion returns `404`. |

### 11.4 Super-admin complaint API

| Method | Path | Behaviour |
|---|---|---|
| GET | `/admin/complaints` | All complaints. |
| GET | `/admin/complaints/unassigned` | Complaints with no active assignment. |
| GET | `/admin/complaints/{id}` | Any complaint detail. |
| POST | `/admin/complaints/{id}/assign` | `{departmentId, reason}`. Only active, accepting, `VERIFIED` departments are eligible. |
| POST | `/admin/complaints/{id}/unassign` | Requires `{reason}`; closes the active assignment and notifies super admins. |
| GET | `/admin/reassignment-requests` | Pending reassignment requests. |
| POST | `/admin/reassignment-requests/{id}/review` | `{decision: APPROVED\|REJECTED, reason?}`. Approval closes the old assignment, creates a `REASSIGN` assignment, changes ownership, and records an `ASSIGNED → ASSIGNED` status-history event. Rejection requires a reason. |

`GET /admin/analytics` is **not implemented** in Phase 3; no hardcoded or cross-department aggregate is exposed.

### 11.5 Notifications

| Method | Path | Behaviour |
|---|---|---|
| GET | `/notifications` | Notifications for the authenticated recipient only, newest first; now includes `deliveryStatus` and `sentAt`. |
| GET | `/notifications/unread-count` | `{ "count": number }`. |
| GET | `/notifications/preferences` | `{ "inAppEnabled": true, "emailEnabled": false, "pushEnabled": false }` for the authenticated user. |
| PATCH | `/notifications/preferences` | `{ "inAppEnabled": boolean, "emailEnabled"?: false, "pushEnabled"?: false }`; enabling email or push returns `400`. |
| PATCH | `/notifications/{id}/read` | Marks one own notification read and returns `{ "status": "READ" }`; another recipient's id returns `404`. |
| POST | `/notifications/read-all` | Marks all own notifications read and returns `{ "status": "READ" }`. |

### 11.6 Phase 3 contract notes

- Mutations use HTTP `200` with an empty body for `void` operations; creation endpoints return the created representation. This differs from the Phase 0 draft's `201` convention.
- Complaint location accepts lat/lon and persists `POINT(longitude latitude)` in `complaints.location_wkt`; production Flyway also populates PostGIS `geometry` from that column via a trigger.
- Illegal status transitions return `409` with the allowed status set in the problem detail. Missing reasons/evidence, invalid department eligibility, and invalid WKT return `400`.
- Every audited mutation writes an `audit_logs` row from the JWT identity, never from a client-supplied actor.
- Outbox rows are persisted for domain events. The Phase 9 dispatcher/worker described in §18 supersedes the original Phase 3 note that no worker existed.
- Upload presigning/confirmation, reverse geocoding, AI analysis, classification overrides, and `/meta/config` remain later-phase functionality.

### 11.7 Phase 3 error examples

Illegal transition (`409`, from `PATCH /api/v1/department/complaints/{id}/status`):

```json
{
  "type": "https://civicsense.app/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Cannot move from RESOLVED to IN_PROGRESS. Allowed: [CLOSED, REOPENED]",
  "instance": "/api/v1/department/complaints/6d8e.../status",
  "correlationId": "0d72e2d4-7f36-4a7c-b7d4-83c9dc7b8b21"
}
```

Cross-department access (`404`, from `GET /api/v1/department/complaints/{id}`):

```json
{
  "type": "https://civicsense.app/errors/not-found",
  "title": "Not found",
  "status": 404,
  "detail": "Complaint not found",
  "instance": "/api/v1/department/complaints/6d8e...",
  "correlationId": "41a04ab9-1f92-4a19-9c16-2f3387b49c0e"
}
```

Invalid service-area boundary (`400`, from `POST /api/v1/department/service-areas`):

```json
{
  "type": "https://civicsense.app/errors/invalid-request",
  "title": "Invalid request",
  "status": 400,
  "detail": "boundaryWkt must be a POLYGON or MULTIPOLYGON",
  "instance": "/api/v1/department/service-areas",
  "correlationId": "8f6b3e24-65d1-4f0f-91f0-0a12c7d3e5b4"
}
```

---

## 12. Phase 4 frontend integration notes (2026-11-21)

The citizen frontend consumes only the implemented endpoints below; no mock/fallback data is present in production paths. All paths are relative to `/api/v1`. These notes describe client behaviour, not new backend contracts.

### 12.1 Endpoints actually called by the citizen UI

| Area | Calls | Client usage |
|---|---|---|
| Auth (Phase 2) | `POST /auth/login`, `POST /auth/register/citizen`, `POST /auth/register/department`, `GET /auth/me`, `POST /auth/refresh`, `POST /auth/logout` | `AuthProvider` + login/register pages; token refresh handled by the existing axios interceptor; access token in memory only. |
| Admin (Phase 2) | `GET /admin/departments`, `POST /admin/departments/{id}/verify` | Super-admin department verification screen. |
| Reference (§11.1) | `GET /meta/categories` | Report wizard category/subcategory selects use the nested `children` tree; My Complaints filter flattens it. `includeInactive=false` (default). |
| Citizen (§11.2) | `POST /citizen/complaints`, `GET /citizen/complaints`, `GET /citizen/complaints/{id}`, `GET .../timeline`, `GET/POST .../comments`, `POST .../reopen`, `POST .../feedback` | Dashboard aggregates, report submission, filtering, detail/timeline/comments, reopen and feedback. |
| Notifications (§11.5) | `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `POST /notifications/read-all` | Notification centre and nav unread badge (30 s polling). |

### 12.2 Request/response shape confirmations

- `GET /meta/categories` returns `CategoryResponse[]` with nested `children` and **no** `parentCode`; subcategories must be read from the selected top-level category's `children`.
- `GET /notifications/unread-count` returns `{ "count": number }` (Phase 3 contract).
- `POST /citizen/complaints/{id}/comments` returns `CommentResponse` (`id`, `authorName`, `authorRole`, `body`, `createdAt`).
- `POST .../reopen` and `POST .../feedback` return `200` with no meaningful body. `PATCH /notifications/{id}/read` and `POST /notifications/read-all` return `{ "status": "READ" }`; the UI invalidates affected queries rather than relying on the body.
- `GET /citizen/complaints` date filters are sent as ISO-8601 instants built from the local date inputs (`T00:00:00` / `T23:59:59`).
- Dashboard counts are computed **client-side from the returned rows**; no hardcoded statistics and no backend analytics endpoint is used for the citizen role.

### 12.3 RFC 7807 handling (§1.4)

`frontend/src/lib/api/errors.ts` parses `type`, `title`, `status`, `detail`, `instance`, `correlationId` and `errors[]`. Field errors are joined into a single message; a `404` is detected for "not found" states; network errors produce a distinct retryable message. Every list, detail and mutation exposes an error state with a retry action.

### 12.4 Contract gaps that remain outside Phase 5

These are explicitly surfaced in the UI rather than faked:

| Gap | Effect | UI behaviour |
|---|---|---|
| `/auth/me` is read-only | Profile editing unavailable | Profile page shows identity/session facts and states that settings are not available. |
| No reverse-geocoding endpoint (Phase 6) | No address lookup | Address is entered manually alongside GPS/map coordinates; `locationSource` is `GPS`, `MANUAL` or `GEOCODED` as selected. |
| Backend routing exists, but no admin routing UI yet (Phase 8) | Decisions are exposed through API only | Detail still shows "Awaiting assignment" until an admin UI consumes `routingDecisions[]`; the AI department hint remains advisory only. |
| Department upload evidence flow | Superseded by Phase 7 | Secure presign → PUT → confirm → `POST /department/complaints/{id}/evidence` is documented in §6. |

### 12.5 Local verification

`npm run test` (8 Vitest unit tests), `npm run lint` (oxlint) and `npm run build` (tsc + Vite) all pass. No live frontend→backend request or hosted Supabase query was observed; see `DEVELOPMENT_ROADMAP.md §8.4–§8.5` and §9.7 for the full reports and run instructions.

---

## 13. Phase 5 implemented surface (2026-11-21)

### 13.1 Upload + analysis sequence

1. `POST /uploads/presign` — server creates a `PENDING` row bound to the JWT user.
2. Browser `PUT`s bytes to the returned presigned URL. Non-2xx is a real failure.
3. `POST /uploads/{imageId}/confirm` — server verifies bytes and marks `VERIFIED`.
4. `POST /citizen/complaints` with `imageIds` — verified images are attached inside the complaint transaction, status becomes `AI_ANALYSIS_PENDING`, and a `PENDING` `ai_analyses` row is created.
5. After commit, an async listener calls `AiAnalysisService.process(complaintId)` once. An `ai_analysis_runs` row prevents duplicate processing.
6. The citizen detail endpoint exposes `images` and `analysis`. View URLs are minted per request via `GET /uploads/{imageId}/url`.

If any upload or confirmation fails, the frontend discards already-confirmed images and submits **no** complaint. If AI analysis fails after submission, the complaint and images remain persisted and the status becomes `AI_ANALYSIS_FAILED`.

### 13.2 AI analysis result fields

`analysis` on `ComplaintDetail` contains:

| Field | Meaning |
|---|---|
| `status` | `PENDING`, `PROCESSING`, `SUCCESS`, `COMPLETED`, `FAILED`, `TIMEOUT`, `INVALID_SCHEMA` |
| `isCivicIssue` | Provider's civic/non-civic judgement; `false` forces review |
| `categoryCode`, `subcategoryCode`, `severity`, `description` | Validated structured classification/advisory summary |
| `safetyRisk`, `visibleDamage`, `isBlurry`, `requiresAdditionalInfo` | Safety and image-usability signals |
| `confidence` | `0–1`; below threshold forces human review |
| `recommendedDepartmentHint` | Advisory string from the provider; **never** used for assignment |
| `requiresHumanReview` | Recomputed server-side from `isCivicIssue`, blurriness, confidence, info request, and the provider flag |
| `errorMessage` | Generic persisted failure reason; raw provider text is retained in `ai_analyses.raw_response`, not returned to citizens |

Status transitions: `SUBMITTED → AI_ANALYSIS_PENDING → AI_ANALYSIS_COMPLETED | AI_ANALYSIS_FAILED`. The provider is selected with `CIVICSENSE_AI_PROVIDER` (`stub` in dev/test, `openai-compatible` in prod). Prod refuses to start with `stub`, a blank `CIVICSENSE_AI_BASE_URL`, or a blank `CIVICSENSE_AI_API_KEY`.

### 13.3 Explicit non-contracts

- AI never assigns a department and never returns coordinates/addresses inferred from images.
- No admin classification override endpoint was added in Phase 5. This was a historical Phase 5 limitation; the endpoint was added in Phase 6 (§14) and preserves the original analysis row.
- No permanent public image URL is issued; presigned URLs expire.
- No paid AI call, real Supabase upload, or real provider API call was made during verification.

---

## 14. Phase 6 implemented surface (2026-11-21)

### 14.1 Routing ruleset `routing-v1`

Routing runs after commit on no-image submit, AI success and AI failure. The production `SpatialMatcher` uses PostGIS `ST_Intersects` against GiST-indexed service-area and jurisdiction geometry; H2 tests use an explicit JTS WKT double. The AI department hint is advisory only and is never scored.

Eligibility: verified, active, accepting complaints, auto-assign enabled, category capability match, valid coordinates and valid/active service-area coverage.

| Signal | Score |
|---|---:|
| Service-area polygon contains point | 1000 |
| Ward match | 600 |
| Zone match | 400 |
| City / jurisdiction match | 200 |
| Exact subcategory capability | 300 |
| Category capability | 240 |
| Parent-category capability | 150 |

`total = geoScore + catScore + departments.routing_priority` (default priority `100`). Results are ordered by `total DESC`, department `created_at ASC`, then department id. Exact top ties between different departments produce `AMBIGUOUS`; no coin flip occurs. A full winning department produces `NO_CAPACITY`; a lower-scored department is not silently substituted.

### 14.2 Outcomes and unresolved handling

| Outcome | Assignment | Handling |
|---|---|---|
| `ASSIGNED` | `AUTO` or `ADMIN_CORRECTION` | Citizen + department admins notified; SLA set from validated AI severity |
| `UNASSIGNED` | none | Super-admin review queue + notification |
| `AMBIGUOUS` | none | Candidates retained; super admin must choose or reroute |
| `LOW_CONFIDENCE` | none | Includes missing/failed AI; human review required |
| `NON_CIVIC` | none | AI determined non-civic; human review required |
| `NO_CAPACITY` | none | Winning department is at its open-complaint cap; resolved/closed complaints do not consume capacity |

Unresolved current decisions are included in `GET /admin/complaints/unassigned`. They are never silently dropped. Forced reroutes preserve all prior decision rows and mark only the newest one current.

### 14.3 Local verification boundary

The routing matrix from `AI_ROUTING_DESIGN.md §8.2` is covered by `Phase6RoutingIntegrationTest` and `Phase6RoutingUnitTest` (18 tests). These tests prove determinism, eligibility, persistence and API behaviour locally using H2/JTS. They are **not** proof of hosted PostGIS query execution. H2 2.3 cannot create PostgreSQL partial indexes, so V9's `uq_current_routing_decision` is production-only; the local tests assert one current decision after forced reroutes, and the engine explicitly flushes the supersede before inserting its replacement. V9 has not been applied to Supabase, and Phase 1 §1.1 remains open.

---

## 15. Review questions

1. Is 15-minute access / 7-day refresh acceptable, or is a different session policy preferred?
2. Should department reassignment require super-admin approval (as designed) or be directly executable by the requesting department admin?
3. Is polling (30 s) acceptable for in-app notifications in v1, or is WebSocket required from the start?

---

## 16. Phase 7 department dashboard contract (2026-11-21)

### 16.1 Permission boundaries

- `DepartmentComplaintController`, `DepartmentController` and `DepartmentServiceAreaController` all use `@PreAuthorize("@departmentGuard.isVerified(authentication)")`. The guard re-reads department verification/active status from the database each request.
- Every complaint lookup calls `findByIdAndRelatedDepartmentId(id, principal.departmentId())`. Cross-department IDs return `404` and are indistinguishable from missing rows.
- Service-area reads/writes use `principal.departmentId()`; profile, capabilities and team management use the caller's own department record. No Phase 7 endpoint accepts a department ID to broaden scope.
- `internalNotes` is populated only for department admins; citizen detail responses never contain it.
- Evidence attachment requires a `VERIFIED` upload owned by the acting admin, allows only `BEFORE`/`AFTER`/`EVIDENCE`, and rejects images already attached to another complaint.
- Super-admin routes remain unchanged: department/citizen callers receive `403`; department/citizen callers cannot reach `/admin/*`.

### 16.2 Frontend consumption

| Screen | Routes | Calls |
|---|---|---|
| Dashboard | `/department` | `GET /department/overview?periodDays=...`, notification count |
| Queue | `/department/complaints` | `GET /department/complaints/queue`, `GET /meta/categories` |
| Detail | `/department/complaints/:id` | detail, notes, all action endpoints, upload lifecycle, secure image-view URLs |
| Profile | `/department/profile` | profile, capabilities, team, service areas, notifications |

Legacy `GET /department/complaints` is intentionally unchanged and still returns `List<ComplaintSummary>`. The rich queue is the separate paged endpoint in §6.

### 16.3 Verification results

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

| Check | Result |
|---|---|
| Backend tests | ✅ 93 passed, 0 failures/errors/skips |
| Backend package | ✅ BUILD SUCCESS |
| Frontend tests | ✅ 20 passed in 3 files |
| Frontend lint | ✅ exit 0, non-blocking React effect warnings |
| Frontend build | ✅ TypeScript + Vite production build succeeded |

Phase 7 integration tests cover queue scoping/pagination/sort/filters, legacy-array compatibility, period analytics, overview/activity/notifications, super-admin regression, state-machine actions, private notes, evidence/view authorization, profile/capabilities/team/service-area management, and inactive-department denial. Component tests cover dashboard metrics/error retry, queue filters/pagination/responsive layouts, detail history/notes/retry, and profile data rendering.

### 16.4 Open verification boundary

Hosted Supabase/PostGIS, Flyway V9/V10 on real PostgreSQL, production object storage, and real AI provider calls were not available. Local H2/JTS and stubbed object storage are test doubles. Phase 1 §1.1 remains open. Frontend E2E/browser automation is deferred; responsive behaviour is asserted at component level.

---

## 17. Phase 8 super administrator contract (2026-11-21)

This section supersedes the earlier high-level Phase 0 wording in §7 wherever the implemented payloads differ. All routes are relative to `/api/v1` and require `ROLE_SUPER_ADMIN` both in the URL security chain and via controller-level `@PreAuthorize("hasRole('SUPER_ADMIN')")`.

### 17.1 Platform insight

`GET /admin/overview?periodDays=30` returns:

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
  "byStatus": [{"key": "ASSIGNED", "label": "Assigned", "count": 0}],
  "byCategory": [{"key": "ROAD", "label": "Roads", "count": 0}],
  "byDepartment": [{"key": "uuid", "label": "Roads Department", "count": 0}],
  "byJurisdiction": [{"key": "WARD-12", "label": "Ward 12", "count": 0}],
  "activity": [{
    "complaintId": "uuid", "complaintNumber": "CIV-...", "fromStatus": "SUBMITTED",
    "toStatus": "ASSIGNED", "actorRole": "SYSTEM", "reason": null,
    "createdAt": "2026-11-21T12:00:00Z"
  }],
  "audit": [{
    "id": "uuid", "actorName": "Admin", "actorRole": "SUPER_ADMIN",
    "action": "DEPARTMENT_VERIFY", "entityType": "department", "entityId": "uuid",
    "reason": "Documents confirmed during onboarding review.",
    "createdAt": "2026-11-21T12:00:00Z", "correlationId": "uuid"
  }]
}
```

`GET /admin/analytics` returns the same object without `activity`/`audit`; `GET /admin/activity?limit=20` returns just the activity array. `periodDays` is clamped to 1–365. All values are derived from persisted rows rather than frontend constants.

### 17.2 Department and user governance

| Method | Path | Request / behaviour |
|---|---|---|
| PATCH | `/admin/departments/{id}/status` | `{ "status":"VERIFIED|REJECTED|SUSPENDED", "reason":"..." }` |
| PATCH | `/admin/departments/{id}/operations` | `{ "routingPriority":0..10000, "maxOpenComplaints":1..10000|null, "autoAssignEnabled":true|false, "reason":"..." }` |
| PATCH | `/admin/users/{id}/status` | `{ "status":"ACTIVE|SUSPENDED|DISABLED", "reason":"..." }` |
| PATCH | `/admin/users/{id}/role` | `{ "role":"CITIZEN|DEPARTMENT_ADMIN|SUPER_ADMIN", "reason":"..." }` |

Reason length is 10–1000 characters, trimmed server-side. A department admin cannot change their own status/role, remove the last active super admin, or broaden a department-scoped query with a client-supplied department ID. Suspending/disabling a user revokes refresh-token families. Department status is re-read by access guards on every request, so suspension takes effect on an existing session; reactivation is `VERIFIED` status, not a separate endpoint.

### 17.3 Global queue and AI-history governance

`GET /admin/complaints/queue` accepts the Phase 7 queue parameters plus `departmentId`, `routingOutcome`, and `aiStatus`, returning the existing `PagedResponse<ComplaintSummary>` envelope. It is deliberately separate from `/admin/complaints/{id}`.

`GET /admin/complaints/{id}/analyses` returns newest-first, append-only history:

```json
{
  "analyses": [{
    "id": "uuid", "status": "SUCCESS", "source": "ADMIN_OVERRIDE",
    "isCivicIssue": true, "categoryCode": "WATER", "subcategoryCode": "LEAK",
    "severity": "HIGH", "description": "Field inspection confirmed a leaking water main.",
    "confidence": null, "requiresHumanReview": false,
    "adminOverrideReason": "Supervisor confirmed the reported issue is a water leak.",
    "actorName": "Admin", "actorRole": "SUPER_ADMIN",
    "createdAt": "2026-11-21T12:05:00Z"
  }]
}
```

`source` is `AI` or `ADMIN_OVERRIDE`. Raw provider responses, request payloads, parsed results, provider identifiers, storage keys, and admin user IDs are not present in this response. The original `AI` row is never mutated when an administrator overrides it.

`POST /admin/complaints/{id}/reroute`, `/assign`, `/unassign`, and `/reassignment-requests/{id}/review` require reasons and preserve routing, assignment, and status history. A forced reroute supersedes the previous current decision instead of deleting it. `GET /uploads/{imageId}/url` now also authorizes an authenticated `SUPER_ADMIN` to inspect evidence.

### 17.4 Audit and migration

`GET /admin/audit-logs?action=&entityType=&entityId=&actorId=&from=&to=&page=0&size=20` returns a paged audit model with actor name/role, action, entity type/id, reason, correlation id, and timestamp. Flyway `V11__phase8_admin_governance.sql` adds `audit_logs.reason`, `idx_audit_created_at`, and `idx_audit_entity_id`; the H2 test schema mirrors the reason column.

### 17.5 Frontend consumption

| Screen | Route | Primary calls |
|---|---|---|
| Dashboard | `/admin` | `/admin/overview`, `/admin/activity`, `/admin/audit-logs` |
| Departments | `/admin/departments` | `/admin/departments`, detail/status/operations/verify |
| Users | `/admin/users` | `/admin/users`, status/role mutations |
| Complaints | `/admin/complaints` | `/admin/complaints/queue` |
| Complaint detail | `/admin/complaints/:id` | detail, analyses, routing decisions, assign/reroute/unassign/classification/review, secure image URLs |
| Platform | `/admin/platform` | categories, jurisdictions, service areas |
| Audit | `/admin/audit` | `/admin/audit-logs` |

All routes are wrapped in `RequireRole(['SUPER_ADMIN'])`; this is UI convenience only and is enforced server-side.

### 17.6 Verification and boundaries

Backend `mvn -B test` ran 107 tests with 0 failures/errors/skips; `mvn -B package` succeeded. Targeted `mvn -B -Dtest=Phase8* test` ran 14 tests with 0 failures/errors/skips. Frontend `npm run test` passed 30 tests in 4 files; `npm run lint` exited 0 with only three pre-existing `DepartmentProfilePage.tsx` warnings; `npm run build` succeeded with the non-blocking 500 kB chunk advisory. `git diff --check` reported no whitespace errors.

Hosted Supabase/PostGIS, Flyway V1–V11 on real PostgreSQL, production object storage, real AI-provider calls, and browser-level Playwright E2E were not executed. No jurisdiction polygon write API was added in Phase 8; the missing WKT geometry column remains a documented limitation.

---

## 18. Phase 9 notification and workflow contract (2026-11-21)

This section adds the Phase 9 notification delivery and workflow surfaces to the Phase 8 API. All paths remain relative to `/api/v1`.

### 18.1 New and changed endpoints

| Method | Path | Request / response |
|---|---|---|
| POST | `/citizen/complaints/{id}/respond-info` | `{ "body": "..." }` (10–2000 characters). Adds a citizen-visible comment and emits `INFO_RESPONDED` to the owning department admins, or super admins when unassigned. Returns `200` with no body. |
| GET | `/notifications/preferences` | `{ "inAppEnabled": true, "emailEnabled": false, "pushEnabled": false }`; row is created on first read if absent. |
| PATCH | `/notifications/preferences` | `{ "inAppEnabled": boolean, "emailEnabled"?: false, "pushEnabled"?: false }`. Any attempt to enable email or push is rejected with `400`; in-app may be disabled or re-enabled by the owner. |
| GET | `/notifications` | Existing recipient-scoped list; each row now also exposes `deliveryStatus` (`PENDING`/`SENT`/`FAILED`/`READ`) and `sentAt`. |

`GET /citizen/notifications`, `GET /department/notifications`, and the shared unread/read routes remain as phase-prefixed aliases of the same recipient-scoped service. Duplicate feedback now returns `409` instead of `400`.

### 18.2 Delivery and retry semantics

- Workflow services write an `outbox_events` row inside the caller transaction. Notification rows are created only after commit, so a delivery failure cannot roll back a complaint, transition, assignment, audit row, or status-history row.
- A post-commit event triggers immediate dispatch. In the `test` profile the executor is synchronous; other profiles use a bounded `civicsense-notify-*` pool. A scheduled sweep retries missed events in production and is disabled in tests for determinism.
- `PENDING`, retryable `FAILED`, and stale `PROCESSING` events can be claimed atomically with a two-minute lease. Success sets `PROCESSED`; failure increments `attempts`, records `last_error`, and retries after 30 seconds up to five attempts. Terminal failures keep `last_error` and are no longer retried automatically.
- A unique `(outbox_event_id, recipient_user_id)` index plus an existence check makes repeated or overlapping delivery attempts idempotent. Each event produces at most one notification per recipient.
- `in_app_enabled=false` marks the outbox event processed without creating a notification row. Email and push remain disabled by default; no real provider is invoked.

### 18.3 Workflow event coverage

In addition to the Phase 3–8 events, Phase 9 emits `INFO_RESPONDED`, `EVIDENCE_ATTACHED`, `FEEDBACK_RECEIVED`, `COMPLAINT_CLOSED`, `REASSIGNMENT_APPROVED`, and `REASSIGNMENT_REJECTED`, and corrects submission, department registration/verification, and closed/reopened coverage. Notification bodies contain only the complaint number, plain-language title/body, and event context; internal notes, raw AI output, storage keys, and secrets are never serialized.

### 18.4 Frontend consumption

| Screen | Phase 9 behaviour |
|---|---|
| `/notifications` | Numeric unread count, unread-only filter, manual refresh, 30-second polling, failed-delivery badge, and role-aware links: citizen `/complaints/{id}`, department admin `/department/complaints/{id}`, super admin `/admin/complaints/{id}`. |
| `/complaints/:id` | Pending information-request banner and dedicated `respond-info` reply; evidence, feedback, confirmed-close and dispute/reopen states. |
| `/department/complaints/:id` | Citizen feedback/dispute card, notification query invalidation after every action, and a single guarded resolution path. |
| `/admin/complaints/:id` | Citizen feedback/dispute card and notification query invalidation after governance actions. |
| `/admin` | Unread notification summary with retry and role-correct complaint links. |
| `/department` | Notification retry controls for both list and unread-count failures. |

Frontend role guards and links are UX convenience only; ownership, department isolation, transition validation, and notification recipient checks are enforced server-side.

### 18.5 Verification

Backend `mvn -B test` ran 116 tests with 0 failures/errors/skips, including nine Phase 9 integration tests; `mvn -B package` succeeded. Targeted `mvn -B -Dtest=Phase9NotificationWorkflowIntegrationTest test` ran 9 tests with 0 failures/errors/skips. Frontend `npm run test` passed 33 tests in 5 files; `npm run lint` exited 0 with only the three pre-existing `DepartmentProfilePage.tsx` warnings; `npm run build` succeeded with the existing non-blocking 500 kB chunk advisory. Hosted Supabase/PostGIS, Flyway V1–V12 on real PostgreSQL, production object storage/email/push, real AI-provider calls, and browser-level Playwright E2E were not executed.

---

## 19. Phase 10 security and password reset contract (2026-11-22)

### 19.1 Password reset endpoints

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/auth/forgot-password` | public | `{ "email": "user@example.com" }` | `200` `{ "message": "If the account exists, a password reset link has been sent." }` |
| POST | `/auth/reset-password` | public | `{ "token": "<raw-token>", "password": "..." }` | `200` with no body; `400` for invalid/used/expired token or weak password |

The `forgot-password` response is intentionally generic for known and unknown emails, preventing account enumeration. For an active known account only, the server generates a 256-bit token, stores its SHA-256 hex digest in `password_reset_tokens`, and writes an `auth.password_reset_requested` outbox event containing the one-time link token. No in-app notification is created and no raw token is returned by any API. Tokens expire after 30 minutes (`CIVICSENSE_RESET_TOKEN_MINUTES`) and are single-use. Since Phase 12, that outbox event is routed to the provider-gated SMTP channel described in §21.2 when email is explicitly configured; otherwise no email is sent.

`reset-password` enforces the registration password policy: 10–100 characters with upper case, lower case, a digit, and a special character. Success stores a prefixed Argon2id hash through `UpgradingPasswordEncoder`, clears lockout counters, marks the token used, and revokes all refresh-token families for that user. Invalid, expired, and reused tokens return `400` with a non-specific validation message. The frontend reads the token from the `/reset-password?token=...` query string and sends it only in the request body; it is never persisted in browser storage.

### 19.2 Rate limiting and 429 responses

| Scope | Default | Key |
|---|---|---|
| Login | 5 / 15 min | IP |
| Password reset request/reset | 5 / 15 min | IP |
| Global API | 120 / min | IP |
| `POST /citizen/complaints` | 3 / min | authenticated user id, IP fallback |
| `POST /uploads/presign` | 10 / min | authenticated user id, IP fallback |

Health, actuator, OpenAPI/Swagger, error, and favicon paths are excluded from the global limiter. Exceeding a limit returns RFC 7807-shaped JSON with `type=https://civicsense.app/errors/too-many-requests`, `status=429`, `Retry-After`, and a correlation id. The limiter is in-memory/single-instance; Redis remains a scale-out prerequisite.

### 19.3 Security response headers

Every response includes a configurable Content-Security-Policy. The default policy is:

```text
default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
img-src 'self' data: blob: https://tile.openstreetmap.org;
connect-src 'self' http://localhost:8080 https://tile.openstreetmap.org;
font-src 'self' data:; object-src 'none'; base-uri 'self';
frame-ancestors 'none'; form-action 'self'
```

`CIVICSENSE_CSP` overrides the default. Spring Security frame denial and content-type protections remain enabled; no browser meta CSP was added.

### 19.4 Verification

At the Phase 10 checkpoint, backend `mvn -B test` ran 127 tests with 0
failures/errors/skips, including 11 Phase 10-specific backend tests;
`mvn -B package` succeeded. Frontend `npm run test` passed 39 tests in 6 files;
`npm run lint` exited 0 with only the three pre-existing
`DepartmentProfilePage.tsx` warnings; `npm run build` succeeded with the
existing non-blocking 500 kB chunk advisory; `npm run test:e2e` passed 5
Playwright tests using local Microsoft Edge and stubbed APIs. The dependency
audit found zero npm and zero OSV Maven advisories after the Spring Boot 4.2.0
remediation. Hosted Supabase/PostGIS, Flyway V1–V13 on real PostgreSQL,
production email/storage/AI providers, and E2E against real services were not
executed. Phase 12's final results supersede these counts; see §21.

---

## 20. Phase 11 deployment verification reference (2026-11-22)

The API contract is unchanged. Deployment support is documented in
`docs/DEPLOYMENT.md`; this section only records how to verify the health surface
described in §10.

| Check | Command | Expected |
|---|---|---|
| API health | `Invoke-RestMethod http://localhost:8080/api/v1/meta/health` | HTTP 200 with `status=UP` and `database`, `postgis`, `objectStorage` all `UP` on the hosted path. |
| Actuator health | `Invoke-RestMethod http://localhost:8080/api/v1/actuator/health` | HTTP 200 with `db`, `postgis`, `objectStorage` components `UP`. |
| SPA | `Invoke-WebRequest http://localhost:5173/` | HTTP 200 from the Vite dev server. |
| Migrations | Startup log / `SELECT * FROM flyway_schema_history` | 13 successful rows (V1–V13, including `V8__phase5_image_analysis.sql`; the unused `V8__super_admin_seed.sql` was never created). |

Under the local `test` profile the backend uses the H2 test double with Flyway
disabled, so `postgis` and `objectStorage` are expected to be `DOWN` and
`/api/v1/meta/health` returns HTTP 503 with the same JSON shape. That result
proves local process startup only; it is not the hosted health gate and does
not close Phase 1 §1.1.

---

## 21. Phase 12 API and runtime notes (2026-11-22)

### 21.1 Contract stability

Phase 12 added **no new endpoint, request field, response field or status
code**. Password-reset endpoints remain public and rate-limited; presigned
upload URLs remain short-lived; RBAC/ownership rules and rate-limit values are
unchanged. The only additive wire-level behavior is the global
`X-Correlation-Id` response header, already specified in §1.5, now generated by
a request filter and exposed through CORS.

### 21.2 Password reset delivery

`POST /auth/forgot-password` continues to return the same generic
response. The created `auth.password_reset_requested` outbox event is now routed
to a provider-gated SMTP channel when `CIVICSENSE_MAIL_ENABLED=true` and all
SMTP settings are present; otherwise email remains disabled and no delivery is
claimed. The raw token is sent only in the message body, never in API
responses, notifications or logs. A deterministic Message-ID supports
provider-side best-effort dedupe; terminal delivery errors remain inspectable
in outbox state with sanitised exception text.

### 21.3 Logging and observability

Structured ECS JSON is emitted to stdout using Boot 4.2's built-in encoder.
Correlation IDs are propagated from the request boundary into audit rows,
health responses, error bodies, async workers and scheduled cleanup. Operators
must capture stdout with their host log driver and wire health probes into
their alerting system; no aggregation stack was added.

### 21.4 Final verification

The final Phase 12 run recorded 149 backend tests, 39 frontend unit tests, 5
Playwright tests, zero npm/OSV advisories, no secret-scan findings, a passing
no-Docker smoke/restart check, and a passing H2 backup/restore dry run. Hosted
Supabase/PostGIS, S3, SMTP delivery and deployment checks were **not executed**.
Full evidence and exact blockers are in `DEVELOPMENT_ROADMAP.md §16` and
`docs/PHASE12_IMPLEMENTATION_CONTRACT.md §11`.
