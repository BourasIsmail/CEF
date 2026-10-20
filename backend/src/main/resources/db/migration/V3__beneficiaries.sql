-- ============================================================================
-- V3 : Bénéficiaires (المستفيدون)
--
-- Situation sociale (multi-sélection) :
--   * is_vulnerable                  شخص في وضعية هشاشة
--   * disability_type  (non NULL)    شخص في وضعية إعاقة
--   * migration_status (non NULL)    مهاجر(ة) أو أجنبي(ة) في وضعية هشاشة
--   * other_social_situation_text    وضعية اجتماعية أخرى
--
-- stat_category : catégorie statistique unique, calculée par la base selon la
-- règle de priorité (à confirmer) : MIGRANT > DISABLED > OTHER > REMAINING.
-- Les personnes en situation de précarité seule tombent dans REMAINING.
-- ============================================================================

CREATE TABLE beneficiary (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    center_id                   BIGINT       NOT NULL,
    rank_number                 INT          NOT NULL,           -- الرقم الترتيبي داخل المركز
    registration_number         VARCHAR(30)  NOT NULL,
    registration_date           DATE         NOT NULL,
    gender                      CHAR(1)      NOT NULL,           -- M / F
    full_name_ar                VARCHAR(200) NOT NULL,
    full_name_fr                VARCHAR(200) NULL,
    birth_date                  DATE         NOT NULL,           -- l'âge est calculé, jamais stocké
    birth_place                 VARCHAR(150) NULL,
    id_document_type            VARCHAR(30)  NULL,
    id_document_number          VARCHAR(50)  NULL,
    address                     VARCHAR(300) NULL,
    phone                       VARCHAR(30)  NULL,
    education_level             VARCHAR(40)  NULL,
    family_status               VARCHAR(10)  NULL,               -- الحالة العائلية
    family_status_other_text    VARCHAR(255) NULL,
    additional_info             TEXT         NULL,

    is_vulnerable               BOOLEAN      NOT NULL DEFAULT FALSE,
    disability_type             VARCHAR(20)  NULL,
    disability_other_text       VARCHAR(255) NULL,
    migration_status            VARCHAR(40)  NULL,
    migration_other_text        VARCHAR(255) NULL,
    origin_country              VARCHAR(100) NULL,
    other_social_situation_text VARCHAR(255) NULL,

    stat_category VARCHAR(10) GENERATED ALWAYS AS (
        CASE
            WHEN migration_status IS NOT NULL THEN 'MIGRANT'
            WHEN disability_type IS NOT NULL THEN 'DISABLED'
            WHEN other_social_situation_text IS NOT NULL THEN 'OTHER'
            ELSE 'REMAINING'
        END) STORED,

    created_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by                  VARCHAR(100) NULL,
    updated_by                  VARCHAR(100) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_beneficiary_rank (center_id, rank_number),
    UNIQUE KEY uk_beneficiary_registration (center_id, registration_number),
    KEY idx_beneficiary_name_ar (full_name_ar),
    KEY idx_beneficiary_name_fr (full_name_fr),
    KEY idx_beneficiary_id_doc (id_document_number),
    KEY idx_beneficiary_category (stat_category),
    CONSTRAINT fk_beneficiary_center FOREIGN KEY (center_id) REFERENCES center (id),
    CONSTRAINT ck_beneficiary_gender CHECK (gender IN ('M', 'F')),
    CONSTRAINT ck_beneficiary_id_doc_type CHECK (id_document_type IS NULL OR id_document_type IN
        ('NATIONAL_ID', 'PASSPORT', 'RESIDENCE_CARD', 'BIRTH_CERTIFICATE', 'OTHER')),
    CONSTRAINT ck_beneficiary_family_status CHECK (family_status IS NULL OR family_status IN
        ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'OTHER')),
    CONSTRAINT ck_beneficiary_family_other CHECK (family_status IS NULL OR family_status <> 'OTHER'
        OR (family_status_other_text IS NOT NULL AND family_status_other_text <> '')),
    CONSTRAINT ck_beneficiary_disability CHECK (disability_type IS NULL OR disability_type IN
        ('MOTOR', 'AUDITORY', 'VISUAL', 'INTELLECTUAL', 'OTHER')),
    CONSTRAINT ck_beneficiary_disability_other CHECK (disability_type IS NULL OR disability_type <> 'OTHER'
        OR (disability_other_text IS NOT NULL AND disability_other_text <> '')),
    CONSTRAINT ck_beneficiary_migration CHECK (migration_status IS NULL OR migration_status IN
        ('REGULAR', 'IRREGULAR', 'REFUGEE', 'ASYLUM_SEEKER', 'IRREGULAR_MIGRATION_SITUATION', 'OTHER')),
    CONSTRAINT ck_beneficiary_migration_other CHECK (migration_status IS NULL OR migration_status <> 'OTHER'
        OR (migration_other_text IS NOT NULL AND migration_other_text <> '')),
    CONSTRAINT ck_beneficiary_other_social CHECK (other_social_situation_text IS NULL
        OR other_social_situation_text <> '')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
