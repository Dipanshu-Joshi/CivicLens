-- V7: environment-gated reference data (issue category tree, example
-- jurisdictions and default platform settings).
-- PostgreSQL/Supabase target. Flyway owns this schema; do not create these
-- rows manually in the Supabase dashboard.
--
-- GATING: the Flyway placeholder ${seedEnabled} must resolve to the literal
-- string 'true' for any row to be inserted. It defaults to false (unset
-- placeholders are left as text by Flyway, which also evaluates to false), so
-- production databases are never seeded by accident. Enable explicitly with
--   spring.flyway.placeholders.seedEnabled=true
-- or -Dflyway.placeholders.seedEnabled=true.
--
-- Rollback (manual review only, Flyway migrations are forward-only):
--   DELETE FROM platform_settings WHERE key IN ('ai.confidence_threshold', ...);
--   DELETE FROM jurisdictions WHERE code IN ('WARD-12','WARD-13','ZONE-3','CITY-X');
--   DELETE FROM issue_categories WHERE code LIKE 'ROADS%' OR code LIKE 'WASTE%'
--     OR code LIKE 'WATER%' OR code LIKE 'LIGHTING%' OR code LIKE 'TRAFFIC%'
--     OR code LIKE 'SANITATION%' OR code = 'UNCATEGORIZED';
-- All seeded rows use stable UUIDs/codes so they are identifiable and idempotent.

SET search_path = public, extensions;

