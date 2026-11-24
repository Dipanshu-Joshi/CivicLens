-- V13: Phase 10 password reset tokens.
-- Raw reset tokens are never stored. Only a SHA-256 digest is persisted, and
-- every token is single-use with a short expiry.
SET search_path = public, extensions;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user_active
    ON password_reset_tokens (user_id, used_at, expires_at);

-- Rollback (manual review only):
--   DROP INDEX IF EXISTS idx_password_reset_user_active;
--   DROP TABLE IF EXISTS password_reset_tokens;
