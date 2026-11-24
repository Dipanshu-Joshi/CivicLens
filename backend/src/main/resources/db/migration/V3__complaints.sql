-- V3: complaints, images, AI analysis and assignment/history workflow.
-- PostgreSQL/Supabase target. Flyway owns this schema; do not create these
-- tables manually in the Supabase dashboard.
--
-- Rollback (manual review only, Flyway migrations are forward-only):
--   DROP TRIGGER IF EXISTS trg_service_areas_boundary ON service_areas;
--   DROP FUNCTION IF EXISTS public.set_service_area_boundary_from_wkt();
--   ALTER TABLE service_areas DROP COLUMN IF EXISTS boundary_wkt;
--   DROP TRIGGER IF EXISTS trg_complaints_location ON complaints;
--   DROP FUNCTION IF EXISTS public.set_complaint_location_from_wkt();
--   DROP TABLE IF EXISTS reassignment_requests, complaint_status_history,
--     complaint_assignments, ai_analyses, complaint_images, complaints CASCADE;
--
-- DESIGN NOTE (geometry / JPA): PostGIS geometry remains authoritative exactly
-- as in DATABASE_DESIGN.md. JPA does not map geometry columns. Java instead
-- writes the text companion columns location_wkt / boundary_wkt and the BEFORE
-- INSERT/UPDATE triggers below derive the geometry with ST_GeomFromText(..., 4326).
-- Point WKT must be written as "longitude latitude" (SRID 4326).
-- complaint_number is generated in Java (CIV-YYYY-000123); no DB default is used.
-- The partial unique index uq_active_assignment lives here because it is a
-- correctness constraint; non-unique partial indexes are deferred to V6.

