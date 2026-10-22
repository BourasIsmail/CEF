-- ============================================================================
-- V100 : Données de DÉVELOPPEMENT uniquement (profil « dev »).
-- Jamais chargé en production : voir spring.flyway.locations dans application-dev.yml.
-- Données fictives, pensées pour valider le rapport statistique :
--   20 bénéficiaires, 3 filières, 5 centres, toutes les catégories et tous les statuts.
-- Aucun mot de passe ni compte utilisateur n'est créé ici (voir le module sécurité).
-- ============================================================================

-- Saison
INSERT INTO training_season (label, start_date, end_date, is_current)
VALUES ('2025-2026', '2025-10-01', '2026-09-30', TRUE),
       ('2024-2025', '2024-10-01', '2025-09-30', FALSE);
SET @season = (SELECT id FROM training_season WHERE label = '2025-2026');

-- Directions provinciales : fournies par la migration V11 (Casablanca-Anfa = 0601, Mohammedia = 0602, Rabat = 0401).
SET @pd_cas = (SELECT id FROM provincial_directorate WHERE code = '0601');
SET @pd_moh = (SELECT id FROM provincial_directorate WHERE code = '0602');
SET @pd_rab = (SELECT id FROM provincial_directorate WHERE code = '0401');
-- Noms français des trois provinces de démonstration (V11 reprend le nom arabe)
UPDATE provincial_directorate SET name_fr = 'Casablanca-Anfa' WHERE id = @pd_cas;
UPDATE provincial_directorate SET name_fr = 'Mohammedia' WHERE id = @pd_moh;
UPDATE provincial_directorate SET name_fr = 'Rabat' WHERE id = @pd_rab;

-- Communes fictives rattachées aux provinces de démonstration (celles qui existent déjà dans V11 sont ignorées)
INSERT IGNORE INTO commune (provincial_directorate_id, name_ar, name_fr) VALUES
    (@pd_cas, 'سيدي مومن',   'Sidi Moumen'),
    (@pd_cas, 'عين الشق',    'Aïn Chock'),
    (@pd_cas, 'الحي الحسني', 'Hay Hassani'),
    (@pd_moh, 'المحمدية',    'Mohammedia'),
    (@pd_rab, 'الرباط',      'Rabat');

-- Centres
INSERT INTO center (provincial_directorate_id, commune_id, code, name_ar, name_fr, area_type, address,
                    building_type, floors_count, land_status, management_mode) VALUES
    (@pd_cas, (SELECT id FROM commune WHERE name_ar = 'سيدي مومن'   AND provincial_directorate_id = @pd_cas), 'CAS-01', 'مركز سيدي مومن',   'Centre Sidi Moumen',   'URBAN', 'شارع الأمل، سيدي مومن',      'TWO_FLOORS',   NULL, 'OWNED_BY_EN',   'ASSOCIATION_WITH_EN_DIRECTOR'),
    (@pd_cas, (SELECT id FROM commune WHERE name_ar = 'عين الشق'    AND provincial_directorate_id = @pd_cas), 'CAS-02', 'مركز عين الشق',    'Centre Aïn Chock',     'URBAN', 'حي عين الشق',                'ONE_FLOOR',    NULL, 'OWNED_BY_EN',   'DIRECT_EN'),
    (@pd_cas, (SELECT id FROM commune WHERE name_ar = 'الحي الحسني' AND provincial_directorate_id = @pd_cas), 'CAS-03', 'مركز الحي الحسني', 'Centre Hay Hassani',   'URBAN', 'الحي الحسني',                'OTHER',        4,    'ASSOCIATION',   'ASSOCIATION_FULL'),
    (@pd_moh, (SELECT id FROM commune WHERE name_ar = 'المحمدية'    AND provincial_directorate_id = @pd_moh), 'MOH-01', 'مركز المحمدية',    'Centre Mohammedia',    'URBAN', 'وسط المحمدية',               'THREE_FLOORS', NULL, 'RENTED_BY_EN',  'ASSOCIATION_WITH_EN_DIRECTOR'),
    (@pd_rab, (SELECT id FROM commune WHERE name_ar = 'الرباط'      AND provincial_directorate_id = @pd_rab), 'RAB-01', 'مركز الرباط أكدال', 'Centre Rabat Agdal',   'URBAN', 'أكدال، الرباط',              'TWO_FLOORS',   NULL, 'PROVIDED_TO_EN','DIRECT_EN');
