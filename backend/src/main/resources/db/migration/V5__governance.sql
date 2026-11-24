-- V5: routing governance, geocoding cache, audit trail and platform settings.
-- PostgreSQL/Supabase target. Flyway owns this schema; do not create these
-- tables manually in the Supabase dashboard.
--
-- Rollback (manual review only, Flyway migrations are forward-only):
--   DROP TRIGGER IF EXISTS trg_platform_settings_updated_at ON platform_settings;
--   DROP SEQUENCE IF EXISTS public.complaint_number_seq;
--   DROP TABLE IF EXISTS platform_settings, audit_logs, geocoding_cache,
--     routing_decisions CASCADE;
--
-- complaint_number_seq provides atomic DB-side numbering. Java reads nextval
-- and formats complaint_number (CIV-YYYY-000123); V3 enforces uniqueness.
-- ip_address is varchar(45) rather than inet to keep the JPA mapping portable
-- across Supabase and the H2 test double (same choice as V2.refresh_tokens).

SET search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Routing decisions (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS routing_decisions (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id           uuid NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    ruleset_version        varchar(20),
    ai_category_code       varchar(60),
    confidence             numeric(4,3),
    -- Authoritative point; no JPA mapping (V6 adds the GiST index).
    location_point         geometry(Point,4326),
    matched_jurisdiction_id uuid REFERENCES jurisdictions(id),
    candidates             jsonb,
    selected_department_id uuid REFERENCES departments(id),
    outcome                varchar(30) NOT NULL,
    reason                 text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_routing_outcome CHECK (outcome IN
        ('ASSIGNED','UNASSIGNED','AMBIGUOUS','LOW_CONFIDENCE','NO_CAPACITY')),
    CONSTRAINT ck_routing_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
CREATE INDEX IF NOT EXISTS idx_routing_decisions_complaint ON routing_decisions (complaint_id);
CREATE INDEX IF NOT EXISTS idx_routing_decisions_department ON routing_decisions (selected_department_id);

-- ---------------------------------------------------------------------------
-- Geocoding cache
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS geocoding_cache (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider     varchar(50) NOT NULL,
    lat_key      numeric(10,5) NOT NULL,
    lon_key      numeric(10,5) NOT NULL,
    response     jsonb,
    address_line varchar(255),
    city         varchar(120),
    state        varchar(120),
    postal_code  varchar(20),
    country      varchar(120),
    ward         varchar(50),
    zone         varchar(50),
    expires_at   timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_geocoding_cache_key UNIQUE (provider, lat_key, lon_key)
);

-- ---------------------------------------------------------------------------
-- Audit log (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id uuid REFERENCES users(id),
    actor_role    varchar(20),
    action        varchar(100) NOT NULL,
    entity_type   varchar(60) NOT NULL,
    entity_id     varchar(60) NOT NULL,
    before_state  jsonb,
    after_state   jsonb,
    ip_address    varchar(45),
    user_agent    text,
    correlation_id varchar(60),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs (action);

-- ---------------------------------------------------------------------------
-- Platform settings
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_settings (
    key                varchar(100) PRIMARY KEY,
    value              jsonb NOT NULL,
    description        text,
    updated_by_user_id uuid REFERENCES users(id),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_platform_settings_updated_at ON platform_settings;
CREATE TRIGGER trg_platform_settings_updated_at BEFORE UPDATE ON platform_settings
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Complaint number sequence (Java calls nextval and formats CIV-YYYY-000123)
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS public.complaint_number_seq
    AS integer START WITH 1 INCREMENT BY 1 NO CYCLE;
