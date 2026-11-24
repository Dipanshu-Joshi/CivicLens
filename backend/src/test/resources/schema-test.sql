-- H2 (PostgreSQL mode) TEST DOUBLE schema.
-- Mirrors V2__identity_and_departments.sql through V7__seed_reference_data.sql.
-- This file exists ONLY for automated tests; production schema is Flyway-owned.
--
-- Deliberate differences from the production migrations:
--   * geometry / jsonb / inet columns are omitted or represented as text;
--     complaints.location_wkt and service_areas.boundary_wkt are the JPA-
--     mapped companion columns used instead of PostGIS geometry.
--   * no triggers or Postgres functions (in particular the WKT -> geometry
--     triggers and set_updated_at() do not exist here).
--   * function-based unique indexes are replaced by plain unique indexes;
--     the service layer lowercases emails before persistence.
--   * partial indexes are represented by plain indexes. In particular the
--     production partial unique index uq_active_assignment (one active
--     assignment per complaint) cannot be expressed in H2: uniqueness must be
--     enforced in the service layer. See idx_assignments_complaint_active.
--   * platform_settings.key/value are quoted because KEY and VALUE are H2
--     reserved words.

-- ---------------------------------------------------------------------------
-- Drop children before parents (CASCADE keeps re-creation idempotent).
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS platform_settings CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS geocoding_cache CASCADE;
DROP TABLE IF EXISTS routing_decisions CASCADE;
DROP TABLE IF EXISTS complaint_feedback CASCADE;
DROP TABLE IF EXISTS notification_preferences CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS complaint_internal_notes CASCADE;
DROP TABLE IF EXISTS complaint_comments CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS reassignment_requests CASCADE;
DROP TABLE IF EXISTS complaint_status_history CASCADE;
DROP TABLE IF EXISTS complaint_assignments CASCADE;
DROP TABLE IF EXISTS ai_analysis_runs CASCADE;
DROP TABLE IF EXISTS ai_analyses CASCADE;
DROP TABLE IF EXISTS complaint_images CASCADE;
DROP TABLE IF EXISTS complaints CASCADE;
DROP TABLE IF EXISTS service_areas CASCADE;
DROP TABLE IF EXISTS jurisdictions CASCADE;
DROP TABLE IF EXISTS department_issue_categories CASCADE;
DROP TABLE IF EXISTS issue_categories CASCADE;
DROP TABLE IF EXISTS department_admins CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS citizen_profiles CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP SEQUENCE IF EXISTS complaint_number_seq;
CREATE SEQUENCE complaint_number_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------
-- Identity (V2)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                     uuid PRIMARY KEY,
    email                  varchar(255) NOT NULL,
    password_hash          varchar(255) NOT NULL,
    role                   varchar(20)  NOT NULL,
    account_status         varchar(20)  NOT NULL,
    full_name              varchar(150) NOT NULL,
    phone                  varchar(30),
    email_verified         boolean      NOT NULL DEFAULT false,
    failed_login_attempts  integer      NOT NULL DEFAULT 0,
    locked_until           timestamp with time zone,
    last_login_at          timestamp with time zone,
    locale                 varchar(10)  NOT NULL DEFAULT 'en',
    created_at             timestamp with time zone NOT NULL,
    updated_at             timestamp with time zone NOT NULL,
    version                integer      NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_role CHECK (role IN ('CITIZEN','DEPARTMENT_ADMIN','SUPER_ADMIN')),
    CONSTRAINT ck_users_status CHECK (account_status IN ('ACTIVE','PENDING_VERIFICATION','SUSPENDED','DISABLED'))
);
-- H2 does not accept function-based indexes; the service lowercases emails
-- before persistence, so a plain unique index is an equivalent test double.
CREATE UNIQUE INDEX uq_users_email_lower ON users (email);