SET @c1 = (SELECT id FROM center WHERE code = 'CAS-01');
SET @c2 = (SELECT id FROM center WHERE code = 'CAS-02');
SET @c3 = (SELECT id FROM center WHERE code = 'CAS-03');
SET @c4 = (SELECT id FROM center WHERE code = 'MOH-01');
SET @c5 = (SELECT id FROM center WHERE code = 'RAB-01');
UPDATE center SET land_provider_id = (SELECT id FROM land_provider WHERE code = 'L06') WHERE code = 'RAB-01';   -- أملاك الدولة

INSERT INTO center_floor_area (center_id, floor_number, area_m2) VALUES
    (@c1, 1, 220), (@c1, 2, 180),
    (@c3, 1, 220), (@c3, 2, 180), (@c3, 3, 150), (@c3, 4, 90);

INSERT INTO center_space (center_id, space_type_id)
SELECT @c1, id FROM space_type
WHERE code IN ('RECEPTION', 'WAITING', 'ADMIN_1', 'TRAINING_1', 'TRAINING_2', 'TRAINING_3', 'CHILDREN', 'SOCIAL_ASSISTANCE');

INSERT INTO association (name_ar, name_fr) VALUES
    ('جمعية الأمل للتنمية', 'Association Al Amal pour le développement'),
    ('جمعية النور للتنمية', 'Association An Nour pour le développement');

INSERT INTO center_association (center_id, association_id, agreement_subject, agreement_date, agreement_reference,
                                partnership_evaluation, measure_taken) VALUES
    (@c1, (SELECT id FROM association WHERE name_ar = 'جمعية الأمل للتنمية'), 'تسيير مركز التربية والتكوين', '2022-09-01', 'اتفاقية رقم 2022/37', 'GOOD', 'CONTINUE'),
    (@c3, (SELECT id FROM association WHERE name_ar = 'جمعية النور للتنمية'), 'تسيير مركز التربية والتكوين', '2023-01-15', 'اتفاقية رقم 2023/05', 'FAIR', 'EXPAND');
INSERT INTO center_association_partner (center_association_id, partner_type_id)
SELECT ca.id, pt.id FROM center_association ca JOIN partner_type pt ON pt.code IN ('P08', 'P21')
WHERE ca.center_id = @c1;

-- Filières et modules
INSERT INTO filiere (program_id, code, name_ar, name_fr, duration_months) VALUES
    ((SELECT id FROM program WHERE code = 'CEF'), 'SEW',  'خياطة وتفصيل عصري', 'Couture et mode moderne',  12),
    ((SELECT id FROM program WHERE code = 'CEF'), 'HAIR', 'الحلاقة والتجميل',   'Coiffure et esthétique',   12),
    ((SELECT id FROM program WHERE code = 'CEF'), 'PAST', 'الحلويات',            'Pâtisserie',                9);
SET @f_sew  = (SELECT id FROM filiere WHERE code = 'SEW');
SET @f_hair = (SELECT id FROM filiere WHERE code = 'HAIR');
SET @f_past = (SELECT id FROM filiere WHERE code = 'PAST');

