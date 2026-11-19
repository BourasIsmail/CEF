-- ============================================================================
-- Suppression des 3 filières de TEST du jeu de démonstration (profil « dev ») :
--   SEW  خياطة وتفصيل عصري   |   HAIR  الحلاقة والتجميل   |   PAST  الحلويات
-- et de tout ce qui en dépend (inscriptions, examens, notes, diplômes, modules, affectations aux centres,
-- enseignement, contrats de formateurs invités).
--
-- À LANCER UNE SEULE FOIS, sur la base de test qui a reçu le jeu de démonstration, APRÈS UNE SAUVEGARDE :
--     mysqldump --single-transaction -h <hote> -u <compte> -p <base> > avant-purge.sql
--     mysql -h <hote> -u <compte> -p --default-character-set=utf8mb4 <base> < docs/sql/purge-test-filieres.sql
--
-- Garde-fous :
--   * tout est exécuté dans UNE transaction, validée seulement à la dernière ligne : à la première erreur
--     (par exemple une demande de filière qui référence une de ces filières), le client s'arrête et tout est annulé ;
--   * seules les filières dont le code est SEW, HAIR ou PAST sont visées : le catalogue officiel (F001…) est intact ;
--   * les bénéficiaires, centres et employés de démonstration ne sont PAS supprimés, sauf si vous passez
--     @delete_orphan_beneficiaries à 1 (uniquement ceux dont TOUTES les inscriptions étaient dans ces 3 filières).
-- ============================================================================

SET @delete_orphan_beneficiaries = 0;      -- 1 = supprimer aussi les bénéficiaires de démonstration devenus sans inscription

SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_test_filiere AS
    SELECT id FROM filiere WHERE code IN ('SEW', 'HAIR', 'PAST');
CREATE TEMPORARY TABLE tmp_test_module AS
    SELECT id FROM module WHERE filiere_id IN (SELECT id FROM tmp_test_filiere);
CREATE TEMPORARY TABLE tmp_test_enrollment AS
    SELECT id, beneficiary_id FROM enrollment WHERE filiere_id IN (SELECT id FROM tmp_test_filiere);
-- bénéficiaires dont toutes les inscriptions sont dans les filières de test (à calculer AVANT les suppressions)
CREATE TEMPORARY TABLE tmp_test_beneficiary AS
    SELECT DISTINCT te.beneficiary_id AS id
    FROM tmp_test_enrollment te
    WHERE NOT EXISTS (SELECT 1 FROM enrollment e
                      WHERE e.beneficiary_id = te.beneficiary_id
                        AND e.filiere_id NOT IN (SELECT id FROM tmp_test_filiere));

SELECT 'AVANT' AS etape,
       (SELECT COUNT(*) FROM tmp_test_filiere)      AS filieres_test,
       (SELECT COUNT(*) FROM tmp_test_module)       AS modules,
       (SELECT COUNT(*) FROM tmp_test_enrollment)   AS inscriptions,
       (SELECT COUNT(*) FROM tmp_test_beneficiary)  AS beneficiaires_sans_autre_inscription,
       (SELECT COUNT(*) FROM filiere)               AS filieres_total;

START TRANSACTION;

DELETE FROM employee_teaching       WHERE filiere_id IN (SELECT id FROM tmp_test_filiere)
                                       OR module_id  IN (SELECT id FROM tmp_test_module);
DELETE FROM employee_guest_contract WHERE filiere_id IN (SELECT id FROM tmp_test_filiere);

DELETE FROM module_grade WHERE module_id IN (SELECT id FROM tmp_test_module)
                            OR exam_session_id IN (SELECT id FROM exam_session
                                                   WHERE enrollment_id IN (SELECT id FROM tmp_test_enrollment));
DELETE FROM exam_session WHERE enrollment_id IN (SELECT id FROM tmp_test_enrollment);
DELETE FROM diploma      WHERE enrollment_id IN (SELECT id FROM tmp_test_enrollment);
DELETE FROM enrollment   WHERE id            IN (SELECT id FROM tmp_test_enrollment);

DELETE FROM center_filiere WHERE filiere_id IN (SELECT id FROM tmp_test_filiere);
DELETE FROM module         WHERE id         IN (SELECT id FROM tmp_test_module);
-- échoue (et annule tout) si une demande de filière référence encore une de ces filières
DELETE FROM filiere        WHERE id         IN (SELECT id FROM tmp_test_filiere);

DELETE FROM beneficiary WHERE @delete_orphan_beneficiaries = 1 AND id IN (SELECT id FROM tmp_test_beneficiary);

SELECT 'APRES' AS etape,
       (SELECT COUNT(*) FROM filiere WHERE code IN ('SEW', 'HAIR', 'PAST')) AS filieres_test_restantes,
       (SELECT COUNT(*) FROM filiere)                                        AS filieres_total,
       (SELECT COUNT(*) FROM beneficiary b WHERE NOT EXISTS (SELECT 1 FROM enrollment e WHERE e.beneficiary_id = b.id)) AS beneficiaires_sans_inscription;

COMMIT;
