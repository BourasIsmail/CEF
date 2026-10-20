-- ============================================================================
-- V9 : protection contre les essais répétés de connexion
-- ============================================================================
ALTER TABLE app_user
    ADD COLUMN failed_login_attempts INT         NOT NULL DEFAULT 0 AFTER last_login_at,
    ADD COLUMN locked_until          DATETIME(6) NULL AFTER failed_login_attempts;