CREATE TABLE citizen_profiles (
    id                        uuid PRIMARY KEY,
    user_id                   uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    latitude                  decimal(10,7),
    longitude                 decimal(10,7),
    address_line              varchar(255),
    city                      varchar(120),
    state                     varchar(120),
    postal_code               varchar(20),
    country                   varchar(120),
    notification_preferences  text,
    created_at                timestamp with time zone NOT NULL,
    updated_at                timestamp with time zone NOT NULL
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(255) NOT NULL UNIQUE,
    expires_at  timestamp with time zone NOT NULL,
    revoked_at  timestamp with time zone,
    replaced_by uuid,
    ip_address  varchar(45),
    user_agent  varchar(1000),
    created_at  timestamp with time zone NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- V13 test mirror: raw token is never stored.
CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamp with time zone NOT NULL,
    used_at     timestamp with time zone,
    created_at  timestamp with time zone NOT NULL
);
CREATE INDEX idx_password_reset_user_active
    ON password_reset_tokens (user_id, used_at, expires_at);

-- ---------------------------------------------------------------------------
-- Departments and reference data (V2)
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
    id                    uuid PRIMARY KEY,
    name                  varchar(200) NOT NULL,
    department_code       varchar(30)  NOT NULL UNIQUE,
    department_type       varchar(80)  NOT NULL,
    description           text,
    official_email        varchar(255) NOT NULL,
    contact_number        varchar(30),
    office_address        varchar(255),
    city                  varchar(120),
    state                 varchar(120),
    country               varchar(120),
    verification_status   varchar(25)  NOT NULL,
    rejection_reason      text,
    is_active             boolean NOT NULL DEFAULT true,
    accepts_complaints    boolean NOT NULL DEFAULT false,
    auto_assign_enabled   boolean NOT NULL DEFAULT true,
    routing_priority      integer NOT NULL DEFAULT 100,
    max_open_complaints   integer,
    operating_hours       text,
    verified_at           timestamp with time zone,
    verified_by_user_id   uuid,
    created_at            timestamp with time zone NOT NULL,
    updated_at            timestamp with time zone NOT NULL,
    version               integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_departments_status CHECK (verification_status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','SUSPENDED'))
);
-- Function-based lower(email) index from V2, replaced by plain index (Java lowercases).
CREATE UNIQUE INDEX uq_departments_email_lower ON departments (official_email);

CREATE TABLE department_admins (
    id            uuid PRIMARY KEY,
    user_id       uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    admin_level   varchar(20) NOT NULL,
    is_primary    boolean NOT NULL DEFAULT false,
    title         varchar(80),
    created_at    timestamp with time zone NOT NULL,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT uq_department_admin UNIQUE (department_id, user_id),
    CONSTRAINT ck_admin_level CHECK (admin_level IN ('OWNER','ADMIN','OPERATOR'))
);
-- Partial unique uq_dept_primary_admin cannot be expressed in H2; service enforces it.

CREATE TABLE issue_categories (
    id                 uuid PRIMARY KEY,
    code               varchar(60) NOT NULL UNIQUE,
    name               varchar(120) NOT NULL,
    description        text,
    parent_category_id uuid REFERENCES issue_categories(id),
    icon               varchar(80),
    color              varchar(20),
    display_order      integer NOT NULL DEFAULT 0,
    is_active          boolean NOT NULL DEFAULT true,
    created_at         timestamp with time zone NOT NULL,
    updated_at         timestamp with time zone NOT NULL
);

CREATE TABLE department_issue_categories (
    department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    category_id   uuid NOT NULL REFERENCES issue_categories(id) ON DELETE CASCADE,
    is_primary    boolean NOT NULL DEFAULT false,
    priority      integer NOT NULL DEFAULT 100,
    created_at    timestamp with time zone NOT NULL,
    PRIMARY KEY (department_id, category_id)
);

CREATE TABLE jurisdictions (
    id         uuid PRIMARY KEY,
    code       varchar(50) NOT NULL UNIQUE,
    name       varchar(150) NOT NULL,
    level      varchar(20) NOT NULL,
    parent_id  uuid REFERENCES jurisdictions(id),
    city       varchar(120),
    state      varchar(120),
    country    varchar(120),
    is_active  boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_jurisdiction_level CHECK (level IN ('CITY','ZONE','WARD','LOCALITY'))
);
CREATE INDEX idx_jurisdiction_parent ON jurisdictions (parent_id);

