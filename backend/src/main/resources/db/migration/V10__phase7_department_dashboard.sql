-- V10: Phase 7 department dashboard support.
-- Resolution summaries are authored by department admins on completion and are
-- distinct from the short status-history reason.

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS resolution_summary text;

CREATE INDEX IF NOT EXISTS idx_complaints_department_created
    ON complaints (related_department_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_complaints_department_status
    ON complaints (related_department_id, status);

CREATE INDEX IF NOT EXISTS idx_status_history_complaint_created
    ON complaint_status_history (complaint_id, created_at DESC);
