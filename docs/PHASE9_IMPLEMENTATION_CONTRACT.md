# Phase 9 implementation contract (working document)

All paths are relative to `/api/v1`. Phase 8 is the approved checkpoint
`ef2ea7c` / tag `phase8-approved`. This document defines the Phase 9 boundary
before any implementation code is changed.

## 1. Preserve existing contracts

Do not break, rename, or narrow these existing surfaces:

- Citizen: `POST /citizen/complaints`, `GET /citizen/complaints`, `GET /citizen/complaints/{id}`, `GET .../timeline`, `GET/POST .../comments`, `POST .../reopen`, `POST .../feedback`, `GET /citizen/overview`.
- Department: queue/detail, accept, reject, guarded status updates, request-info, private notes, evidence attach, profile/capabilities/team/service areas, analytics/activity, reassign-request.
- Super admin: all Phase 8 governance endpoints, global queue, AI history, classification override, forced reroute, assignment/unassignment, reassignment review, audit exploration.
- Notifications: `GET /notifications`, `GET /citizen/notifications`, `GET /department/notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `POST /notifications/read-all`.
- Existing `/admin/reference/*` administration routes.
- All role guards, department scoping, reasons, append-only AI/routing/assignment/status history, and audit records.

No authentication bypass, fake production data, client-supplied actor, or client-supplied department scope may be introduced.

## 2. Existing functionality to reuse

The repository already contains:

- `Notification`, `NotificationType`, `OutboxEvent`, `OutboxStatus`, repositories, `NotificationService`, `NotificationController`, `NotificationDtos`, and recipient-scoped read/read-all endpoints.
- Citizen workflow endpoints for submission, tracking, timeline, comments, reopen, and feedback.
- Department workflow endpoints for accept/reject/status/request-info/notes/evidence/reassign-request.
- Super-admin reassignment review, manual assignment/unassignment, forced reroute, and AI-history governance.
- Schema for `notifications`, `outbox_events`, `complaint_feedback`, `complaint_status_history`, `reassignment_requests`, and `users.notification_preferences`.
- Frontend notification centre, unread badge polling, citizen complaint detail workflow screens, department workflow screens, and Phase 8 admin screens.

Phase 9 must extend these rather than duplicate them.

## 3. Gaps to close

1. **Reliable delivery:** outbox rows are persisted but there is no dispatcher/worker that turns them into notification rows with retry/backoff and terminal failure handling.
2. **Deduplication:** repeated processing/retries must not create duplicate notification rows for the same event/recipient.
3. **Event coverage:** lifecycle events must be emitted consistently for submission, AI failure/unassigned outcomes, assignment, acceptance/rejection, request-info, status changes, resolution, feedback/confirmation, reopening, department verification/rejection, reassignment requests and reviews.
4. **Preferences:** no notification preference management endpoint exists despite the schema field; define and implement the supported subset or document the deliberate non-implementation.
5. **Delivery channels:** in-app is authoritative. Email/push may be represented only as an interface/config-disabled channel; no real email or push integration is introduced without explicit infrastructure configuration.
6. **Frontend:** notification navigation currently hardcodes `/complaints/{id}`, so department and super-admin recipients are redirected to `/forbidden`. Navigation must resolve by role: citizen `/complaints/{id}`, department admin `/department/complaints/{id}`, super admin `/admin/complaints/{id}`. Unread counts, empty/loading/error/retry states, and workflow feedback must be consistent across all three role surfaces.
7. **Workflow feedback:** citizen UI has no explicit info-request reply state; department/admin detail screens do not display citizen feedback or disputed reopen state; admin mutations do not invalidate notification queries; department dashboard notification errors have no retry; `AppLayout` hides unread-fetch failures by falling back to zero.
8. **Payload contract:** `complaintNumber` is documented in notification payloads but absent from `NotificationDtos.NotificationResponse` and the frontend `NotificationEntry`; reconcile the DTO/client contract.
9. **Tests:** add integration/component coverage for dispatch, retry, dedupe, ownership, all lifecycle events, unauthorized actions, and the end-to-end 20-step citizen→department→citizen scenario.

## 4. Backend design constraints

- Keep complaint commits independent of notification delivery. Workflow services write a durable `outbox_events` row in the caller transaction; notification rows are created only after commit by `OutboxEventProcessor` in a new transaction, so delivery failures never roll back a complaint, transition, assignment, or audit record.
- `NotificationService` no longer creates the in-app row synchronously. It publishes a post-commit event for immediate dispatch and the scheduled dispatcher catches missed or failed events.
- Dispatcher processing is idempotent: the unique `(outbox_event_id, recipient_user_id)` notification index and a repository existence check prevent duplicate rows when retries or the scheduler and post-commit listener overlap.
- Claiming an event atomically moves `PENDING`/retryable `FAILED`/stale `PROCESSING` rows to `PROCESSING` with a lease in `next_attempt_at`. Success sets `PROCESSED`/`processed_at`; failure increments `attempts`, records `last_error`, and sets backoff until the bounded attempt limit, after which the row remains terminal `FAILED` with no `next_attempt_at`.
- A post-commit `@Async` notification executor is synchronous under the `test` profile so integration tests observe deterministic delivery; production uses a bounded thread pool. Scheduling is enabled for the retry sweep.
- Notification preferences are user-scoped with `in_app_enabled`; email/push remain disabled-by-default and are rejected when requested. `GET/PATCH /notifications/preferences` exposes this subset.
- Notification ownership is derived from the authenticated principal. A user can read/mark only their own notifications; another user's ID is indistinguishable from missing (`404`).
- Every workflow transition remains validated by `StatusTransitionPolicy` and existing service guards. Phase 9 does not relax reasons, evidence, eligibility, or department isolation.
- Notification payloads contain only recipient, complaint reference, type, title, body, and dedupe metadata. Raw AI responses, storage keys, tokens, secrets, and internal notes are never included.
- Audit and complaint history remain append-only.

## 5. Workflow coverage

The end-to-end acceptance flow to verify locally:

1. Citizen registers and signs in.
2. Citizen submits a complaint with verified images or no images.
3. Complaint is committed with status history and initial notification/outbox event.
4. AI analysis succeeds or fails without losing the complaint.
5. Routing assigns a verified eligible department or leaves the complaint for super-admin handling.
6. Relevant citizen and department notifications are delivered.
7. Department accepts the assignment.
8. Department requests information; citizen is notified and can reply via the existing comment path.
9. Department updates status through legal transitions only.
10. Department attaches evidence and resolves; resolution evidence guard is enforced.
11. Citizen is notified of resolution and can view the evidence.
12. Citizen submits feedback confirming resolution → `CLOSED`.
13. Alternate path: citizen disputes/reopens with a valid reason → `REOPENED`.
14. Reopened complaint is rerouted or manually assigned without destroying history.
15. Department requests reassignment; super admin is notified.
16. Super admin approves or rejects the reassignment with a reason.
17. Notifications reach the correct recipient group for each event.
18. Unauthorized cross-user/cross-department access is denied.
19. Notification read/unread state is persisted and scoped to the owner.
20. Full audit/history remains intact after retries and restarts.

### 5.1 Required event wiring

Existing emissions to preserve: `COMPLAINT_SUBMITTED` (super admins), `COMPLAINT_ASSIGNED` (citizen and target department admins), `COMPLAINT_ACCEPTED`, `INFO_REQUESTED`, `STATUS_CHANGED`, `COMPLAINT_RESOLVED`, `COMPLAINT_REJECTED`, `UNASSIGNED_COMPLAINT`, `REASSIGNMENT_REQUESTED`.

New or corrected emissions:

- `COMPLAINT_SUBMITTED` to the submitting citizen.
- `DEPARTMENT_APPROVAL_REQUIRED` to super admins on department registration.
- `DEPARTMENT_VERIFIED` / `DEPARTMENT_REJECTED` to department owners on verification, suspension, and reactivation through the existing status endpoints.
- `INFO_RESPONDED` to the owning department admins when a citizen answers an information request through the new citizen endpoint.
- `EVIDENCE_ATTACHED` and `FEEDBACK_RECEIVED` to the citizen and owning department/super admins respectively.
- `COMPLAINT_CLOSED` on confirmed feedback and `COMPLAINT_REOPENED` to department admins and super admins on reopen/dispute.
- `REASSIGNMENT_APPROVED` / `REASSIGNMENT_REJECTED` to the relevant department admins; existing `COMPLAINT_ASSIGNED` on approval remains.

No notification type or payload may expose internal notes or another user's private data.

## 6. Frontend contract

- Reuse `AppLayout`, `PageHeader`, `Card`, `Badge`, `StatePanel`, `StatusBadge`, `StatusTimeline`, `Button`, `Field`, `inputClass`, and existing query/mutation patterns.
- Notification list shows type/title/body/timestamp, complaint number when present, unread styling, and a role-aware link: citizen `/complaints/{id}`, department admin `/department/complaints/{id}`, super admin `/admin/complaints/{id}`. Opening the linked complaint marks that notification read using the existing authenticated endpoint.
- Unread indicator is derived from `GET /notifications/unread-count`, polls at the existing 30-second interval, exposes a visible failure state instead of silently showing zero, and is invalidated/refreshed after all notification-producing workflow mutations. The notification list supports refetch and unread-only filtering.
- Citizen detail exposes info-request reply, evidence view, feedback, confirmation, and reopen states, including clear pending-info-request and final CLOSED/REOPENED messaging.
- Department detail exposes accept/reject/request-info/status/evidence/notes/reassign-request with clear success/error feedback, displays citizen feedback/dispute, and avoids the duplicate generic `RESOLVED` path.
- Super-admin reassignment review and governance actions show the resulting workflow state and audit/history, display citizen feedback/dispute, and include an unread notification summary on the dashboard.
- Every screen has loading, empty, error/retry, and responsive table/card states. No hardcoded notification or workflow data.

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

cd E:\CivicSense
git diff --check
```

## 8. Completion gate

Update `DEVELOPMENT_ROADMAP.md`, `API_DOCUMENTATION.md`, `ARCHITECTURE.md`, `DATABASE_DESIGN.md`, and this contract with the implemented Phase 9 result. Report only tests actually executed, state external integrations actually tested, and stop for explicit owner approval before Phase 10.

## 9. Completion result (2026-11-21)

This contract is complete for Phase 9. The implementation preserves `phase8-approved` at `ef2ea7c` and adds no Phase 10 work.

- Reliable delivery, dedupe, claim leasing, retry/backoff, terminal failures, preferences, and disabled email/push channels are implemented as specified in §4.
- All §5.1 event emissions are wired through the existing services; the new citizen `respond-info` path and duplicate-feedback guard are in place.
- The §6 frontend contract is implemented across notification, citizen, department, and admin screens, including role-aware deep links and visible unread-fetch failures.
- Flyway `V12__phase9_notification_delivery.sql` and the H2 test mirror match the §4/§5 data model.
- Verification actually executed: backend `mvn -B test` (116 tests, 0 failures/errors/skips), targeted Phase 9 suite (9 tests, 0 failures/errors/skips), backend `mvn -B package` (`BUILD SUCCESS`), frontend `npm run test` (33 tests in 5 files), `npm run lint` (exit 0; three pre-existing warnings), `npm run build` (success; existing chunk advisory), and `git diff --check` (no whitespace errors).
- Hosted Supabase/PostGIS, Flyway V1–V12 on real PostgreSQL, production object storage/email/push, real AI-provider calls, and browser-level Playwright E2E were not executed. No Phase 9 commit, tag, or push was created; work remains in the working tree for owner review.
