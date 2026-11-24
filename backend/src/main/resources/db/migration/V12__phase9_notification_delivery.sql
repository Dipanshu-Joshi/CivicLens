-- V12: Phase 9 reliable notification delivery.
-- One notification per outbox event/recipient prevents duplicate delivery when
-- the post-commit dispatcher and retry scheduler overlap.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_outbox_recipient
    ON notifications (outbox_event_id, recipient_user_id);

-- User-scoped in-app preference. Email and push columns are reserved but stay
-- false until a real channel is configured; the application rejects enabling them.
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id         uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    in_app_enabled  boolean NOT NULL DEFAULT true,
    email_enabled   boolean NOT NULL DEFAULT false,
    push_enabled    boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