INSERT INTO module (filiere_id, name_ar, coefficient, duration_hours, position) VALUES
    (@f_sew, 'تقنيات الخياطة الأساسية', 3, 120, 1),
    (@f_sew, 'التفصيل والقص',            3, 100, 2),
    (@f_sew, 'الخياطة العصرية',          4, 140, 3),
    (@f_sew, 'الاتصال والتواصل',         1,  40, 4),
    (@f_sew, 'مبادئ التدبير المقاولاتي', 2,  60, 5),
    (@f_sew, 'السلامة والصحة المهنية',   1,  30, 6),
    (@f_hair, 'تقنيات الحلاقة',          4, 140, 1),
    (@f_hair, 'الصباغة والعناية بالشعر', 3, 110, 2),
    (@f_hair, 'التجميل والعناية بالبشرة',3, 100, 3),
    (@f_hair, 'الاتصال والتواصل',        1,  40, 4),
    (@f_hair, 'السلامة والصحة المهنية',  1,  30, 5),
    (@f_past, 'تقنيات العجين',           3, 100, 1),
    (@f_past, 'الحلويات المغربية',       4, 120, 2),
    (@f_past, 'الحلويات العصرية',        3, 100, 3),
    (@f_past, 'الاتصال والتواصل',        1,  40, 4),
    (@f_past, 'النظافة والسلامة الغذائية',2, 50, 5);

INSERT INTO center_filiere (center_id, filiere_id) VALUES
    (@c1, @f_sew), (@c1, @f_hair), (@c1, @f_past),
    (@c2, @f_sew), (@c2, @f_hair),
    (@c3, @f_hair), (@c3, @f_past),
    (@c4, @f_sew), (@c4, @f_past),
    (@c5, @f_sew), (@c5, @f_hair);

