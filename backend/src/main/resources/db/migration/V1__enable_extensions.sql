-- V1: extensions required by the platform.
-- Works on Supabase (extensions are installed into the `extensions` schema)
-- and on a local PostgreSQL 16 + PostGIS 3.4 instance.
-- Rollback (manual review only): DROP FUNCTION IF EXISTS public.set_updated_at();
--   Extension removal is intentionally not automated.

CREATE SCHEMA IF NOT EXISTS extensions;
SET search_path = public, extensions;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