DO $$
BEGIN
    IF '${seedEnabled}' = 'true' THEN

        -- -------------------------------------------------------------------
        -- Issue categories: roots first, then subcategories (self-FK).
        -- Unknown AI category codes map to UNCATEGORIZED in the service layer.
        -- -------------------------------------------------------------------
        INSERT INTO issue_categories (id, code, name, description, parent_category_id, icon, color, display_order, is_active)
        VALUES
            ('10000000-0000-4000-8000-000000000001'::uuid, 'ROADS', 'Roads & Pavement', 'Road surface, potholes and footpaths', NULL, 'road', '#8D6E63', 10, true),
            ('10000000-0000-4000-8000-000000000002'::uuid, 'WASTE', 'Waste Management', 'Household waste collection and dumping', NULL, 'delete', '#607D8B', 20, true),
            ('10000000-0000-4000-8000-000000000003'::uuid, 'WATER', 'Water & Drainage', 'Supply leaks and storm drainage', NULL, 'water', '#2196F3', 30, true),
            ('10000000-0000-4000-8000-000000000004'::uuid, 'LIGHTING', 'Street Lighting', 'Public street lights', NULL, 'lightbulb', '#FFC107', 40, true),
            ('10000000-0000-4000-8000-000000000005'::uuid, 'TRAFFIC', 'Traffic & Signals', 'Signals, signs and parking', NULL, 'traffic', '#F44336', 50, true),
            ('10000000-0000-4000-8000-000000000006'::uuid, 'SANITATION', 'Public Sanitation', 'Public toilets and hygiene', NULL, 'sanitizer', '#009688', 60, true),
            ('10000000-0000-4000-8000-000000000007'::uuid, 'UNCATEGORIZED', 'Uncategorized', 'Fallback for unknown AI categories', NULL, 'help', '#9E9E9E', 999, true)
        ON CONFLICT (code) DO NOTHING;

        INSERT INTO issue_categories (id, code, name, description, parent_category_id, icon, color, display_order, is_active)
        VALUES
            ('10000000-0000-4000-8000-000000000101'::uuid, 'ROADS_POTHOLE', 'Pothole', 'Pothole or damaged road surface', '10000000-0000-4000-8000-000000000001'::uuid, 'construction', '#8D6E63', 11, true),
            ('10000000-0000-4000-8000-000000000102'::uuid, 'ROADS_SIDEWALK', 'Damaged Footpath', 'Cracked or missing pavement', '10000000-0000-4000-8000-000000000001'::uuid, 'footpath', '#8D6E63', 12, true),
            ('10000000-0000-4000-8000-000000000201'::uuid, 'WASTE_MISSED_COLLECTION', 'Missed Collection', 'Scheduled collection not performed', '10000000-0000-4000-8000-000000000002'::uuid, 'schedule', '#607D8B', 21, true),
            ('10000000-0000-4000-8000-000000000202'::uuid, 'WASTE_ILLEGAL_DUMPING', 'Illegal Dumping', 'Unauthorized waste disposal', '10000000-0000-4000-8000-000000000002'::uuid, 'warning', '#607D8B', 22, true),
            ('10000000-0000-4000-8000-000000000301'::uuid, 'WATER_LEAK', 'Water Leak', 'Leaking supply pipe or hydrant', '10000000-0000-4000-8000-000000000003'::uuid, 'water_drop', '#2196F3', 31, true),
            ('10000000-0000-4000-8000-000000000302'::uuid, 'WATER_DRAIN_BLOCKAGE', 'Blocked Drain', 'Blocked storm water drain', '10000000-0000-4000-8000-000000000003'::uuid, 'plumbing', '#2196F3', 32, true),
            ('10000000-0000-4000-8000-000000000401'::uuid, 'LIGHTING_OUTAGE', 'Light Outage', 'Street light not working', '10000000-0000-4000-8000-000000000004'::uuid, 'power_off', '#FFC107', 41, true),
            ('10000000-0000-4000-8000-000000000402'::uuid, 'LIGHTING_FLICKER', 'Flickering Light', 'Intermittent or flickering light', '10000000-0000-4000-8000-000000000004'::uuid, 'flash_on', '#FFC107', 42, true),
            ('10000000-0000-4000-8000-000000000501'::uuid, 'TRAFFIC_SIGNAL_FAULT', 'Signal Fault', 'Faulty traffic signal', '10000000-0000-4000-8000-000000000005'::uuid, 'traffic_light', '#F44336', 51, true),
            ('10000000-0000-4000-8000-000000000502'::uuid, 'TRAFFIC_ILLEGAL_PARKING', 'Illegal Parking', 'Vehicle parked in a restricted area', '10000000-0000-4000-8000-000000000005'::uuid, 'local_parking', '#F44336', 52, true),
            ('10000000-0000-4000-8000-000000000601'::uuid, 'SANITATION_PUBLIC_TOILET', 'Public Toilet Issue', 'Cleanliness or damage at a public toilet', '10000000-0000-4000-8000-000000000006'::uuid, 'wc', '#009688', 61, true)
        ON CONFLICT (code) DO NOTHING;

        -- -------------------------------------------------------------------
        -- Example jurisdiction hierarchy. Boundaries remain NULL; PostGIS
        -- polygons are supplied by administrators at runtime.
        -- -------------------------------------------------------------------
        INSERT INTO jurisdictions (id, code, name, level, parent_id, city, state, country, is_active)
        VALUES
            ('20000000-0000-4000-8000-000000000001'::uuid, 'CITY-X', 'Example City', 'CITY', NULL, 'Example City', 'Example State', 'IN', true),
            ('20000000-0000-4000-8000-000000000002'::uuid, 'ZONE-3', 'Zone 3', 'ZONE', '20000000-0000-4000-8000-000000000001'::uuid, 'Example City', 'Example State', 'IN', true),
            ('20000000-0000-4000-8000-000000000003'::uuid, 'WARD-12', 'Ward 12', 'WARD', '20000000-0000-4000-8000-000000000002'::uuid, 'Example City', 'Example State', 'IN', true),
            ('20000000-0000-4000-8000-000000000004'::uuid, 'WARD-13', 'Ward 13', 'WARD', '20000000-0000-4000-8000-000000000002'::uuid, 'Example City', 'Example State', 'IN', true)
        ON CONFLICT (code) DO NOTHING;

        -- -------------------------------------------------------------------
        -- Default platform settings (overridable at runtime via the admin UI).
        -- -------------------------------------------------------------------
        INSERT INTO platform_settings (key, value, description)
        VALUES
            ('ai.enabled', 'true'::jsonb, 'Enable AI classification of new complaints'),
            ('ai.confidence_threshold', '0.75'::jsonb, 'Minimum confidence for automatic routing'),
            ('sla.hours.CRITICAL', '4'::jsonb, 'Resolution target hours for CRITICAL severity'),
            ('sla.hours.HIGH', '24'::jsonb, 'Resolution target hours for HIGH severity'),
            ('sla.hours.MEDIUM', '72'::jsonb, 'Resolution target hours for MEDIUM severity'),
            ('sla.hours.LOW', '168'::jsonb, 'Resolution target hours for LOW severity'),
            ('duplicates.radius_meters', '100'::jsonb, 'Radius used when suggesting duplicate complaints'),
            ('map.default_precision_digits', '3'::jsonb, 'Coordinate digits shown by default on public maps'),
            ('retention_months', '24'::jsonb, 'Retention period for audit logs and AI analyses'),
            ('complaint.number_prefix', '"CIV"'::jsonb, 'Prefix used by the Java complaint-number generator')
        ON CONFLICT (key) DO NOTHING;

    END IF;
END
$$;
