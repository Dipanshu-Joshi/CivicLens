-- Phase 5: secure uploads and provider-independent AI analysis workflow.

-- Pending uploads are created before a complaint exists and attached on submit.
ALTER TABLE complaint_images
    ALTER COLUMN complaint_id DROP NOT NULL;

-- Additional structured AI fields and expanded lifecycle states.
ALTER TABLE ai_analyses
    ADD COLUMN IF NOT EXISTS ai_description varchar(500),
    ADD COLUMN IF NOT EXISTS visible_damage varchar(300);

ALTER TABLE ai_analyses
    DROP CONSTRAINT IF EXISTS ck_ai_status;

ALTER TABLE ai_analyses
    ADD CONSTRAINT ck_ai_status
    CHECK (status IN ('PENDING','PROCESSING','SUCCESS','COMPLETED','FAILED','TIMEOUT','INVALID_SCHEMA'));

-- V3 already permits all complaint AI statuses, so no complaint status change is required.

-- Append-only guard table: only one active analysis per complaint.
CREATE TABLE IF NOT EXISTS ai_analysis_runs (
    complaint_id uuid PRIMARY KEY REFERENCES complaints(id) ON DELETE CASCADE,
    analysis_id  uuid NOT NULL REFERENCES ai_analyses(id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now()
);
