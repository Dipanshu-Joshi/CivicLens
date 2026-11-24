-- V6: geospatial indexes, partial indexes, remaining checks/FKs and
-- disabled RLS scaffolding.
-- PostgreSQL/Supabase target. Flyway owns this schema; do not create these
-- objects manually in the Supabase dashboard.
--
-- Rollback (manual review only, Flyway migrations are forward-only):
--   ALTER TABLE complaint_assignments DROP CONSTRAINT IF EXISTS fk_assignments_routing_decision;
--   ALTER TABLE reassignment_requests DROP CONSTRAINT IF EXISTS ck_reassign_review_dates;
--   ALTER TABLE complaint_assignments DROP CONSTRAINT IF EXISTS ck_assignment_dates;
--   ALTER TABLE geocoding_cache DROP CONSTRAINT IF EXISTS ck_geocoding_coordinates;
--   ALTER TABLE complaints DROP CONSTRAINT IF EXISTS ck_complaints_dates;
--   ALTER TABLE complaints DROP CONSTRAINT IF EXISTS ck_complaints_coordinates;
--   DROP INDEX IF EXISTS idx_audit_created, idx_complaints_duplicates,
--     idx_complaints_unassigned, idx_service_areas_ward_zone,
--     idx_routing_decisions_location, idx_complaints_location;
--   ALTER TABLE service_areas DROP COLUMN IF EXISTS valid_until;
--   ALTER TABLE service_areas DROP COLUMN IF EXISTS valid_from;
--
-- The partial unique index uq_active_assignment (one active assignment per
-- complaint) was created in V3 because it is a correctness constraint. The
-- H2 test double cannot express partial indexes and enforces it in the service
-- layer instead.

SET search_path = public, extensions;

-- ---------------------------------------------------------------------------
-- Complete the service_areas definition from DATABASE_DESIGN.md §4.2
-- (valid_from / valid_until were not present in V2).
-- ---------------------------------------------------------------------------
ALTER TABLE service_areas ADD COLUMN IF NOT EXISTS valid_from timestamptz;
ALTER TABLE service_areas ADD COLUMN IF NOT EXISTS valid_until timestamptz;

-- ---------------------------------------------------------------------------
-- Geospatial (GiST) indexes. Geometry remains authoritative; JPA reads the
-- denormalised latitude/longitude/ward/zone copies on complaints.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_complaints_location
    ON complaints USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_routing_decisions_location
    ON routing_decisions USING GIST (location_point);

-- ---------------------------------------------------------------------------
-- Fast fallback / partial read paths
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_service_areas_ward_zone
    ON service_areas (covers_ward, covers_zone);
CREATE INDEX IF NOT EXISTS idx_complaints_unassigned
    ON complaints (id)
    WHERE related_department_id IS NULL
      AND status IN ('UNASSIGNED','PENDING_ASSIGNMENT');
CREATE INDEX IF NOT EXISTS idx_complaints_duplicates
    ON complaints (duplicate_of_id)
    WHERE duplicate_of_id IS NOT NULL;

-- BRIN suits the append-only audit stream.
CREATE INDEX IF NOT EXISTS idx_audit_created
    ON audit_logs USING BRIN (created_at);

-- ---------------------------------------------------------------------------
-- Remaining checks (guarded so the migration stays re-runnable in review).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_complaints_coordinates') THEN
        ALTER TABLE complaints ADD CONSTRAINT ck_complaints_coordinates
            CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_complaints_dates') THEN
        ALTER TABLE complaints ADD CONSTRAINT ck_complaints_dates
            CHECK ((assigned_at IS NULL OR assigned_at >= created_at)
               AND (resolved_at IS NULL OR resolved_at >= created_at)
               AND (closed_at IS NULL OR closed_at >= created_at));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_geocoding_coordinates') THEN
        ALTER TABLE geocoding_cache ADD CONSTRAINT ck_geocoding_coordinates
            CHECK (lat_key BETWEEN -90 AND 90 AND lon_key BETWEEN -180 AND 180);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assignment_dates') THEN
        ALTER TABLE complaint_assignments ADD CONSTRAINT ck_assignment_dates
            CHECK (unassigned_at IS NULL OR unassigned_at >= assigned_at);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_reassign_review_dates') THEN
        ALTER TABLE reassignment_requests ADD CONSTRAINT ck_reassign_review_dates
            CHECK (reviewed_at IS NULL OR reviewed_at >= created_at);
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- Cross-migration FK: complaint_assignments (V3) -> routing_decisions (V5).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_assignments_routing_decision') THEN
        ALTER TABLE complaint_assignments ADD CONSTRAINT fk_assignments_routing_decision
            FOREIGN KEY (routing_decision_id) REFERENCES routing_decisions(id);
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- RLS scaffolding — DISABLED. Every statement below is intentionally a comment.
-- Enable only after a dedicated security review: the application connects as
-- the table owner and would bypass policies unless FORCE ROW LEVEL SECURITY is
-- also used, and current_setting() would have to be populated per request.
-- ---------------------------------------------------------------------------
-- ALTER TABLE complaints ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE complaint_internal_notes ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY complaints_owner_select ON complaints
--     FOR SELECT USING (citizen_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);
-- CREATE POLICY notifications_recipient_select ON notifications
--     FOR SELECT USING (recipient_user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid);
-- CREATE POLICY internal_notes_dept_select ON complaint_internal_notes
--     FOR SELECT USING (department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid);
-- CREATE POLICY audit_read_super_admin ON audit_logs
--     FOR SELECT USING (current_setting('app.current_role', true) = 'SUPER_ADMIN');
