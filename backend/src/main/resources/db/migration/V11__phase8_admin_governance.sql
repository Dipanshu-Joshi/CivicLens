-- V11: Phase 8 platform governance support.
-- Administrative mutations must carry a human-readable reason. The reason is
-- captured by AuditAspect from the request record and persisted alongside the
-- existing append-only audit row.

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS reason varchar(1000);

CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_entity_id ON audit_logs (entity_id);
