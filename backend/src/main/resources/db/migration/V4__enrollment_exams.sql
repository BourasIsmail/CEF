-- ============================================================================
-- V4 : Inscription, cycle de vie, examens (session normale / rattrapage), diplôme
--
-- Statuts d'inscription :
--   ENROLLED  inscrit (reste inscrit tant que le rattrapage est possible)
--   DEPARTED  parti avant l'examen final (date + motif obligatoires)
--   GRADUATED diplômé
--   FAILED    recalé
--   ABSENT    absent à l'examen (compté à part, jamais parmi les recalés)
--
-- Moyenne générale = (moyenne pondérée des modules + note de l'examen final) / 2
-- Seuil de réussite par défaut : 10/20, modifiable à la saisie ; réussite
-- exceptionnelle possible (justification obligatoire).
-- Le rattrapage reprend TOUS les examens (modules + examen final).
-- ============================================================================

CREATE TABLE enrollment (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    beneficiary_id    BIGINT       NOT NULL,
    center_id         BIGINT       NOT NULL,
    filiere_id        BIGINT       NOT NULL,
    season_id         BIGINT       NOT NULL,
    start_date        DATE         NOT NULL,
    exam_date         DATE         NULL,                -- تاريخ الامتحان
    status            VARCHAR(10)  NOT NULL DEFAULT 'ENROLLED',
    departure_date    DATE         NULL,
    departure_reason  VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by        VARCHAR(100) NULL,
    updated_by        VARCHAR(100) NULL,
    PRIMARY KEY (id),
    -- un bénéficiaire n'a qu'une inscription par saison
    UNIQUE KEY uk_enrollment_beneficiary_season (beneficiary_id, season_id),
    -- index des agrégations statistiques
    KEY idx_enrollment_stats (season_id, filiere_id, center_id, status),
    KEY idx_enrollment_center (center_id),
    CONSTRAINT fk_enrollment_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiary (id),
    CONSTRAINT fk_enrollment_season FOREIGN KEY (season_id) REFERENCES training_season (id),
    -- la filière doit être assignée au centre
    CONSTRAINT fk_enrollment_center_filiere FOREIGN KEY (center_id, filiere_id)
        REFERENCES center_filiere (center_id, filiere_id),
    CONSTRAINT ck_enrollment_status CHECK (status IN ('ENROLLED', 'DEPARTED', 'GRADUATED', 'FAILED', 'ABSENT')),
    CONSTRAINT ck_enrollment_departure CHECK (
        (status = 'DEPARTED' AND departure_date IS NOT NULL)
        OR (status <> 'DEPARTED' AND departure_date IS NULL AND departure_reason IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Une session d'examen par inscription : NORMAL puis éventuellement MAKEUP (rattrapage)
CREATE TABLE exam_session (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    enrollment_id           BIGINT        NOT NULL,
    session_type            VARCHAR(10)   NOT NULL,
    exam_number             VARCHAR(30)   NULL,                       -- رقم الامتحان
    final_exam_status       VARCHAR(10)   NOT NULL DEFAULT 'NOT_TAKEN',
    final_exam_grade        DECIMAL(5,2)  NULL,                       -- نقطة الامتحان النهائي
    modules_average         DECIMAL(5,2)  NULL,                       -- معدل الوحدات (pondéré)
    overall_average         DECIMAL(5,2)  NULL,                       -- المعدل العام
    pass_threshold          DECIMAL(4,2)  NOT NULL DEFAULT 10.00,
    exceptional_pass        BOOLEAN       NOT NULL DEFAULT FALSE,     -- نجاح استثنائي
    exceptional_pass_reason VARCHAR(500)  NULL,
    exceptional_pass_by     VARCHAR(100)  NULL,
    passed                  BOOLEAN       NULL,
    mention                 VARCHAR(30)   NULL,                       -- الميزة
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by              VARCHAR(100)  NULL,
    updated_by              VARCHAR(100)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_session (enrollment_id, session_type),
    CONSTRAINT fk_exam_session_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollment (id),
    CONSTRAINT ck_exam_session_type CHECK (session_type IN ('NORMAL', 'MAKEUP')),
    CONSTRAINT ck_exam_session_final_status CHECK (final_exam_status IN ('TAKEN', 'ABSENT', 'NOT_TAKEN')),
    CONSTRAINT ck_exam_session_grades CHECK (
        (final_exam_grade IS NULL OR final_exam_grade BETWEEN 0 AND 20)
        AND (modules_average IS NULL OR modules_average BETWEEN 0 AND 20)
        AND (overall_average IS NULL OR overall_average BETWEEN 0 AND 20)
        AND pass_threshold BETWEEN 0 AND 20),
    CONSTRAINT ck_exam_session_exceptional CHECK (exceptional_pass = FALSE
        OR (exceptional_pass_reason IS NOT NULL AND exceptional_pass_reason <> ''))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE module_grade (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    exam_session_id  BIGINT        NOT NULL,
    module_id        BIGINT        NOT NULL,
    exam_status      VARCHAR(10)   NOT NULL DEFAULT 'TAKEN',          -- passé / غائب / non passé
    grade            DECIMAL(5,2)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_grade (exam_session_id, module_id),
    KEY idx_module_grade_module (module_id),
    CONSTRAINT fk_module_grade_session FOREIGN KEY (exam_session_id) REFERENCES exam_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_module_grade_module FOREIGN KEY (module_id) REFERENCES module (id),
    CONSTRAINT ck_module_grade_status CHECK (exam_status IN ('TAKEN', 'ABSENT', 'NOT_TAKEN')),
    CONSTRAINT ck_module_grade_value CHECK (grade IS NULL OR grade BETWEEN 0 AND 20)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE diploma (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    enrollment_id     BIGINT       NOT NULL,
    received_date     DATE         NULL,                -- تاريخ تسلم الدبلوم
    serial_number     VARCHAR(40)  NOT NULL,            -- الرقم التسلسلي للدبلوم
    additional_info   VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by        VARCHAR(100) NULL,
    updated_by        VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diploma_enrollment (enrollment_id),
    UNIQUE KEY uk_diploma_serial (serial_number),
    CONSTRAINT fk_diploma_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollment (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
