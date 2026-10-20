-- ============================================================================
-- V1 : Organisation (Région -> Province -> Commune -> Centre), équipements,
--      associations partenaires.
-- Conventions : InnoDB / utf8mb4, enums en VARCHAR + CHECK, FK nommées fk_*.
-- ============================================================================

CREATE TABLE regional_directorate (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name_ar     VARCHAR(200) NOT NULL,
    name_fr     VARCHAR(200) NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_regional_directorate_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE provincial_directorate (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    regional_directorate_id  BIGINT       NOT NULL,
    code                     VARCHAR(20)  NOT NULL,
    name_ar                  VARCHAR(200) NOT NULL,
    name_fr                  VARCHAR(200) NOT NULL,
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provincial_directorate_code (code),
    KEY idx_provincial_directorate_region (regional_directorate_id),
    CONSTRAINT fk_provincial_directorate_region
        FOREIGN KEY (regional_directorate_id) REFERENCES regional_directorate (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- الجماعة
CREATE TABLE commune (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    provincial_directorate_id  BIGINT       NOT NULL,
    name_ar                    VARCHAR(200) NOT NULL,
    name_fr                    VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commune_name (provincial_directorate_id, name_ar),
    -- permet la FK composite center -> commune (même province)
    UNIQUE KEY uk_commune_id_province (id, provincial_directorate_id),
    CONSTRAINT fk_commune_province
        FOREIGN KEY (provincial_directorate_id) REFERENCES provincial_directorate (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- المركز
CREATE TABLE center (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    provincial_directorate_id  BIGINT       NOT NULL,
    commune_id                 BIGINT       NOT NULL,
    code                       VARCHAR(30)  NOT NULL,
    name_ar                    VARCHAR(200) NOT NULL,
    name_fr                    VARCHAR(200) NULL,
    area_type                  VARCHAR(10)  NOT NULL,            -- المجال : قروي / حضري
    address                    VARCHAR(300) NULL,
    building_type              VARCHAR(20)  NULL,                -- نوع البناية
    floors_count               TINYINT UNSIGNED NULL,            -- obligatoire si building_type = OTHER
    land_status                VARCHAR(30)  NULL,                -- الوضعية العقارية
    land_owner_text            VARCHAR(255) NULL,                -- propriétaire / secteur à préciser
    management_mode            VARCHAR(40)  NULL,                -- وضعية تسيير المركز
    active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by                 VARCHAR(100) NULL,
    updated_by                 VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_center_code (code),
    -- permet les FK composites employee -> center (même province)
    UNIQUE KEY uk_center_id_province (id, provincial_directorate_id),
    KEY idx_center_province (provincial_directorate_id),
    CONSTRAINT fk_center_commune
        FOREIGN KEY (commune_id, provincial_directorate_id)
        REFERENCES commune (id, provincial_directorate_id),
    CONSTRAINT fk_center_province
        FOREIGN KEY (provincial_directorate_id) REFERENCES provincial_directorate (id),
    CONSTRAINT ck_center_area_type CHECK (area_type IN ('RURAL', 'URBAN')),
    CONSTRAINT ck_center_building_type CHECK (building_type IS NULL OR building_type IN
        ('ONE_FLOOR', 'TWO_FLOORS', 'THREE_FLOORS', 'OTHER')),
    CONSTRAINT ck_center_floors_count CHECK (building_type IS NULL OR building_type <> 'OTHER'
        OR (floors_count IS NOT NULL AND floors_count > 0)),
    CONSTRAINT ck_center_land_status CHECK (land_status IS NULL OR land_status IN
        ('OWNED_BY_EN', 'RENTED_BY_EN', 'PROVIDED_TO_EN', 'ASSOCIATION', 'OTHER_SECTOR')),
    CONSTRAINT ck_center_management_mode CHECK (management_mode IS NULL OR management_mode IN
        ('DIRECT_EN', 'ASSOCIATION_WITH_EN_DIRECTOR', 'ASSOCIATION_FULL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- مساحة الطوابق : une ligne par étage (1, 2, 3, ... ou plus si building_type = OTHER)
-- La surface totale de la bâtisse = SUM(area_m2), calculée à la lecture.
CREATE TABLE center_floor_area (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    center_id     BIGINT        NOT NULL,
    floor_number  TINYINT UNSIGNED NOT NULL,
    area_m2       DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_center_floor (center_id, floor_number),
    CONSTRAINT fk_center_floor_center FOREIGN KEY (center_id) REFERENCES center (id) ON DELETE CASCADE,
    CONSTRAINT ck_center_floor_area CHECK (area_m2 > 0 AND floor_number > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- المرافق المكونة لمراكز التربية والتكوين (référentiel + affectation)
CREATE TABLE space_type (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(40)  NOT NULL,
    name_ar                 VARCHAR(200) NOT NULL,
    name_fr                 VARCHAR(200) NOT NULL,
    position                SMALLINT     NOT NULL,
    requires_specification  BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_space_type_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE center_space (
    center_id      BIGINT       NOT NULL,
    space_type_id  BIGINT       NOT NULL,
    specification  VARCHAR(200) NULL,
    PRIMARY KEY (center_id, space_type_id),
    CONSTRAINT fk_center_space_center FOREIGN KEY (center_id) REFERENCES center (id) ON DELETE CASCADE,
    CONSTRAINT fk_center_space_type FOREIGN KEY (space_type_id) REFERENCES space_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- الشركاء : liste prédéfinie (valeurs provisoires, la liste définitive viendra plus tard)
CREATE TABLE partner_type (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    code      VARCHAR(40)  NOT NULL,
    name_ar   VARCHAR(200) NOT NULL,
    name_fr   VARCHAR(200) NOT NULL,
    position  SMALLINT     NOT NULL DEFAULT 0,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_type_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- الجمعية
CREATE TABLE association (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name_ar     VARCHAR(200) NOT NULL,
    name_fr     VARCHAR(200) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_association_name (name_ar)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- معطيات الجمعية الشريكة بالمركز (une ligne par association partenaire du centre)
CREATE TABLE center_association (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    center_id              BIGINT       NOT NULL,
    association_id         BIGINT       NOT NULL,
    agreement_subject      VARCHAR(300) NULL,       -- موضوع الاتفاقية
    agreement_date         DATE         NULL,       -- تاريخ الاتفاقية
    agreement_reference    VARCHAR(100) NULL,       -- مرجعها
    partnership_evaluation VARCHAR(10)  NULL,       -- تقييم الشراكة
    measure_taken          VARCHAR(20)  NULL,       -- التدابير المتخذة
    measure_other_text     VARCHAR(255) NULL,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by             VARCHAR(100) NULL,
    updated_by             VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_center_association_center (center_id),
    KEY idx_center_association_association (association_id),
    CONSTRAINT fk_center_association_center FOREIGN KEY (center_id) REFERENCES center (id) ON DELETE CASCADE,
    CONSTRAINT fk_center_association_association FOREIGN KEY (association_id) REFERENCES association (id),
    CONSTRAINT ck_center_association_eval CHECK (partnership_evaluation IS NULL OR partnership_evaluation IN
        ('GOOD', 'FAIR', 'AVERAGE', 'WEAK')),
    CONSTRAINT ck_center_association_measure CHECK (measure_taken IS NULL OR measure_taken IN
        ('EXPAND', 'CONTINUE', 'TERMINATE', 'OTHER')),
    CONSTRAINT ck_center_association_measure_text CHECK (measure_taken IS NULL OR measure_taken <> 'OTHER'
        OR (measure_other_text IS NOT NULL AND measure_other_text <> ''))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE center_association_partner (
    center_association_id  BIGINT NOT NULL,
    partner_type_id        BIGINT NOT NULL,
    PRIMARY KEY (center_association_id, partner_type_id),
    CONSTRAINT fk_cap_center_association FOREIGN KEY (center_association_id)
        REFERENCES center_association (id) ON DELETE CASCADE,
    CONSTRAINT fk_cap_partner_type FOREIGN KEY (partner_type_id) REFERENCES partner_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
-- Données de référence (non spécifiques au développement)
-- ============================================================================

-- Les 12 régions du Royaume (المديريات الجهوية)
INSERT INTO regional_directorate (code, name_ar, name_fr) VALUES
    ('01', 'المديرية الجهوية لطنجة-تطوان-الحسيمة',      'Direction régionale de Tanger-Tétouan-Al Hoceïma'),
    ('02', 'المديرية الجهوية للشرق',                    'Direction régionale de l''Oriental'),
    ('03', 'المديرية الجهوية لفاس-مكناس',               'Direction régionale de Fès-Meknès'),
    ('04', 'المديرية الجهوية للرباط-سلا-القنيطرة',      'Direction régionale de Rabat-Salé-Kénitra'),
    ('05', 'المديرية الجهوية لبني ملال-خنيفرة',         'Direction régionale de Béni Mellal-Khénifra'),
    ('06', 'المديرية الجهوية للدار البيضاء-سطات',       'Direction régionale de Casablanca-Settat'),
    ('07', 'المديرية الجهوية لمراكش-آسفي',              'Direction régionale de Marrakech-Safi'),
    ('08', 'المديرية الجهوية لدرعة-تافيلالت',           'Direction régionale de Drâa-Tafilalet'),
    ('09', 'المديرية الجهوية لسوس-ماسة',                'Direction régionale de Souss-Massa'),
    ('10', 'المديرية الجهوية لكلميم-واد نون',           'Direction régionale de Guelmim-Oued Noun'),
    ('11', 'المديرية الجهوية للعيون-الساقية الحمراء',   'Direction régionale de Laâyoune-Sakia El Hamra'),
    ('12', 'المديرية الجهوية للداخلة-وادي الذهب',       'Direction régionale de Dakhla-Oued Ed-Dahab');

INSERT INTO space_type (code, name_ar, name_fr, position, requires_specification) VALUES
    ('RECEPTION',        'فضاء الاستقبال والتوجيه',  'Espace d''accueil et d''orientation', 1,  FALSE),
    ('WAITING',          'فضاء الانتظار',             'Espace d''attente',                   2,  FALSE),
    ('ADMIN_1',          'فضاء إداري 1',              'Espace administratif 1',              3,  FALSE),
    ('ADMIN_2',          'فضاء إداري 2',              'Espace administratif 2',              4,  FALSE),
    ('TRAINING_1',       'فضاء التكوين 1',            'Espace de formation 1',               5,  FALSE),
    ('TRAINING_2',       'فضاء التكوين 2',            'Espace de formation 2',               6,  FALSE),
    ('TRAINING_3',       'فضاء التكوين 3',            'Espace de formation 3',               7,  FALSE),
    ('TRAINING_4',       'فضاء التكوين 4',            'Espace de formation 4',               8,  FALSE),
    ('TRAINING_5',       'فضاء التكوين 5',            'Espace de formation 5',               9,  FALSE),
    ('CHILDREN',         'فضاء الأطفال',              'Espace enfants',                      10, FALSE),
    ('SOCIAL_ASSISTANCE','فضاء المساعدة الاجتماعية',  'Espace d''aide sociale',              11, FALSE),
    ('SOCIAL_INCUBATOR', 'الحاضنة الاجتماعية',        'Incubateur social',                   12, FALSE),
    ('OTHER',            'فضاء آخر مع تحديده',        'Autre espace (à préciser)',           13, TRUE);

-- Valeurs provisoires : la liste définitive des partenaires sera fournie plus tard.
INSERT INTO partner_type (code, name_ar, name_fr, position) VALUES
    ('LOCAL',      'محلي',  'Local',      1),
    ('REGIONAL',   'جهوي',  'Régional',   2),
    ('PROVINCIAL', 'إقليمي', 'Provincial', 3);
