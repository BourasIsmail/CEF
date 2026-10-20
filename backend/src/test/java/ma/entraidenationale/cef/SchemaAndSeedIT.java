package ma.entraidenationale.cef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Vérifie les migrations Flyway, le jeu de données de développement et les garde-fous du schéma. */
class SchemaAndSeedIT extends AbstractMysqlIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "100");
    }

    @Test
    void expectedTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class)
                .stream().map(String::toLowerCase).toList();
        assertThat(tables).contains(
                "regional_directorate", "provincial_directorate", "commune", "center", "center_floor_area",
                "space_type", "center_space", "partner_type", "association", "center_association",
                "center_association_partner", "program", "training_season", "filiere", "module", "center_filiere",
                "beneficiary", "enrollment", "exam_session", "module_grade", "diploma", "employee",
                "employee_guest_contract", "employee_assignment", "employee_teaching", "app_user",
                "refresh_token", "audit_log", "filiere_request", "request_document_type", "request_document",
                "request_status_history");
    }

    @Test
    void referenceDataIsLoaded() {
        assertThat(count("regional_directorate")).isEqualTo(12);
        assertThat(count("space_type")).isEqualTo(13);
        assertThat(count("request_document_type")).isEqualTo(8);
        assertThat(count("partner_type")).isEqualTo(3);
    }

    @Test
    void arabicTextRoundTripsInUtf8mb4() {
        String name = jdbc.queryForObject("SELECT name_ar FROM center WHERE code = 'CAS-01'", String.class);
        assertThat(name).isEqualTo("مركز سيدي مومن");
    }

    @Test
    void statCategoryFollowsPriorityRule() {
        Map<String, String> idsByCategory = jdbc.query(
                "SELECT stat_category, GROUP_CONCAT(id ORDER BY id) ids FROM beneficiary GROUP BY stat_category",
                rs -> {
                    Map<String, String> m = new java.util.HashMap<>();
                    while (rs.next()) {
                        m.put(rs.getString(1), rs.getString(2));
                    }
                    return m;
                });
        assertThat(idsByCategory)
                .containsEntry("MIGRANT", "5,6,11,18")   // 6 = migrant + handicap -> migrant (priorité)
                .containsEntry("DISABLED", "4,10,17")
                .containsEntry("OTHER", "7,14,20")       // 20 = précarité + autre -> autre
                .containsEntry("REMAINING", "1,2,3,8,9,12,13,15,16,19"); // 3, 13 = précarité seule
    }

    /** « Remaining » = total de la filière - (migrants + handicap + autre), par construction. */
    @Test
    void remainingEqualsFiliereTotalMinusOtherCategories() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.code,
                       COUNT(*) AS total,
                       SUM(b.stat_category = 'MIGRANT')  AS migrants,
                       SUM(b.stat_category = 'DISABLED') AS disabled,
                       SUM(b.stat_category = 'OTHER')    AS other,
                       SUM(b.stat_category = 'REMAINING') AS remaining
                FROM enrollment e
                JOIN beneficiary b ON b.id = e.beneficiary_id
                JOIN filiere f ON f.id = e.filiere_id
                JOIN training_season s ON s.id = e.season_id AND s.is_current
                GROUP BY f.code
                """);
        assertThat(rows).hasSize(3);
        for (Map<String, Object> r : rows) {
            long total = ((Number) r.get("total")).longValue();
            long others = ((Number) r.get("migrants")).longValue() + ((Number) r.get("disabled")).longValue()
                    + ((Number) r.get("other")).longValue();
            assertThat(((Number) r.get("remaining")).longValue()).as("filière %s", r.get("code")).isEqualTo(total - others);
        }
    }

    @Test
    void seedCoversEveryLifecycleStatus() {
        Map<String, Long> byStatus = jdbc.queryForList("SELECT status, COUNT(*) c FROM enrollment GROUP BY status")
                .stream().collect(Collectors.toMap(r -> (String) r.get("status"), r -> ((Number) r.get("c")).longValue()));
        assertThat(byStatus).containsEntry("GRADUATED", 10L).containsEntry("FAILED", 3L)
                .containsEntry("DEPARTED", 3L).containsEntry("ABSENT", 2L).containsEntry("ENROLLED", 2L);
    }

    @Test
    void makeupSessionExistsForBeneficiaryWhoFailedTheNormalSession() {
        Integer sessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam_session WHERE enrollment_id = 3 AND ((session_type = 'NORMAL' AND passed = 0) OR (session_type = 'MAKEUP' AND passed = 1))",
                Integer.class);
        assertThat(sessions).isEqualTo(2);
        // moyenne générale = (moyenne modules + note finale) / 2
        Double overall = jdbc.queryForObject(
                "SELECT overall_average FROM exam_session WHERE enrollment_id = 3 AND session_type = 'NORMAL'", Double.class);
        assertThat(overall).isEqualTo(9.39);
    }

    // ----- garde-fous : la base refuse les données incohérentes -----

    @Test
    void departedEnrollmentRequiresDepartureDate() {
        // MySQL signale les violations CHECK (erreur 3819) hors de la hiérarchie DataIntegrityViolation
        assertThatThrownBy(() -> jdbc.update("UPDATE enrollment SET status = 'DEPARTED' WHERE id = 8"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_enrollment_departure");
    }

    @Test
    void enrollmentMustUseAFiliereAssignedToTheCenter() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE enrollment SET filiere_id = (SELECT id FROM filiere WHERE code = 'PAST') WHERE id = 5"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void employeeCenterMustBelongToEmployeeProvince() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE employee SET provincial_directorate_id = (SELECT id FROM provincial_directorate WHERE code = '0602') WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void onlyOneCurrentSeasonIsAllowed() {
        assertThatThrownBy(() -> jdbc.update("UPDATE training_season SET is_current = TRUE WHERE label = '2024-2025'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void exceptionalPassRequiresAJustification() {
        assertThatThrownBy(() -> jdbc.update("UPDATE exam_session SET exceptional_pass = TRUE WHERE id = 1"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_exam_session_exceptional");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
