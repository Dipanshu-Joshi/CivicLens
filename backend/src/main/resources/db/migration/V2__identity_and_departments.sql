-- V2: identity, departments and reference data.
-- PostgreSQL/Supabase target. NOT executed yet: Supabase credentials are pending
-- (DEVELOPMENT_ROADMAP.md §1.1). Flyway owns this schema; do not create these
-- tables manually in the Supabase dashboard.
--
-- Rollback (manual review only, Flyway migrations are forward-only):
--   DROP TABLE IF EXISTS service_areas, jurisdictions, department_issue_categories,
--     issue_categories, department_admins, departments, refresh_tokens,
--     citizen_profiles, users CASCADE;
--
-- Note: refresh_tokens.ip_address is varchar(45) rather than inet so the JPA
-- mapping stays portable across Supabase and the H2 test double.

SET search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email                  varchar(255) NOT NULL,
    password_hash          varchar(255) NOT NULL,
    role                   varchar(20)  NOT NULL,
    account_status         varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    full_name              varchar(150) NOT NULL,
    phone                  varchar(30),
    email_verified         boolean      NOT NULL DEFAULT false,
    failed_login_attempts  integer      NOT NULL DEFAULT 0,
    locked_until           timestamptz,
    last_login_at          timestamptz,
    locale                 varchar(10)  NOT NULL DEFAULT 'en',
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    version                integer      NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_role CHECK (role IN ('CITIZEN','DEPARTMENT_ADMIN','SUPER_ADMIN')),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('ACTIVE','PENDING_VERIFICATION','SUSPENDED','DISABLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email));
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
CREATE INDEX IF NOT EXISTS idx_users_account_status ON users (account_status);
CREATE INDEX IF NOT EXISTS idx_users_last_login ON users (last_login_at);

CREATE TABLE IF NOT EXISTS citizen_profiles (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    default_location       geometry(Point,4326),
    latitude               double precision,
    longitude              double precision,
    address_line           varchar(255),
    city                   varchar(120),
    state                  varchar(120),
    postal_code            varchar(20),
    country                varchar(120),
    notification_preferences jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_citizen_profiles_location ON citizen_profiles USING GIST (default_location);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(255) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid REFERENCES refresh_tokens(id),
    ip_address  varchar(45),
    user_agent  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------------
-- Departments and reference data
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS departments (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
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
    office_location       geometry(Point,4326),
    verification_status   varchar(25)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    rejection_reason      text,
    is_active             boolean NOT NULL DEFAULT true,
    accepts_complaints    boolean NOT NULL DEFAULT false,
    auto_assign_enabled   boolean NOT NULL DEFAULT true,
    routing_priority      integer NOT NULL DEFAULT 100,
    max_open_complaints   integer,
    operating_hours       jsonb,
    verified_at           timestamptz,
    verified_by_user_id   uuid REFERENCES users(id),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    version               integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_departments_status CHECK (verification_status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','SUSPENDED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_departments_email_lower ON departments (lower(official_email));
CREATE INDEX IF NOT EXISTS idx_departments_status_type ON departments (verification_status, department_type);
CREATE INDEX IF NOT EXISTS idx_departments_city ON departments (city);
CREATE INDEX IF NOT EXISTS idx_departments_location ON departments USING GIST (office_location);

CREATE TABLE IF NOT EXISTS department_admins (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    admin_level   varchar(20) NOT NULL DEFAULT 'OWNER',
    is_primary    boolean NOT NULL DEFAULT false,
    title         varchar(80),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_department_admin UNIQUE (department_id, user_id),
    CONSTRAINT ck_admin_level CHECK (admin_level IN ('OWNER','ADMIN','OPERATOR'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dept_primary_admin ON department_admins (department_id) WHERE is_primary;

CREATE TABLE IF NOT EXISTS issue_categories (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(60) NOT NULL UNIQUE,
    name                varchar(120) NOT NULL,
    description         text,
    parent_category_id  uuid REFERENCES issue_categories(id),
    icon                varchar(80),
    color               varchar(20),
    display_order       integer NOT NULL DEFAULT 0,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS department_issue_categories (
    department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    category_id   uuid NOT NULL REFERENCES issue_categories(id) ON DELETE CASCADE,
    is_primary    boolean NOT NULL DEFAULT false,
    priority      integer NOT NULL DEFAULT 100,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (department_id, category_id)
);

CREATE TABLE IF NOT EXISTS jurisdictions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(50) NOT NULL UNIQUE,
    name        varchar(150) NOT NULL,
    level       varchar(20) NOT NULL,
    parent_id   uuid REFERENCES jurisdictions(id),
    city        varchar(120),
    state       varchar(120),
    country     varchar(120),
    boundary    geometry(MultiPolygon,4326),
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_jurisdiction_level CHECK (level IN ('CITY','ZONE','WARD','LOCALITY'))
);
CREATE INDEX IF NOT EXISTS idx_jurisdiction_parent ON jurisdictions (parent_id);
CREATE INDEX IF NOT EXISTS idx_jurisdiction_boundary ON jurisdictions USING GIST (boundary);

CREATE TABLE IF NOT EXISTS service_areas (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id   uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    jurisdiction_id uuid REFERENCES jurisdictions(id),
    name            varchar(150) NOT NULL,
    boundary        geometry(MultiPolygon,4326) NOT NULL,
    covers_ward     varchar(50),
    covers_zone     varchar(50),
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_service_areas_department ON service_areas (department_id);
CREATE INDEX IF NOT EXISTS idx_service_areas_boundary ON service_areas USING GIST (boundary);

-- Keep updated_at consistent for all Phase 2 tables (function created in V1).
DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
DROP TRIGGER IF EXISTS trg_departments_updated_at ON departments;
CREATE TRIGGER trg_departments_updated_at BEFORE UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
DROP TRIGGER IF EXISTS trg_department_admins_updated_at ON department_admins;
CREATE TRIGGER trg_department_admins_updated_at BEFORE UPDATE ON department_admins
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
