-- V4: citizen/department collaboration, notifications, feedback and outbox.
-- PostgreSQL/Supabase target. Flyway owns this schema; do not create these
-- tables manually in the Supabase dashboard.
--
-- Rollback (manual review only, Flyway migrations are forward-only):
--   DROP TRIGGER IF EXISTS trg_complaint_feedback_updated_at ON complaint_feedback;
--   DROP TRIGGER IF EXISTS trg_internal_notes_updated_at ON complaint_internal_notes;
--   DROP TRIGGER IF EXISTS trg_complaint_comments_updated_at ON complaint_comments;
--   DROP TABLE IF EXISTS complaint_feedback, notifications, complaint_internal_notes,
--     complaint_comments, outbox_events CASCADE;
--
-- outbox_events is created before notifications so notifications.outbox_event_id
-- can declare its FK inline; the logical table order otherwise follows §4.4.
-- complaint_internal_notes are department-private: the service layer asserts
-- department_id == actor.departmentId before writing.

SET search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Outbox (created first: notifications reference it)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_events (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      varchar(100) NOT NULL,
    aggregate_type  varchar(60),
    aggregate_id    varchar(60),
    payload         jsonb NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts        integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error      text,
    idempotency_key varchar(120) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    processed_at    timestamptz,
    CONSTRAINT uq_outbox_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PROCESSING','PROCESSED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (status, next_attempt_at);

-- ---------------------------------------------------------------------------
-- Citizen-visible comments
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_comments (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id       uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    author_user_id     uuid NOT NULL REFERENCES users(id),
    author_role        varchar(20) NOT NULL,
    body               text NOT NULL,
    attachment_image_id uuid REFERENCES complaint_images(id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_comment_author_role CHECK (author_role IN
        ('CITIZEN','DEPARTMENT_ADMIN','SUPER_ADMIN','SYSTEM'))
);
CREATE INDEX IF NOT EXISTS idx_complaint_comments_complaint ON complaint_comments (complaint_id);

DROP TRIGGER IF EXISTS trg_complaint_comments_updated_at ON complaint_comments;
CREATE TRIGGER trg_complaint_comments_updated_at BEFORE UPDATE ON complaint_comments
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Department-private internal notes
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_internal_notes (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id  uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    department_id uuid NOT NULL REFERENCES departments(id),
    author_user_id uuid NOT NULL REFERENCES users(id),
    body          text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_internal_notes_complaint ON complaint_internal_notes (complaint_id);
CREATE INDEX IF NOT EXISTS idx_internal_notes_department ON complaint_internal_notes (department_id);

DROP TRIGGER IF EXISTS trg_internal_notes_updated_at ON complaint_internal_notes;
CREATE TRIGGER trg_internal_notes_updated_at BEFORE UPDATE ON complaint_internal_notes
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    complaint_id     uuid REFERENCES complaints(id),
    type             varchar(60) NOT NULL,
    title            varchar(200),
    body             text,
    channel          varchar(20) NOT NULL DEFAULT 'IN_APP',
    delivery_status  varchar(20) NOT NULL DEFAULT 'PENDING',
    payload          jsonb,
    outbox_event_id  uuid REFERENCES outbox_events(id),
    read_at          timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    sent_at          timestamptz,
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP','EMAIL','PUSH')),
    CONSTRAINT ck_notification_delivery CHECK (delivery_status IN ('PENDING','SENT','FAILED','READ'))
);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread
    ON notifications (recipient_user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_notifications_complaint ON notifications (complaint_id);

-- ---------------------------------------------------------------------------
-- Citizen feedback (one row per complaint)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_feedback (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id         uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    citizen_id           uuid NOT NULL REFERENCES users(id),
    rating               smallint NOT NULL,
    comment              text,
    resolution_confirmed boolean NOT NULL DEFAULT false,
    dispute_reason       text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_complaint UNIQUE (complaint_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

DROP TRIGGER IF EXISTS trg_complaint_feedback_updated_at ON complaint_feedback;
CREATE TRIGGER trg_complaint_feedback_updated_at BEFORE UPDATE ON complaint_feedback
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
