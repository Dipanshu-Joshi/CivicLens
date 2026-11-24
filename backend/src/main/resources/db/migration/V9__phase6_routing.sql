-- V9: Phase 6 deterministic routing support.
-- PostGIS geometry remains authoritative; WKT companion columns are written by
-- JPA and converted by BEFORE INSERT/UPDATE triggers, matching V3's pattern.

SET search_path = public, extensions;

-- Geometry written by a plain POLYGON must be cast to MultiPolygon because the
-- production service_areas.boundary column is geometry(MultiPolygon,4326).
CREATE OR REPLACE FUNCTION public.set_service_area_boundary_from_wkt()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.boundary_wkt IS NOT NULL THEN
        NEW.boundary := ST_Multi(ST_GeomFromText(NEW.boundary_wkt, 4326));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE routing_decisions
    ADD COLUMN IF NOT EXISTS location_point_wkt varchar(2000),
    ADD COLUMN IF NOT EXISTS is_current boolean NOT NULL DEFAULT true;

CREATE OR REPLACE FUNCTION public.set_routing_decision_location_from_wkt()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.location_point_wkt IS NOT NULL THEN
        NEW.location_point := ST_GeomFromText(NEW.location_point_wkt, 4326);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_routing_decisions_location ON routing_decisions;
CREATE TRIGGER trg_routing_decisions_location
    BEFORE INSERT OR UPDATE OF location_point_wkt ON routing_decisions
    FOR EACH ROW EXECUTE FUNCTION public.set_routing_decision_location_from_wkt();

-- Existing rows predate is_current. Keep only the newest decision current so
-- the partial unique index can be created on installations with prior history.
UPDATE routing_decisions d
SET is_current = false
WHERE d.id NOT IN (
    SELECT DISTINCT ON (complaint_id) id
    FROM routing_decisions
    ORDER BY complaint_id, created_at DESC, id DESC
);

-- At most one decision is authoritative for a complaint. Historical decisions
-- are retained with is_current=false for audit and reroute history.
CREATE UNIQUE INDEX IF NOT EXISTS uq_current_routing_decision
    ON routing_decisions (complaint_id) WHERE is_current;

ALTER TABLE routing_decisions DROP CONSTRAINT IF EXISTS ck_routing_outcome;
ALTER TABLE routing_decisions ADD CONSTRAINT ck_routing_outcome CHECK (outcome IN
    ('ASSIGNED','UNASSIGNED','AMBIGUOUS','LOW_CONFIDENCE','NON_CIVIC','NO_CAPACITY'));

CREATE INDEX IF NOT EXISTS idx_routing_decisions_current
    ON routing_decisions (complaint_id, is_current);
