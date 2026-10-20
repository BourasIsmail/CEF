-- ============================================================================
-- V6 : Utilisateurs, jetons de rafraîchissement, journal d'audit
--
-- Profils de la première version : ADMIN, REGIONAL, PROVINCIAL.
-- CENTER_MANAGER, HR_OFFICER et READ_ONLY sont prévus comme extensions futures
-- (déjà autorisés par la contrainte, sans changement de schéma).
-- Portée (scope) : chaque utilisateur est rattaché à une région, une province
-- ou un centre selon son profil.
-- ============================================================================

CREATE TABLE app_user (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    username                   VARCHAR(100) NOT NULL,
    password_hash              VARCHAR(100) NOT NULL,
    full_name                  VARCHAR(200) NOT NULL,
    email                      VARCHAR(200) NULL,
    role                       VARCHAR(30)  NOT NULL,
    regional_directorate_id    BIGINT       NULL,
    provincial_directorate_id  BIGINT       NULL,
    center_id                  BIGINT       NULL,
    enabled                    BOOLEAN      NOT NULL DEFAULT TRUE,
    preferred_locale           VARCHAR(5)   NOT NULL DEFAULT 'ar',
    last_login_at              DATETIME(6)  NULL,
    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    CONSTRAINT fk_app_user_region FOREIGN KEY (regional_directorate_id) REFERENCES regional_directorate (id),
    CONSTRAINT fk_app_user_province FOREIGN KEY (provincial_directorate_id) REFERENCES provincial_directorate (id),
    CONSTRAINT fk_app_user_center FOREIGN KEY (center_id) REFERENCES center (id),
    CONSTRAINT ck_app_user_role CHECK (role IN
        ('ADMIN', 'REGIONAL', 'PROVINCIAL', 'CENTER_MANAGER', 'HR_OFFICER', 'READ_ONLY')),
    CONSTRAINT ck_app_user_locale CHECK (preferred_locale IN ('ar', 'fr')),
    -- cohérence profil / portée
    CONSTRAINT ck_app_user_scope CHECK (
        (role = 'ADMIN' AND regional_directorate_id IS NULL AND provincial_directorate_id IS NULL AND center_id IS NULL)
        OR (role = 'REGIONAL' AND regional_directorate_id IS NOT NULL AND provincial_directorate_id IS NULL AND center_id IS NULL)
        OR (role = 'PROVINCIAL' AND provincial_directorate_id IS NOT NULL AND center_id IS NULL)
        OR (role = 'CENTER_MANAGER' AND center_id IS NOT NULL)
        OR role IN ('HR_OFFICER', 'READ_ONLY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Jetons de rafraîchissement (cookie httpOnly) : seul le hash SHA-256 est stocké.
CREATE TABLE refresh_token (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    issued_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at     DATETIME(6)  NOT NULL,
    revoked_at     DATETIME(6)  NULL,
    replaced_by_id BIGINT       NULL,                 -- rotation : jeton qui a pris la suite
    user_agent     VARCHAR(255) NULL,
    ip_address     VARCHAR(45)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id),
    KEY idx_refresh_token_expiry (expires_at),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Journal d'audit : création / modification / suppression (bénéficiaires, employés, ...)
CREATE TABLE audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    entity_type  VARCHAR(60)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(10)  NOT NULL,
    user_id      BIGINT       NULL,
    username     VARCHAR(100) NOT NULL,
    occurred_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    changes      JSON         NULL,                    -- {"champ": {"old": ..., "new": ...}}
    ip_address   VARCHAR(45)  NULL,
    PRIMARY KEY (id),
    KEY idx_audit_entity (entity_type, entity_id, occurred_at),
    KEY idx_audit_user (username, occurred_at),
    KEY idx_audit_time (occurred_at),
    CONSTRAINT ck_audit_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