-- ----------------------------------------------------------------------------
-- Bénéficiaires (id explicite = id d'inscription, pour lisibilité des jeux d'essai)
-- Catégorie statistique attendue (règle de priorité) :
--   MIGRANT : 5, 6, 11, 18   DISABLED : 4, 10, 17   OTHER : 7, 14, 20   REMAINING : les autres
-- ----------------------------------------------------------------------------
INSERT INTO beneficiary (id, center_id, rank_number, registration_number, registration_date, gender,
                         full_name_ar, full_name_fr, birth_date, id_document_type, id_document_number,
                         education_level, family_status, is_vulnerable, disability_type, migration_status,
                         origin_country, other_social_situation_text) VALUES
 (1,  @c1, 1, '2025-0001', '2025-10-06', 'F', 'فاطمة الزهراء بنعلي', 'Fatima Zahra Benali',      '2001-04-12', 'NATIONAL_ID',    'BK100001', 'UPPER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     NULL),
 (2,  @c1, 2, '2025-0002', '2025-10-06', 'F', 'خديجة أوعلي',          'Khadija Ouali',            '1994-09-03', 'NATIONAL_ID',    'BK100002', 'LOWER_SECONDARY', 'MARRIED',  FALSE, NULL,           NULL,       NULL,     NULL),
 (3,  @c1, 3, '2025-0003', '2025-10-07', 'F', 'حفصة بنسعيد',          'Hafsa Bensaid',            '2001-06-21', 'NATIONAL_ID',    'BK100003', 'LOWER_SECONDARY', 'SINGLE',   TRUE,  NULL,           NULL,       NULL,     NULL),
 (4,  @c1, 4, '2025-0004', '2025-10-07', 'M', 'يوسف الإدريسي',        'Youssef El Idrissi',       '2006-01-15', 'NATIONAL_ID',    'BK100004', 'LOWER_SECONDARY', 'SINGLE',   FALSE, 'MOTOR',        NULL,       NULL,     NULL),
 (5,  @c2, 1, '2025-0101', '2025-10-08', 'F', 'آوا تراوري',           'Awa Traoré',               '1998-03-14', 'RESIDENCE_CARD', 'AB123456', 'LOWER_SECONDARY', 'MARRIED',  TRUE,  NULL,           'REFUGEE',  'مالي',   NULL),
 (6,  @c2, 2, '2025-0102', '2025-10-08', 'F', 'مريم كامارا',          'Mariam Camara',            '1999-11-30', 'PASSPORT',       'P7654321', 'PRIMARY',         'SINGLE',   FALSE, 'VISUAL',       'IRREGULAR','غينيا',  NULL),
 (7,  @c4, 1, '2025-0201', '2025-10-09', 'F', 'سعاد المنصوري',        'Souad El Mansouri',        '1990-02-18', 'NATIONAL_ID',    'BE100007', 'PRIMARY',         'WIDOWED',  FALSE, NULL,           NULL,       NULL,     'أرملة معيلة'),
 (8,  @c4, 2, '2025-0202', '2025-10-09', 'M', 'محمد أمين الحداد',     'Mohamed Amine El Haddad',  '2004-08-09', 'NATIONAL_ID',    'BE100008', 'LOWER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     NULL),
 (9,  @c1, 5, '2025-0005', '2025-10-10', 'F', 'نادية العلوي',         'Nadia El Alaoui',          '1997-12-01', 'NATIONAL_ID',    'BK100009', 'UPPER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     NULL),
 (10, @c2, 3, '2025-0103', '2025-10-10', 'F', 'سلمى الودغيري',        'Salma El Ouadghiri',       '2002-05-27', 'NATIONAL_ID',    'BK100010', 'LOWER_SECONDARY', 'SINGLE',   FALSE, 'AUDITORY',     NULL,       NULL,     NULL),
 (11, @c2, 4, '2025-0104', '2025-10-10', 'F', 'فاتو جالو',            'Fatou Diallo',             '1996-07-19', 'PASSPORT',       'P1122334', 'PRIMARY',         'MARRIED',  FALSE, NULL,           'REGULAR',  'السنغال',NULL),
 (12, @c3, 1, '2025-0301', '2025-10-11', 'F', 'ليلى بوعزة',           'Leila Bouazza',            '1993-10-10', 'NATIONAL_ID',    'BK100012', 'LOWER_SECONDARY', 'MARRIED',  FALSE, NULL,           NULL,       NULL,     NULL),
 (13, @c3, 2, '2025-0302', '2025-10-11', 'M', 'عبد الإله الصقلي',     'Abdelilah Skalli',         '2005-03-03', 'NATIONAL_ID',    'BK100013', 'LOWER_SECONDARY', 'SINGLE',   TRUE,  NULL,           NULL,       NULL,     NULL),
 (14, @c5, 1, '2025-0401', '2025-10-12', 'F', 'ثريا الكتاني',         'Soraya El Kettani',        '1992-01-25', 'NATIONAL_ID',    'AB100014', 'UPPER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     'أم عازبة'),
 (15, @c1, 6, '2025-0006', '2025-10-12', 'M', 'إدريس المرابط',        'Driss El Mrabet',          '2003-09-14', 'NATIONAL_ID',    'BK100015', 'UPPER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     NULL),
 (16, @c3, 3, '2025-0303', '2025-10-13', 'F', 'أمينة بوخبزة',         'Amina Boukhbza',           '2000-02-02', 'NATIONAL_ID',    'BK100016', 'LOWER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     NULL),
 (17, @c3, 4, '2025-0304', '2025-10-13', 'F', 'هاجر الزيدي',          'Hajar Zidi',               '2001-11-11', 'NATIONAL_ID',    'BK100017', 'PRIMARY',         'SINGLE',   FALSE, 'INTELLECTUAL', NULL,       NULL,     NULL),
 (18, @c4, 3, '2025-0203', '2025-10-13', 'M', 'إبراهيم سيسي',         'Ibrahim Cissé',            '1999-04-04', 'PASSPORT',       'P9988776', 'LOWER_SECONDARY', 'SINGLE',   FALSE, NULL,           'IRREGULAR','غينيا',  NULL),
 (19, @c4, 4, '2025-0204', '2025-10-14', 'F', 'كوثر بنموسى',          'Kawtar Benmoussa',         '2000-06-06', 'NATIONAL_ID',    'BE100019', 'UPPER_SECONDARY', 'SINGLE',   FALSE, NULL,           NULL,       NULL,     NULL),
 (20, @c4, 5, '2025-0205', '2025-10-14', 'M', 'ياسين الفاسي',         'Yassine El Fassi',         '2007-12-12', 'NATIONAL_ID',    'BE100020', 'LOWER_SECONDARY', 'SINGLE',   TRUE,  NULL,           NULL,       NULL,     'يتيم');

-- Inscriptions (cycle de vie)
INSERT INTO enrollment (id, beneficiary_id, center_id, filiere_id, season_id, start_date, exam_date, status,
                        departure_date, departure_reason) VALUES
 (1,  1,  @c1, @f_sew,  @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (2,  2,  @c1, @f_sew,  @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (3,  3,  @c1, @f_sew,  @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (4,  4,  @c1, @f_sew,  @season, '2025-10-13', '2026-09-15', 'FAILED',    NULL, NULL),
 (5,  5,  @c2, @f_sew,  @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (6,  6,  @c2, @f_sew,  @season, '2025-10-13', NULL,         'DEPARTED',  '2026-01-20', 'انتقال إلى مدينة أخرى'),
 (7,  7,  @c4, @f_sew,  @season, '2025-10-13', '2026-09-15', 'ABSENT',    NULL, NULL),
 (8,  8,  @c4, @f_sew,  @season, '2025-10-13', NULL,         'ENROLLED',  NULL, NULL),
 (9,  9,  @c1, @f_hair, @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (10, 10, @c2, @f_hair, @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (11, 11, @c2, @f_hair, @season, '2025-10-13', '2026-09-15', 'FAILED',    NULL, NULL),
 (12, 12, @c3, @f_hair, @season, '2025-10-13', NULL,         'DEPARTED',  '2026-03-02', 'ظروف عائلية'),
 (13, 13, @c3, @f_hair, @season, '2025-10-13', NULL,         'ENROLLED',  NULL, NULL),
 (14, 14, @c5, @f_hair, @season, '2025-10-13', '2026-09-15', 'GRADUATED', NULL, NULL),
 (15, 15, @c1, @f_past, @season, '2025-10-13', '2026-06-30', 'GRADUATED', NULL, NULL),
 (16, 16, @c3, @f_past, @season, '2025-10-13', '2026-06-30', 'GRADUATED', NULL, NULL),
 (17, 17, @c3, @f_past, @season, '2025-10-13', '2026-06-30', 'FAILED',    NULL, NULL),
 (18, 18, @c4, @f_past, @season, '2025-10-13', '2026-06-30', 'ABSENT',    NULL, NULL),
 (19, 19, @c4, @f_past, @season, '2025-10-13', '2026-06-30', 'GRADUATED', NULL, NULL),
 (20, 20, @c4, @f_past, @season, '2025-10-13', NULL,         'DEPARTED',  '2026-02-11', 'التحاق بعمل');

-- Sessions d'examen. Moyenne générale = (moyenne modules + note finale) / 2.
INSERT INTO exam_session (enrollment_id, session_type, exam_number, final_exam_status, final_exam_grade,
                          modules_average, overall_average, passed, mention) VALUES
 (1,  'NORMAL', 'EX-2026-0001', 'TAKEN',  13.00, 14.20, 13.60, TRUE,  'مستحسن'),
 (2,  'NORMAL', 'EX-2026-0002', 'TAKEN',  12.00, 11.50, 11.75, TRUE,  'مقبول'),
 (3,  'NORMAL', 'EX-2026-0003', 'TAKEN',   8.50, 10.29,  9.39, FALSE, NULL),
 (3,  'MAKEUP', 'EX-2026-0003', 'TAKEN',  11.00, 11.79, 11.39, TRUE,  'مقبول'),
 (4,  'NORMAL', 'EX-2026-0004', 'TAKEN',   7.00,  8.00,  7.50, FALSE, NULL),
 (4,  'MAKEUP', 'EX-2026-0004', 'TAKEN',   8.50,  9.00,  8.75, FALSE, NULL),
 (5,  'NORMAL', 'EX-2026-0005', 'TAKEN',  13.00, 12.50, 12.75, TRUE,  'مستحسن'),
 (7,  'NORMAL', 'EX-2026-0007', 'ABSENT', NULL,  NULL,  NULL,  NULL,  NULL),
 (9,  'NORMAL', 'EX-2026-0009', 'TAKEN',  14.00, 15.00, 14.50, TRUE,  'جيد'),
 (10, 'NORMAL', 'EX-2026-0010', 'TAKEN',  10.50, 11.00, 10.75, TRUE,  'مقبول'),
 (11, 'NORMAL', 'EX-2026-0011', 'TAKEN',   9.00,  8.00,  8.50, FALSE, NULL),
 (11, 'MAKEUP', 'EX-2026-0011', 'TAKEN',   9.00,  9.00,  9.00, FALSE, NULL),
 (14, 'NORMAL', 'EX-2026-0014', 'TAKEN',  12.00, 12.00, 12.00, TRUE,  'مستحسن'),
 (15, 'NORMAL', 'EX-2026-0015', 'TAKEN',  11.00, 10.50, 10.75, TRUE,  'مقبول'),
 (16, 'NORMAL', 'EX-2026-0016', 'TAKEN',  12.00, 13.00, 12.50, TRUE,  'مستحسن'),
 (17, 'NORMAL', 'EX-2026-0017', 'TAKEN',   8.00,  7.50,  7.75, FALSE, NULL),
 (17, 'MAKEUP', 'EX-2026-0017', 'TAKEN',   8.00,  8.00,  8.00, FALSE, NULL),
 (18, 'NORMAL', 'EX-2026-0018', 'ABSENT', NULL,  NULL,  NULL,  NULL,  NULL),
 (19, 'NORMAL', 'EX-2026-0019', 'TAKEN',  15.00, 16.00, 15.50, TRUE,  'جيد');

-- Notes par module du bénéficiaire 3 (échoue en session normale, réussit au rattrapage)
INSERT INTO module_grade (exam_session_id, module_id, exam_status, grade)
SELECT es.id, m.id, 'TAKEN', ELT(m.position, 12, 9, 8, 14, 11, 13)
FROM exam_session es JOIN module m ON m.filiere_id = @f_sew
WHERE es.enrollment_id = 3 AND es.session_type = 'NORMAL';
INSERT INTO module_grade (exam_session_id, module_id, exam_status, grade)
SELECT es.id, m.id, 'TAKEN', ELT(m.position, 13, 11, 10, 15, 12, 14)
FROM exam_session es JOIN module m ON m.filiere_id = @f_sew
WHERE es.enrollment_id = 3 AND es.session_type = 'MAKEUP';

-- Diplômes (les deux derniers pas encore remis)
INSERT INTO diploma (enrollment_id, received_date, serial_number) VALUES
 (1,  '2026-10-05', 'CEF-2026-000001'), (2,  '2026-10-05', 'CEF-2026-000002'),
 (3,  '2026-10-05', 'CEF-2026-000003'), (5,  '2026-10-05', 'CEF-2026-000004'),
 (9,  '2026-10-06', 'CEF-2026-000005'), (10, '2026-10-06', 'CEF-2026-000006'),
 (14, '2026-10-06', 'CEF-2026-000007'), (15, '2026-07-20', 'CEF-2026-000008'),
 (16, NULL,         'CEF-2026-000009'), (19, NULL,         'CEF-2026-000010');

-- Ressources humaines
INSERT INTO employee (id, provincial_directorate_id, center_id, employee_type, administrative_status, full_name_ar,
                      full_name_fr, gender, birth_date, birth_place, id_document_type, id_document_number,
                      education_level, diploma_title, specialty, diploma_date, en_grade, en_scale, rank_number,
                      hire_date, activity_status) VALUES
 (1, @pd_cas, @c1,  'PEDAGOGICAL',             'ENTRAIDE_NATIONALE', 'نادية الشرقاوي',  'Nadia Charkaoui',     'F', '1985-07-22', 'الدار البيضاء', 'NATIONAL_ID', 'BK120001', 'UNIVERSITY', 'دبلوم تقني متخصص', 'الخياطة والتفصيل', '2008-07-01', 'مكوّن', 'السلم 9', 'M-10482', '2012-03-01', 'ACTIVE'),
 (2, @pd_cas, @c2,  'CENTER_DIRECTOR',         'ENTRAIDE_NATIONALE', 'كريم بنجلون',     'Karim Benjelloun',    'M', '1980-03-11', 'الدار البيضاء', 'NATIONAL_ID', 'BK120002', 'UNIVERSITY', 'إجازة',            'التدبير',          '2003-07-01', 'متصرف', 'السلم 10','M-10517', '2009-09-15', 'ACTIVE'),
 (3, @pd_cas, @c1,  'PEDAGOGICAL',             'ASSOCIATION',        'حفصة الودغيري',   'Hafsa El Ouadghiri',  'F', '1990-11-05', 'المحمدية',      'NATIONAL_ID', 'BK120003', 'UNIVERSITY', 'دبلوم مهني',       'الحلويات',         '2013-07-01', NULL,    NULL,      NULL,      '2016-10-03', 'ACTIVE'),
 (4, @pd_cas, NULL, 'PEDAGOGICAL_COORDINATOR', 'ENTRAIDE_NATIONALE', 'ثريا الكتاني',    'Soraya El Kettani',   'F', '1983-01-30', 'فاس',           'NATIONAL_ID', 'BK120004', 'UNIVERSITY', 'ماستر',            'علوم التربية',     '2007-07-01', 'منسق',  'السلم 10','M-12755', '2015-11-02', 'ACTIVE'),
 (5, @pd_cas, @c3,  'GUEST_TRAINER',           'ASSOCIATION',        'رشيد الأزرق',     'Rachid El Azraq',     'M', '1988-05-17', 'الدار البيضاء', 'NATIONAL_ID', 'BK120005', 'VOCATIONAL', 'دبلوم مهني',       'الحلاقة',          '2015-07-01', NULL,    NULL,      NULL,      NULL,         'ACTIVE');

INSERT INTO employee_guest_contract (employee_id, season_id, filiere_id, diploma_year, work_start_date,
                                     hourly_rate_mad, months_per_year, hours_per_month, beneficiaries_count)
VALUES (5, @season, @f_hair, 2015, '2025-10-13', 120.00, 9, 40.00, 12);

INSERT INTO employee_assignment (employee_id, from_date, to_date, provincial_directorate_id, center_id,
                                 position_title, decision_reference) VALUES
 (1, '2012-03-01', '2016-09-14', @pd_cas, @c2, 'مكوّنة متدربة', 'قرار 2012/021'),
 (1, '2016-09-15', '2021-08-31', @pd_cas, @c2, 'مكوّنة',        'قرار 2016/087'),
 (1, '2021-09-01', NULL,         @pd_cas, @c1, 'مكوّنة',        'قرار 2021/114'),
 (4, '2015-11-02', NULL,         @pd_cas, NULL,'منسقة تربوية',  'قرار 2015/301');

INSERT INTO employee_teaching (employee_id, season_id, filiere_id, module_id)
SELECT 1, @season, @f_sew, id FROM module WHERE filiere_id = @f_sew AND position IN (1, 2, 3);
INSERT INTO employee_teaching (employee_id, season_id, filiere_id, module_id)
VALUES (3, @season, @f_past, NULL);