SET search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Complaints
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaints (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_number          varchar(30) NOT NULL,
    citizen_id                uuid NOT NULL REFERENCES users(id),
    category_id               uuid REFERENCES issue_categories(id),
    subcategory_id            uuid REFERENCES issue_categories(id),
    title                     varchar(200),
    description               text NOT NULL,
    additional_info           text,
    status                    varchar(30) NOT NULL,
    severity                  varchar(20) NOT NULL,
    -- Authoritative PostGIS point; populated from location_wkt by trigger.
    location                  geometry(Point,4326) NOT NULL,
    location_wkt              varchar(2000),
    latitude                  numeric(10,7) NOT NULL,
    longitude                 numeric(10,7) NOT NULL,
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
    assigned_at               timestamptz,
    resolved_at               timestamptz,
    closed_at                 timestamptz,
    resolution_due_at         timestamptz,
    duplicate_of_id           uuid REFERENCES complaints(id),
    duplicate_flag_reason     text,
    ai_confidence             numeric(4,3),
    ai_requires_review        boolean NOT NULL DEFAULT false,
    admin_override_by_user_id uuid REFERENCES users(id),
    admin_override_at         timestamptz,
    admin_override_reason     text,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    version                   integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_complaints_number UNIQUE (complaint_number),
    CONSTRAINT ck_complaints_status CHECK (status IN (
        'SUBMITTED','AI_ANALYSIS_PENDING','AI_ANALYSIS_COMPLETED','AI_ANALYSIS_FAILED',
        'PENDING_ASSIGNMENT','UNASSIGNED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS',
        'ON_HOLD','RESOLVED','REOPENED','REJECTED','CLOSED')),
    CONSTRAINT ck_complaints_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_complaints_location_source CHECK (location_source IN ('GPS','MANUAL','GEOCODED')),
    CONSTRAINT ck_complaints_description_length CHECK (char_length(description) BETWEEN 10 AND 2000),
    CONSTRAINT ck_complaints_ai_confidence CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1))
);
CREATE INDEX IF NOT EXISTS idx_complaints_status_created ON complaints (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_complaints_citizen ON complaints (citizen_id);
CREATE INDEX IF NOT EXISTS idx_complaints_category ON complaints (category_id);
CREATE INDEX IF NOT EXISTS idx_complaints_department ON complaints (related_department_id);
CREATE INDEX IF NOT EXISTS idx_complaints_ward_zone ON complaints (ward, zone);
CREATE INDEX IF NOT EXISTS idx_complaints_due ON complaints (resolution_due_at);

-- Trigger: derive the authoritative geometry from the JPA-mapped WKT column.
CREATE OR REPLACE FUNCTION public.set_complaint_location_from_wkt()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.location_wkt IS NOT NULL THEN
        NEW.location := ST_GeomFromText(NEW.location_wkt, 4326);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_complaints_location ON complaints;
CREATE TRIGGER trg_complaints_location
    BEFORE INSERT OR UPDATE OF location_wkt ON complaints
    FOR EACH ROW EXECUTE FUNCTION public.set_complaint_location_from_wkt();

DROP TRIGGER IF EXISTS trg_complaints_updated_at ON complaints;
CREATE TRIGGER trg_complaints_updated_at BEFORE UPDATE ON complaints
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Service areas (V2 table): WKT companion column for JPA + trigger.
-- ---------------------------------------------------------------------------
ALTER TABLE service_areas ADD COLUMN IF NOT EXISTS boundary_wkt varchar(4000);

CREATE OR REPLACE FUNCTION public.set_service_area_boundary_from_wkt()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.boundary_wkt IS NOT NULL THEN
        NEW.boundary := ST_GeomFromText(NEW.boundary_wkt, 4326);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_service_areas_boundary ON service_areas;
CREATE TRIGGER trg_service_areas_boundary
    BEFORE INSERT OR UPDATE OF boundary_wkt ON service_areas
    FOR EACH ROW EXECUTE FUNCTION public.set_service_area_boundary_from_wkt();

-- ---------------------------------------------------------------------------
-- Complaint images
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_images (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id        uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
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
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_images_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_images_kind CHECK (image_kind IN ('CITIZEN','BEFORE','AFTER','EVIDENCE')),
    CONSTRAINT ck_images_upload_status CHECK (upload_status IN ('PENDING','VERIFIED','REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_images_complaint ON complaint_images (complaint_id);
CREATE INDEX IF NOT EXISTS idx_images_phash ON complaint_images (phash);

-- ---------------------------------------------------------------------------
-- AI analyses (raw_response retained for audit; advisory hints only)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_analyses (
    id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id                 uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    provider                     varchar(50),
    model                        varchar(100),
    prompt_version               varchar(30),
    request_payload              jsonb,
    raw_response                 jsonb,
    parsed_result                jsonb,
    is_civic_issue               boolean,
    category_code                varchar(60),
    subcategory_code             varchar(60),
    severity                     varchar(20),
    safety_risk                  boolean,
    is_blurry                    boolean,
    requires_additional_info     boolean,
    confidence                   numeric(4,3),
    recommended_department_hint  varchar(200),
    requires_human_review        boolean,
    status                       varchar(20) NOT NULL,
    source                       varchar(20) NOT NULL DEFAULT 'AI',
    error_message                text,
    latency_ms                   integer,
    attempt_number               integer,
    created_at                   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_severity CHECK (severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_ai_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_ai_status CHECK (status IN ('SUCCESS','FAILED','TIMEOUT','INVALID_SCHEMA')),
    CONSTRAINT ck_ai_source CHECK (source IN ('AI','ADMIN_OVERRIDE'))
);
CREATE INDEX IF NOT EXISTS idx_ai_complaint ON ai_analyses (complaint_id);
CREATE INDEX IF NOT EXISTS idx_ai_created ON ai_analyses (created_at);

-- ---------------------------------------------------------------------------
-- Complaint assignments (append-only; only is_active flips to false)
-- routing_decision_id has no FK here: routing_decisions is created in V5 and
-- the cross-migration FK is added in V6.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_assignments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id          uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    department_id         uuid NOT NULL REFERENCES departments(id),
    assignment_type       varchar(25) NOT NULL,
    previous_department_id uuid REFERENCES departments(id),
    assigned_by_user_id   uuid REFERENCES users(id),
    routing_decision_id   uuid,
    reason                text,
    is_active             boolean NOT NULL DEFAULT true,
    assigned_at           timestamptz NOT NULL DEFAULT now(),
    unassigned_at         timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_assignment_type CHECK (assignment_type IN
        ('AUTO','MANUAL','REASSIGN','ADMIN_CORRECTION','REOPEN_ROUTE'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_assignment
    ON complaint_assignments (complaint_id) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_assignment_department ON complaint_assignments (department_id);
CREATE INDEX IF NOT EXISTS idx_assignment_assigned_at ON complaint_assignments (assigned_at);

-- ---------------------------------------------------------------------------
-- Complaint status history (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS complaint_status_history (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id      uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    from_status       varchar(30),
    to_status         varchar(30) NOT NULL,
    changed_by_user_id uuid REFERENCES users(id),
    actor_role        varchar(20),
    reason            text,
    metadata          jsonb,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_status_history_from CHECK (from_status IS NULL OR from_status IN (
        'SUBMITTED','AI_ANALYSIS_PENDING','AI_ANALYSIS_COMPLETED','AI_ANALYSIS_FAILED',
        'PENDING_ASSIGNMENT','UNASSIGNED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS',
        'ON_HOLD','RESOLVED','REOPENED','REJECTED','CLOSED')),
    CONSTRAINT ck_status_history_to CHECK (to_status IN (
        'SUBMITTED','AI_ANALYSIS_PENDING','AI_ANALYSIS_COMPLETED','AI_ANALYSIS_FAILED',
        'PENDING_ASSIGNMENT','UNASSIGNED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS',
        'ON_HOLD','RESOLVED','REOPENED','REJECTED','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_status_history_complaint ON complaint_status_history (complaint_id);
CREATE INDEX IF NOT EXISTS idx_status_history_created ON complaint_status_history (created_at);

-- ---------------------------------------------------------------------------
-- Reassignment requests (authorized reassignment workflow)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reassignment_requests (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id        uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    from_department_id  uuid REFERENCES departments(id),
    to_department_id    uuid NOT NULL REFERENCES departments(id),
    requested_by_user_id uuid REFERENCES users(id),
    reason              text,
    status              varchar(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id uuid REFERENCES users(id),
    review_reason       text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    reviewed_at         timestamptz,
    CONSTRAINT ck_reassign_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_reassign_status ON reassignment_requests (status);
CREATE INDEX IF NOT EXISTS idx_reassign_complaint ON reassignment_requests (complaint_id);
