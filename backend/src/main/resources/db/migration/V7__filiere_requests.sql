-- ============================================================================
-- V7 : Demandes d'ouverture / modification de filière (طلبات الشعب)
--
-- Circuit : province (dépôt) -> région (validation) -> national (approbation)
--           -> création de la filière dans le catalogue ET affectation au centre.
--
--   DRAFT -> SUBMITTED -> REGIONAL_VALIDATED -> APPROVED
--                  \-> RETURNED (à corriger)  \-> REJECTED
--
-- Pièces : 8 documents obligatoires, PDF uniquement, 20 Mo maximum par fichier.
-- Les fichiers sont stockés hors base (volume) ; seule la référence est en base.
-- ============================================================================

CREATE TABLE filiere_request (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    request_number            VARCHAR(30)   NOT NULL,
    request_type              VARCHAR(10)   NOT NULL,          -- CREATE / MODIFY
    status                    VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    center_id                 BIGINT        NOT NULL,
    association_id            BIGINT        NULL,              -- الجمعية المعنية
    target_filiere_id         BIGINT        NULL,              -- filière existante (MODIFY)
    proposed_name_ar          VARCHAR(200)  NOT NULL,
    proposed_name_fr          VARCHAR(200)  NULL,
    duration_months           SMALLINT      NULL,
    expected_beneficiaries    INT           NULL,
    modification_summary      TEXT          NULL,

    created_by_user_id        BIGINT        NOT NULL,
    submitted_at              DATETIME(6)   NULL,

    regional_decision_by      BIGINT        NULL,
    regional_decision_at      DATETIME(6)   NULL,
    regional_comment          TEXT          NULL,
    national_decision_by      BIGINT        NULL,
    national_decision_at      DATETIME(6)   NULL,
    national_comment          TEXT          NULL,

    resulting_filiere_id      BIGINT        NULL,              -- filière créée à l'approbation

    created_at                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_filiere_request_number (request_number),
    KEY idx_filiere_request_status (status),
    KEY idx_filiere_request_center (center_id),
    CONSTRAINT fk_filiere_request_center FOREIGN KEY (center_id) REFERENCES center (id),
    CONSTRAINT fk_filiere_request_association FOREIGN KEY (association_id) REFERENCES association (id),
    CONSTRAINT fk_filiere_request_target FOREIGN KEY (target_filiere_id) REFERENCES filiere (id),
    CONSTRAINT fk_filiere_request_result FOREIGN KEY (resulting_filiere_id) REFERENCES filiere (id),
    CONSTRAINT fk_filiere_request_creator FOREIGN KEY (created_by_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_filiere_request_regional FOREIGN KEY (regional_decision_by) REFERENCES app_user (id),
    CONSTRAINT fk_filiere_request_national FOREIGN KEY (national_decision_by) REFERENCES app_user (id),
    CONSTRAINT ck_filiere_request_type CHECK (request_type IN ('CREATE', 'MODIFY')),
    CONSTRAINT ck_filiere_request_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'RETURNED', 'REGIONAL_VALIDATED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_filiere_request_modify CHECK (request_type <> 'MODIFY' OR target_filiere_id IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- الوثائق التي يجب تحميلها لطلب إحداث شعبة
CREATE TABLE request_document_type (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(40)  NOT NULL,
    name_ar    VARCHAR(500) NOT NULL,
    position   SMALLINT     NOT NULL,
    mandatory  BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_document_type_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE request_document (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    request_id          BIGINT       NOT NULL,
    document_type_id    BIGINT       NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    size_bytes          BIGINT       NOT NULL,
    storage_key         VARCHAR(255) NOT NULL,
    sha256              CHAR(64)     NULL,
    uploaded_by_user_id BIGINT       NOT NULL,
    uploaded_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_document (request_id, document_type_id),
    UNIQUE KEY uk_request_document_storage (storage_key),
    CONSTRAINT fk_request_document_request FOREIGN KEY (request_id) REFERENCES filiere_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_request_document_type FOREIGN KEY (document_type_id) REFERENCES request_document_type (id),
    CONSTRAINT fk_request_document_user FOREIGN KEY (uploaded_by_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_request_document_pdf CHECK (content_type = 'application/pdf'),
    CONSTRAINT ck_request_document_size CHECK (size_bytes > 0 AND size_bytes <= 20971520)   -- 20 Mo
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE request_status_history (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    request_id     BIGINT       NOT NULL,
    from_status    VARCHAR(20)  NULL,
    to_status      VARCHAR(20)  NOT NULL,
    actor_user_id  BIGINT       NOT NULL,
    comment        TEXT         NULL,
    occurred_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_request_status_history (request_id, occurred_at),
    CONSTRAINT fk_request_status_history_request FOREIGN KEY (request_id) REFERENCES filiere_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_request_status_history_user FOREIGN KEY (actor_user_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO request_document_type (code, name_ar, position) VALUES
    ('ASSOCIATION_PRESIDENT_REQUEST', 'طلب رئيس الجمعية المعنية موجه إلى السيد مدير التعاون الوطني', 1),
    ('ASSOCIATION_LEGAL_FILE',        'نسخة طبق الأصل من الملف القانوني للجمعية مصادق على كافة الوثائق المكونة له (القانون الأساسي، محضر الجمع العام محين، لائحة أعضاء المكتب المسير، وصل الإيداع النهائي...)', 2),
    ('ASSOCIATION_TECH_SHEET',        'بطاقة تقنية حول الجمعية', 3),
    ('CENTER_TECH_SHEET',             'بطاقة تقنية حول المركز تبين وضعيته العقارية والتسييرية', 4),
    ('PARTNERSHIP_AGREEMENT',         'نسخة من اتفاقية الشراكة أو الملحق التعديلي الذي يتضمن الشعب المراد إحداثها', 5),
    ('FEASIBILITY_STUDY',             'دراسة الجدوى وفق النموذج المعتمد من طرف التعاون الوطني مع التأشير على جميع الصفحات', 6),
    ('TRAINING_PROGRAM',              'البرنامج التكويني الخاص بالشعبة مرفق بالغلاف الزمني', 7),
    ('TRAINERS_QUALIFICATIONS',       'الوثائق التي تبين كفاءة ومؤهلات المكونين المقترحين', 8);