CREATE TABLE service_areas (
    id              uuid PRIMARY KEY,
    department_id   uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    jurisdiction_id uuid REFERENCES jurisdictions(id),
    name            varchar(150) NOT NULL,
    -- Production boundary geometry is derived from this WKT companion column.
    boundary_wkt    varchar(4000),
    covers_ward     varchar(50),
    covers_zone     varchar(50),
    is_active       boolean NOT NULL DEFAULT true,
    valid_from      timestamp with time zone,
    valid_until     timestamp with time zone,
    created_at      timestamp with time zone NOT NULL,
    updated_at      timestamp with time zone NOT NULL
);
CREATE INDEX idx_service_areas_department ON service_areas (department_id);
CREATE INDEX idx_service_areas_ward_zone ON service_areas (covers_ward, covers_zone);

-- ---------------------------------------------------------------------------
-- Complaints (V3)
-- ---------------------------------------------------------------------------
CREATE TABLE complaints (
    id                        uuid PRIMARY KEY,
    complaint_number          varchar(30) NOT NULL,
    citizen_id                uuid NOT NULL REFERENCES users(id),
    category_id               uuid REFERENCES issue_categories(id),
    subcategory_id            uuid REFERENCES issue_categories(id),
    title                     varchar(200),
    description               text NOT NULL,
    additional_info           text,
    status                    varchar(30) NOT NULL,
    severity                  varchar(20) NOT NULL,
    -- No geometry column in H2: JPA maps location_wkt instead.
    location_wkt              varchar(2000),
    latitude                  decimal(10,7) NOT NULL,
    longitude                 decimal(10,7) NOT NULL,
    address_line              varchar(255),
    city                      varchar(120),
    state                     varchar(120),
    postal_code               varchar(20),
    country                   varchar(120),
    ward                      varchar(50),
    zone                      varchar(50),
    location_source           varchar(20) NOT NULL,
    jurisdiction_id           uuid REFERENCES jurisdictions(id),
    related_department_id     uuid REFERENCES departments(id),
    assigned_at               timestamp with time zone,
    resolved_at               timestamp with time zone,
    closed_at                 timestamp with time zone,
    resolution_summary        text,
    resolution_due_at         timestamp with time zone,
    duplicate_of_id           uuid REFERENCES complaints(id),
    duplicate_flag_reason     text,
    ai_confidence             decimal(4,3),
    ai_requires_review        boolean NOT NULL DEFAULT false,
    admin_override_by_user_id uuid REFERENCES users(id),
    admin_override_at         timestamp with time zone,
    admin_override_reason     text,
    created_at                timestamp with time zone NOT NULL,
    updated_at                timestamp with time zone NOT NULL,
    version                   integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_complaints_number UNIQUE (complaint_number),
    CONSTRAINT ck_complaints_status CHECK (status IN (
        'SUBMITTED','AI_ANALYSIS_PENDING','AI_ANALYSIS_COMPLETED','AI_ANALYSIS_FAILED',
        'PENDING_ASSIGNMENT','UNASSIGNED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS',
        'ON_HOLD','RESOLVED','REOPENED','REJECTED','CLOSED')),
    CONSTRAINT ck_complaints_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_complaints_location_source CHECK (location_source IN ('GPS','MANUAL','GEOCODED')),
    CONSTRAINT ck_complaints_description_length CHECK (char_length(description) BETWEEN 10 AND 2000),
    CONSTRAINT ck_complaints_ai_confidence CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1)),
    CONSTRAINT ck_complaints_coordinates CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
);
CREATE INDEX idx_complaints_status_created ON complaints (status, created_at);
CREATE INDEX idx_complaints_citizen ON complaints (citizen_id);
CREATE INDEX idx_complaints_department ON complaints (related_department_id);
CREATE INDEX idx_complaints_ward_zone ON complaints (ward, zone);
-- Partial index equivalents from V6 (H2 does not support WHERE on CREATE INDEX).
CREATE INDEX idx_complaints_unassigned_equiv ON complaints (id);
CREATE INDEX idx_complaints_duplicates_equiv ON complaints (duplicate_of_id);

