-- ============================================================================
-- V5 : Ressources humaines (الموارد البشرية)
--
-- employee_type          : الصفة (cadre pédagogique, directeur, administratif,
--                          formateur invité, coordonnateur pédagogique)
-- administrative_status  : الوضعية الإدارية (Entraide Nationale, association,
--                          autre secteur, Promotion nationale, volontaire étranger)
-- Les compteurs « عدد ... » de la fiche centre sont CALCULÉS (GROUP BY), pas saisis.
-- extra_data (JSON) : champs administratifs supplémentaires à ajouter plus tard.
-- ============================================================================

CREATE TABLE employee (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    provincial_directorate_id  BIGINT       NOT NULL,           -- fixé depuis le compte provincial
    center_id                  BIGINT       NULL,               -- NULL = cadre de la direction provinciale

    employee_type              VARCHAR(30)  NOT NULL,
    administrative_status      VARCHAR(30)  NOT NULL,
    other_sector_text          VARCHAR(255) NULL,
    delegating_body            VARCHAR(255) NULL,               -- الجهة المنتدبة للمتطوع

    full_name_ar               VARCHAR(200) NOT NULL,
    full_name_fr               VARCHAR(200) NULL,
    gender                     CHAR(1)      NOT NULL,
    birth_date                 DATE         NULL,
    birth_place                VARCHAR(150) NULL,
    id_document_type           VARCHAR(30)  NULL,
    id_document_number         VARCHAR(50)  NULL,
    address                    VARCHAR(300) NULL,
    phone                      VARCHAR(30)  NULL,
    email                      VARCHAR(200) NULL,

    education_level            VARCHAR(40)  NULL,
    diploma_title              VARCHAR(200) NULL,               -- الشهادة المحصل عليها
    specialty                  VARCHAR(200) NULL,               -- التخصص
    diploma_date               DATE         NULL,
    experience_description     VARCHAR(500) NULL,               -- التجربة العملية في مجال التربية والتكوين
    other_qualifications       TEXT         NULL,

    -- معطيات حول مستخدمين التعاون الوطني
    en_grade                   VARCHAR(100) NULL,               -- الإطار
    en_scale                   VARCHAR(20)  NULL,               -- السلم
    rank_number                VARCHAR(30)  NULL,               -- الرقم الترتيبي
    hire_date                  DATE         NULL,
    activity_status            VARCHAR(15)  NOT NULL DEFAULT 'ACTIVE',

    extra_data                 JSON         NULL,

    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by                 VARCHAR(100) NULL,
    updated_by                 VARCHAR(100) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_rank_number (rank_number),
    KEY idx_employee_province (provincial_directorate_id),
    KEY idx_employee_center (center_id),
    KEY idx_employee_name_ar (full_name_ar),
    CONSTRAINT fk_employee_province FOREIGN KEY (provincial_directorate_id) REFERENCES provincial_directorate (id),
    -- le centre doit appartenir à la direction provinciale de l'employé
    CONSTRAINT fk_employee_center FOREIGN KEY (center_id, provincial_directorate_id)
        REFERENCES center (id, provincial_directorate_id),
    CONSTRAINT ck_employee_type CHECK (employee_type IN
        ('PEDAGOGICAL', 'CENTER_DIRECTOR', 'ADMINISTRATIVE', 'GUEST_TRAINER', 'PEDAGOGICAL_COORDINATOR')),
    CONSTRAINT ck_employee_admin_status CHECK (administrative_status IN
        ('ENTRAIDE_NATIONALE', 'ASSOCIATION', 'OTHER_SECTOR', 'PROMOTION_NATIONALE', 'FOREIGN_VOLUNTEER')),
    CONSTRAINT ck_employee_other_sector CHECK (administrative_status <> 'OTHER_SECTOR'
        OR (other_sector_text IS NOT NULL AND other_sector_text <> '')),
    CONSTRAINT ck_employee_delegating_body CHECK (administrative_status <> 'FOREIGN_VOLUNTEER'
        OR (delegating_body IS NOT NULL AND delegating_body <> '')),
    CONSTRAINT ck_employee_en_rank CHECK (administrative_status <> 'ENTRAIDE_NATIONALE' OR rank_number IS NOT NULL),
    CONSTRAINT ck_employee_gender CHECK (gender IN ('M', 'F')),
    CONSTRAINT ck_employee_activity CHECK (activity_status IN ('ACTIVE', 'ON_LEAVE', 'SECONDED', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT ck_employee_coordinator_scope CHECK (employee_type <> 'PEDAGOGICAL_COORDINATOR' OR center_id IS NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- المكونون المستضافون : conditions de la prestation (par saison et filière)
CREATE TABLE employee_guest_contract (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id           BIGINT        NOT NULL,
    season_id             BIGINT        NOT NULL,
    filiere_id            BIGINT        NOT NULL,               -- الشعبة الملقنة
    diploma_year          SMALLINT      NULL,                   -- سنة الحصول على الدبلوم / الشهادة
    work_start_date       DATE          NULL,                   -- تاريخ بداية العمل
    hourly_rate_mad       DECIMAL(8,2)  NULL,                   -- التعويض عن كل ساعة (درهم)
    months_per_year       TINYINT UNSIGNED NULL,                -- عدد الأشهر في السنة
    hours_per_month       DECIMAL(6,2)  NULL,                   -- مجموع الساعات في الشهر
    beneficiaries_count   INT           NULL,                   -- عدد المستفيدين لدى المستضاف
    PRIMARY KEY (id),
    UNIQUE KEY uk_guest_contract (employee_id, season_id, filiere_id),
    CONSTRAINT fk_guest_contract_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_guest_contract_season FOREIGN KEY (season_id) REFERENCES training_season (id),
    CONSTRAINT fk_guest_contract_filiere FOREIGN KEY (filiere_id) REFERENCES filiere (id),
    CONSTRAINT ck_guest_contract_values CHECK (
        (hourly_rate_mad IS NULL OR hourly_rate_mad >= 0)
        AND (months_per_year IS NULL OR months_per_year BETWEEN 1 AND 12)
        AND (hours_per_month IS NULL OR hours_per_month >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Historique des affectations
CREATE TABLE employee_assignment (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id                BIGINT       NOT NULL,
    from_date                  DATE         NOT NULL,
    to_date                    DATE         NULL,
    provincial_directorate_id  BIGINT       NOT NULL,
    center_id                  BIGINT       NULL,
    position_title             VARCHAR(150) NULL,
    decision_reference         VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_employee_assignment_employee (employee_id, from_date),
    CONSTRAINT fk_employee_assignment_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_employee_assignment_center FOREIGN KEY (center_id, provincial_directorate_id)
        REFERENCES center (id, provincial_directorate_id),
    CONSTRAINT fk_employee_assignment_province FOREIGN KEY (provincial_directorate_id)
        REFERENCES provincial_directorate (id),
    CONSTRAINT ck_employee_assignment_dates CHECK (to_date IS NULL OR to_date >= from_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- هيئة التكوين : filière / modules enseignés par saison
CREATE TABLE employee_teaching (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    employee_id  BIGINT NOT NULL,
    season_id    BIGINT NOT NULL,
    filiere_id   BIGINT NOT NULL,
    module_id    BIGINT NULL,                      -- NULL = toute la filière
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_teaching (employee_id, season_id, filiere_id, module_id),
    CONSTRAINT fk_employee_teaching_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_employee_teaching_season FOREIGN KEY (season_id) REFERENCES training_season (id),
    CONSTRAINT fk_employee_teaching_filiere FOREIGN KEY (filiere_id) REFERENCES filiere (id),
    CONSTRAINT fk_employee_teaching_module FOREIGN KEY (module_id) REFERENCES module (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
