-- ============================================================================
-- V2 : Programme / Filière (الشعبة) / Module / Saison de formation (الموسم التكويني)
-- ============================================================================

CREATE TABLE program (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    code     VARCHAR(30)  NOT NULL,
    name_ar  VARCHAR(200) NOT NULL,
    name_fr  VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_program_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- الموسم التكويني
CREATE TABLE training_season (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    label       VARCHAR(20) NOT NULL,                  -- ex. 2025-2026
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    is_current  BOOLEAN     NOT NULL DEFAULT FALSE,
    -- au plus une saison courante : NULL (non unique) ou 1 (unique)
    current_marker TINYINT GENERATED ALWAYS AS (IF(is_current, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_training_season_label (label),
    UNIQUE KEY uk_training_season_current (current_marker),
    CONSTRAINT ck_training_season_dates CHECK (end_date > start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE filiere (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    program_id       BIGINT       NOT NULL,
    code             VARCHAR(30)  NOT NULL,
    name_ar          VARCHAR(200) NOT NULL,
    name_fr          VARCHAR(200) NOT NULL,
    duration_months  SMALLINT     NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       VARCHAR(100) NULL,
    updated_by       VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_filiere_code (code),
    KEY idx_filiere_program (program_id),
    CONSTRAINT fk_filiere_program FOREIGN KEY (program_id) REFERENCES program (id),
    CONSTRAINT ck_filiere_duration CHECK (duration_months > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE module (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    filiere_id      BIGINT        NOT NULL,
    name_ar         VARCHAR(200)  NOT NULL,
    name_fr         VARCHAR(200)  NULL,
    coefficient     DECIMAL(4,2)  NOT NULL,
    duration_hours  SMALLINT      NOT NULL,
    position        SMALLINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_position (filiere_id, position),
    CONSTRAINT fk_module_filiere FOREIGN KEY (filiere_id) REFERENCES filiere (id),
    CONSTRAINT ck_module_coefficient CHECK (coefficient > 0),
    CONSTRAINT ck_module_duration CHECK (duration_hours > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Filières assignées aux centres
CREATE TABLE center_filiere (
    center_id    BIGINT      NOT NULL,
    filiere_id   BIGINT      NOT NULL,
    assigned_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (center_id, filiere_id),
    KEY idx_center_filiere_filiere (filiere_id),
    CONSTRAINT fk_center_filiere_center FOREIGN KEY (center_id) REFERENCES center (id),
    CONSTRAINT fk_center_filiere_filiere FOREIGN KEY (filiere_id) REFERENCES filiere (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO program (code, name_ar, name_fr)
VALUES ('CEF', 'مراكز التربية والتكوين', 'Centres d''Éducation et de Formation');