CREATE TABLE complaint_images (
    id                  uuid PRIMARY KEY,
    complaint_id        uuid REFERENCES complaints(id) ON DELETE CASCADE,
    storage_key         varchar(500) NOT NULL,
    thumbnail_key       varchar(500),
    original_filename   varchar(255),
    content_type        varchar(100),
    size_bytes          bigint,
    width               integer,
    height              integer,
    image_kind          varchar(20) NOT NULL,
    phash               varchar(32),
    checksum_sha256     varchar(64),
    upload_status       varchar(20) NOT NULL DEFAULT 'PENDING',
    uploaded_by_user_id uuid REFERENCES users(id),
    created_at          timestamp with time zone NOT NULL,
    CONSTRAINT uq_images_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_images_kind CHECK (image_kind IN ('CITIZEN','BEFORE','AFTER','EVIDENCE')),
    CONSTRAINT ck_images_upload_status CHECK (upload_status IN ('PENDING','VERIFIED','REJECTED'))
);
CREATE INDEX idx_images_complaint ON complaint_images (complaint_id);
CREATE INDEX idx_images_phash ON complaint_images (phash);

CREATE TABLE ai_analyses (
    id                          uuid PRIMARY KEY,
    complaint_id                uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    provider                    varchar(50),
    model                       varchar(100),
    prompt_version              varchar(30),
    request_payload             text,
    raw_response                text,
    parsed_result               text,
    is_civic_issue              boolean,
    category_code               varchar(60),
    subcategory_code            varchar(60),
    severity                    varchar(20),
    safety_risk                 boolean,
    is_blurry                   boolean,
    ai_description              varchar(500),
    visible_damage              varchar(300),
    requires_additional_info    boolean,
    confidence                  decimal(4,3),
    recommended_department_hint varchar(200),
    requires_human_review       boolean,
    status                      varchar(20) NOT NULL,
    source                      varchar(20) NOT NULL DEFAULT 'AI',
    error_message               text,
    latency_ms                  integer,
    attempt_number              integer,
    created_at                  timestamp with time zone NOT NULL,
    CONSTRAINT ck_ai_severity CHECK (severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_ai_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_ai_status CHECK (status IN ('PENDING','PROCESSING','SUCCESS','COMPLETED','FAILED','TIMEOUT','INVALID_SCHEMA')),
    CONSTRAINT ck_ai_source CHECK (source IN ('AI','ADMIN_OVERRIDE'))
);
CREATE INDEX idx_ai_complaint ON ai_analyses (complaint_id);

CREATE TABLE ai_analysis_runs (
    complaint_id uuid PRIMARY KEY REFERENCES complaints(id) ON DELETE CASCADE,
    analysis_id  uuid NOT NULL REFERENCES ai_analyses(id) ON DELETE CASCADE,
    created_at   timestamp with time zone NOT NULL
);

CREATE TABLE complaint_assignments (
    id                     uuid PRIMARY KEY,
    complaint_id           uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    department_id          uuid NOT NULL REFERENCES departments(id),
    assignment_type        varchar(25) NOT NULL,
    previous_department_id uuid REFERENCES departments(id),
    assigned_by_user_id    uuid REFERENCES users(id),
    routing_decision_id    uuid,
    reason                 text,
    is_active              boolean NOT NULL DEFAULT true,
    assigned_at            timestamp with time zone NOT NULL,
    unassigned_at          timestamp with time zone,
    created_at             timestamp with time zone NOT NULL,
    CONSTRAINT ck_assignment_type CHECK (assignment_type IN
        ('AUTO','MANUAL','REASSIGN','ADMIN_CORRECTION','REOPEN_ROUTE'))
);
-- V3 has a partial UNIQUE index uq_active_assignment ON (complaint_id) WHERE is_active.
-- H2 has no partial indexes: this is a plain index only. The service layer must
-- guarantee at most one active assignment per complaint in tests.
CREATE INDEX idx_assignments_complaint_active ON complaint_assignments (complaint_id, is_active);
CREATE INDEX idx_assignment_department ON complaint_assignments (department_id);

CREATE TABLE complaint_status_history (
    id                 uuid PRIMARY KEY,
    complaint_id       uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    from_status        varchar(30),
    to_status          varchar(30) NOT NULL,
    changed_by_user_id uuid REFERENCES users(id),
    actor_role         varchar(20),
    reason             text,
    metadata           text,
    created_at         timestamp with time zone NOT NULL,
    CONSTRAINT ck_status_history_from CHECK (from_status IS NULL OR from_status IN (
        'SUBMITTED','AI_ANALYSIS_PENDING','AI_ANALYSIS_COMPLETED','AI_ANALYSIS_FAILED',
        'PENDING_ASSIGNMENT','UNASSIGNED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS',
        'ON_HOLD','RESOLVED','REOPENED','REJECTED','CLOSED')),
    CONSTRAINT ck_status_history_to CHECK (to_status IN (
        'SUBMITTED','AI_ANALYSIS_PENDING','AI_ANALYSIS_COMPLETED','AI_ANALYSIS_FAILED',
        'PENDING_ASSIGNMENT','UNASSIGNED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS',
        'ON_HOLD','RESOLVED','REOPENED','REJECTED','CLOSED'))
);
CREATE INDEX idx_status_history_complaint ON complaint_status_history (complaint_id);

CREATE TABLE reassignment_requests (
    id                   uuid PRIMARY KEY,
    complaint_id         uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    from_department_id   uuid REFERENCES departments(id),
    to_department_id     uuid NOT NULL REFERENCES departments(id),
    requested_by_user_id uuid REFERENCES users(id),
    reason               text,
    status               varchar(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id  uuid REFERENCES users(id),
    review_reason        text,
    created_at           timestamp with time zone NOT NULL,
    reviewed_at          timestamp with time zone,
    CONSTRAINT ck_reassign_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX idx_reassign_complaint ON reassignment_requests (complaint_id);

-- ---------------------------------------------------------------------------
-- Routing (V5) - created here so complaint_assignments can reference it.
-- ---------------------------------------------------------------------------
CREATE TABLE routing_decisions (
    id                     uuid PRIMARY KEY,
    complaint_id           uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    ruleset_version        varchar(20),
    ai_category_code       varchar(60),
    confidence             decimal(4,3),
    -- No geometry column in H2; location_point is not JPA-mapped.
    matched_jurisdiction_id uuid REFERENCES jurisdictions(id),
    candidates             text,
    location_point_wkt     varchar(2000),
    is_current             boolean NOT NULL DEFAULT true,
    selected_department_id uuid REFERENCES departments(id),
    outcome                varchar(30) NOT NULL,
    reason                 text,
    created_at             timestamp with time zone NOT NULL,
    CONSTRAINT ck_routing_outcome CHECK (outcome IN
        ('ASSIGNED','UNASSIGNED','AMBIGUOUS','LOW_CONFIDENCE','NON_CIVIC','NO_CAPACITY')),
    CONSTRAINT ck_routing_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
CREATE INDEX idx_routing_decisions_complaint ON routing_decisions (complaint_id);

ALTER TABLE complaint_assignments
    ADD CONSTRAINT fk_assignments_routing_decision
    FOREIGN KEY (routing_decision_id) REFERENCES routing_decisions(id);

-- ---------------------------------------------------------------------------
-- Collaboration (V4)
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              uuid PRIMARY KEY,
    event_type      varchar(100) NOT NULL,
    aggregate_type  varchar(60),
    aggregate_id    varchar(60),
    payload         text NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts        integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp with time zone,
    last_error      text,
    idempotency_key varchar(120) NOT NULL,
    created_at      timestamp with time zone NOT NULL,
    processed_at    timestamp with time zone,
    CONSTRAINT uq_outbox_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PROCESSING','PROCESSED','FAILED'))
);
CREATE INDEX idx_outbox_pending ON outbox_events (status, next_attempt_at);

CREATE TABLE complaint_comments (
    id                  uuid PRIMARY KEY,
    complaint_id        uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    author_user_id      uuid NOT NULL REFERENCES users(id),
    author_role         varchar(20) NOT NULL,
    body                text NOT NULL,
    attachment_image_id uuid REFERENCES complaint_images(id),
    created_at          timestamp with time zone NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    CONSTRAINT ck_comment_author_role CHECK (author_role IN
        ('CITIZEN','DEPARTMENT_ADMIN','SUPER_ADMIN','SYSTEM'))
);
CREATE INDEX idx_complaint_comments_complaint ON complaint_comments (complaint_id);

CREATE TABLE complaint_internal_notes (
    id             uuid PRIMARY KEY,
    complaint_id   uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    department_id  uuid NOT NULL REFERENCES departments(id),
    author_user_id uuid NOT NULL REFERENCES users(id),
    body           text NOT NULL,
    created_at     timestamp with time zone NOT NULL,
    updated_at     timestamp with time zone NOT NULL
);
CREATE INDEX idx_internal_notes_complaint ON complaint_internal_notes (complaint_id);

CREATE TABLE notifications (
    id                uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    complaint_id      uuid REFERENCES complaints(id),
    type              varchar(60) NOT NULL,
    title             varchar(200),
    body              text,
    channel           varchar(20) NOT NULL DEFAULT 'IN_APP',
    delivery_status   varchar(20) NOT NULL DEFAULT 'PENDING',
    payload           text,
    outbox_event_id   uuid REFERENCES outbox_events(id),
    read_at           timestamp with time zone,
    created_at        timestamp with time zone NOT NULL,
    sent_at           timestamp with time zone,
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP','EMAIL','PUSH')),
    CONSTRAINT ck_notification_delivery CHECK (delivery_status IN ('PENDING','SENT','FAILED','READ'))
);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_user_id, read_at);
CREATE INDEX idx_notifications_complaint ON notifications (complaint_id);
CREATE UNIQUE INDEX uq_notification_outbox_recipient ON notifications (outbox_event_id, recipient_user_id);

CREATE TABLE notification_preferences (
    user_id          uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    in_app_enabled   boolean NOT NULL DEFAULT true,
    email_enabled    boolean NOT NULL DEFAULT false,
    push_enabled     boolean NOT NULL DEFAULT false,
    created_at       timestamp with time zone NOT NULL,
    updated_at       timestamp with time zone NOT NULL
);

CREATE TABLE complaint_feedback (
    id                   uuid PRIMARY KEY,
    complaint_id         uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    citizen_id           uuid NOT NULL REFERENCES users(id),
    rating               smallint NOT NULL,
    comment              text,
    resolution_confirmed boolean NOT NULL DEFAULT false,
    dispute_reason       text,
    created_at           timestamp with time zone NOT NULL,
    updated_at           timestamp with time zone NOT NULL,
    CONSTRAINT uq_feedback_complaint UNIQUE (complaint_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

-- ---------------------------------------------------------------------------
-- Governance (V5)
-- ---------------------------------------------------------------------------
CREATE TABLE geocoding_cache (
    id           uuid PRIMARY KEY,
    provider     varchar(50) NOT NULL,
    lat_key      decimal(10,5) NOT NULL,
    lon_key      decimal(10,5) NOT NULL,
    response     text,
    address_line varchar(255),
    city         varchar(120),
    state        varchar(120),
    postal_code  varchar(20),
    country      varchar(120),
    ward         varchar(50),
    zone         varchar(50),
    expires_at   timestamp with time zone,
    created_at   timestamp with time zone NOT NULL,
    CONSTRAINT uq_geocoding_cache_key UNIQUE (provider, lat_key, lon_key),
    CONSTRAINT ck_geocoding_coordinates CHECK (lat_key BETWEEN -90 AND 90 AND lon_key BETWEEN -180 AND 180)
);

CREATE TABLE audit_logs (
    id             uuid PRIMARY KEY,
    actor_user_id  uuid REFERENCES users(id),
    actor_role     varchar(20),
    action         varchar(100) NOT NULL,
    entity_type    varchar(60) NOT NULL,
    entity_id      varchar(60) NOT NULL,
    before_state   text,
    after_state    text,
    reason         varchar(1000),
    ip_address     varchar(45),
    user_agent     text,
    correlation_id varchar(60),
    created_at     timestamp with time zone NOT NULL
);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_action ON audit_logs (action);

CREATE TABLE platform_settings (
    "key"               varchar(100) PRIMARY KEY,
    "value"             text NOT NULL,
    description         text,
    updated_by_user_id  uuid REFERENCES users(id),
    updated_at          timestamp with time zone NOT NULL
);
