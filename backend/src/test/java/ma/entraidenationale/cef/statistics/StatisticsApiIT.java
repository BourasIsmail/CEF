package ma.entraidenationale.cef.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import ma.entraidenationale.cef.AbstractApiIT;

/**
 * Tableau statistique, vérifié contre le jeu de données de développement (20 bénéficiaires, comptes calculés à la
 * main). Région 06 : 19 inscrits, 9 diplômés, 3 recalés, 3 partis, 2 absents, 2 encore inscrits.
 */
class StatisticsApiIT extends AbstractApiIT {

    // ------------------------------------------------------------------ structure et chiffres

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/statistics/report")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/statistics/report.xlsx")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesTheWholeHierarchyWithTotals() throws Exception {
        mvc.perform(get("/api/statistics/report").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season.label").value("2025-2026"))
                .andExpect(jsonPath("$.regions[*].code", contains("04", "06")))
                // total général : 20 inscrits, 10 diplômés, 3 recalés, 3 partis, 2 absents
                .andExpect(jsonPath("$.grandTotal.registered.total").value(20))
                .andExpect(jsonPath("$.grandTotal.registered.male").value(6))
                .andExpect(jsonPath("$.grandTotal.registered.female").value(14))
                .andExpect(jsonPath("$.grandTotal.graduates.total").value(10))
                .andExpect(jsonPath("$.grandTotal.failed.total").value(3))
                .andExpect(jsonPath("$.grandTotal.departed.total").value(3))
                .andExpect(jsonPath("$.grandTotal.absent.total").value(2))
                .andExpect(jsonPath("$.grandTotal.passRatePercent").value(76.9));    // 10 / (10 + 3)
    }

    @Test
    void regionTotalsAndPassRate() throws Exception {
        mvc.perform(get("/api/statistics/report").with(regional("06")))
                .andExpect(jsonPath("$.regions", hasSize(1)))
                .andExpect(jsonPath("$.regions[0].provinces[*].code", contains("0601", "0602")))
                .andExpect(jsonPath("$.regions[0].total.registered.total").value(19))
                .andExpect(jsonPath("$.regions[0].total.graduates.total").value(9))
                .andExpect(jsonPath("$.regions[0].total.failed.total").value(3))
                .andExpect(jsonPath("$.regions[0].total.departed.total").value(3))
                .andExpect(jsonPath("$.regions[0].total.absent.total").value(2))
                .andExpect(jsonPath("$.regions[0].total.passRatePercent").value(75.0)); // 9 / (9 + 3)
    }

    @Test
    void provinceTotalsAreTheSumOfTheirFilieres() throws Exception {
        mvc.perform(get("/api/statistics/report").with(provincial("0601")))
                .andExpect(jsonPath("$.regions[0].provinces", hasSize(1)))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[*].code", contains("HAIR", "PAST", "SEW")))
                .andExpect(jsonPath("$.regions[0].provinces[0].total.registered.total").value(14))
                // filière « خياطة » de Casablanca : 6 inscrits (1 garçon, 5 filles), 4 diplômées, 1 recalé, 1 partie
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].total.registered.male").value(1))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].total.registered.female").value(5))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].total.graduates.total").value(4))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].total.failed.total").value(1))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].total.departed.total").value(1));
    }

    @Test
    void categoriesFollowThePriorityRuleAndRemainingIsTheDifference() throws Exception {
        MvcResult result = mvc.perform(get("/api/statistics/report").with(admin())).andExpect(status().isOk()).andReturn();
        // « خياطة » à Casablanca : migrantes 5 et 6 (la 6 est aussi handicapée → migrante), handicap 4, restes 1, 2, 3
        mvc.perform(get("/api/statistics/report").with(provincial("0601")))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].categories[*].category",
                        contains("MIGRANT", "DISABLED", "OTHER", "REMAINING")))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].categories[0].metrics.registered.total").value(contains(2)))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].categories[1].metrics.registered.total").value(contains(1)))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].categories[2].metrics.registered.total").value(contains(0)))
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres[?(@.code=='SEW')].categories[3].metrics.registered.total").value(contains(3)));
        assertThat(result.getResponse().getContentAsString()).contains("\"specification\":\"أرملة معيلة\"");
    }

    /** « Reste » = total de la filière − migrants − handicap − autre, pour CHAQUE filière du rapport. */
    @Test
    void remainingIsExactlyTheFiliereTotalMinusTheOtherCategories() throws Exception {
        String json = mvc.perform(get("/api/statistics/report").with(admin())).andReturn().getResponse().getContentAsString();
        var root = new tools.jackson.databind.json.JsonMapper().readTree(json);
        int checked = 0;
        for (var region : root.get("regions")) {
            for (var province : region.get("provinces")) {
                for (var filiere : province.get("filieres")) {
                    for (String metric : List.of("registered", "departed", "graduates", "failed", "absent")) {
                        for (String part : List.of("male", "female", "total")) {
                            long total = filiere.get("total").get(metric).get(part).asLong();
                            long m = filiere.get("categories").get(0).get("metrics").get(metric).get(part).asLong();
                            long d = filiere.get("categories").get(1).get("metrics").get(metric).get(part).asLong();
                            long o = filiere.get("categories").get(2).get("metrics").get(metric).get(part).asLong();
                            long r = filiere.get("categories").get(3).get("metrics").get(metric).get(part).asLong();
                            assertThat(r).as("%s/%s/%s", filiere.get("code").asText(), metric, part).isEqualTo(total - m - d - o);
                            checked++;
                        }
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
    }

    @Test
    void absentAreCountedApartFromFailedAndExcludedFromThePassRate() throws Exception {
        // Mohammedia : 2 absents (7 « خياطة », 18 « حلويات »), aucun recalé ; 1 diplômé ⇒ taux = 100 %
        mvc.perform(get("/api/statistics/report").with(provincial("0602")))
                .andExpect(jsonPath("$.regions[0].provinces[0].total.absent.total").value(2))
                .andExpect(jsonPath("$.regions[0].provinces[0].total.failed.total").value(0))
                .andExpect(jsonPath("$.regions[0].provinces[0].total.passRatePercent").value(100.0));
    }

    @Test
    void passRateIsNullWithoutGraduatesNorFailed() throws Exception {
        long previous = jdbc.queryForObject("SELECT id FROM training_season WHERE label = '2024-2025'", Long.class);
        mvc.perform(get("/api/statistics/report").param("seasonId", String.valueOf(previous)).with(admin()))
                .andExpect(jsonPath("$.season.label").value("2024-2025"))
                .andExpect(jsonPath("$.grandTotal.registered.total").value(0))
                .andExpect(jsonPath("$.grandTotal.passRatePercent").doesNotExist())
                // les filières proposées apparaissent quand même, avec des zéros
                .andExpect(jsonPath("$.regions[0].provinces[0].filieres").isNotEmpty());
    }

    // ------------------------------------------------------------------ périmètre et filtres

    @Test
    void scopeIsAlwaysApplied() throws Exception {
        mvc.perform(get("/api/statistics/report").with(provincial("0601")))
                .andExpect(jsonPath("$.regions", hasSize(1))).andExpect(jsonPath("$.grandTotal.registered.total").value(14));
        mvc.perform(get("/api/statistics/report").with(regional("04")))
                .andExpect(jsonPath("$.grandTotal.registered.total").value(1));
        // demander une autre région / province / un centre hors périmètre ne donne rien
        mvc.perform(get("/api/statistics/report").param("regionId", String.valueOf(regionId("04"))).with(provincial("0601")))
                .andExpect(jsonPath("$.regions", hasSize(0))).andExpect(jsonPath("$.grandTotal.registered.total").value(0));
        mvc.perform(get("/api/statistics/report").param("centerId", String.valueOf(centerId("RAB-01"))).with(regional("06")))
                .andExpect(jsonPath("$.grandTotal.registered.total").value(0));
    }

    @Test
    void filters() throws Exception {
        mvc.perform(get("/api/statistics/report").param("filiereId", String.valueOf(filiereId("PAST"))).with(admin()))
                .andExpect(jsonPath("$.grandTotal.registered.total").value(6));           // 15, 16, 17, 18, 19, 20
        mvc.perform(get("/api/statistics/report").param("provinceId", String.valueOf(provinceId("0602"))).with(admin()))
                .andExpect(jsonPath("$.regions", hasSize(1))).andExpect(jsonPath("$.grandTotal.registered.total").value(5));
        mvc.perform(get("/api/statistics/report").param("centerId", String.valueOf(centerId("CAS-01"))).with(admin()))
                .andExpect(jsonPath("$.grandTotal.registered.total").value(6));
        mvc.perform(get("/api/statistics/report").param("centerId", String.valueOf(centerId("CAS-01")))
                        .param("filiereId", String.valueOf(filiereId("SEW"))).with(admin()))
                .andExpect(jsonPath("$.grandTotal.registered.total").value(4));
    }

    // ------------------------------------------------------------------ tableau de bord

    @Test
    void dashboard() throws Exception {
        mvc.perform(get("/api/statistics/dashboard").with(regional("06")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.registered.total").value(19))
                .andExpect(jsonPath("$.inProgress").value(2))
                .andExpect(jsonPath("$.byFiliere", hasSize(3)))
                .andExpect(jsonPath("$.byCategory[*].category", contains("MIGRANT", "DISABLED", "OTHER", "REMAINING")))
                .andExpect(jsonPath("$.byCategory[0].registered").value(4))     // 5, 6, 11, 18
                // les 10 diplômés du seed ont tous un diplôme enregistré
                .andExpect(jsonPath("$.todo.graduatesWithoutDiploma").value(0));
        mvc.perform(get("/api/statistics/dashboard").with(provincial("0602")))
                .andExpect(jsonPath("$.totals.registered.total").value(5));
    }

    // ------------------------------------------------------------------ export Excel

    @Test
    void excelExportReproducesTheOfficialLayout() throws Exception {
        MvcResult result = mvc.perform(get("/api/statistics/report.xlsx").with(regional("06")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).contains("spreadsheetml");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("statistiques-cef-2025-2026.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.isRightToLeft()).isTrue();
            assertThat(sheet.getSheetName()).isEqualTo("الإحصائيات");
            assertThat(cell(sheet, 0, 0)).isEqualTo("حصيلة المستفيدين من برنامج مراكز التربية والتكوين");
            assertThat(cell(sheet, 1, 0)).isEqualTo("الموسم التكويني 2025-2026");

            // en-têtes : 2 lignes avec fusions
            assertThat(cell(sheet, 3, 0)).isEqualTo("الجهة");
            assertThat(cell(sheet, 3, 1)).isEqualTo("الإقليم");
            assertThat(cell(sheet, 3, 2)).isEqualTo("الشعبة");
            assertThat(cell(sheet, 3, 3)).isEqualTo("الفئة المستفيدة");
            assertThat(cell(sheet, 3, 4)).isEqualTo("المسجلين");
            assertThat(cell(sheet, 3, 7)).isEqualTo("المغادرين");
            assertThat(cell(sheet, 3, 10)).isEqualTo("الخريجين");
            assertThat(cell(sheet, 3, 13)).isEqualTo("الراسبين");
            assertThat(cell(sheet, 3, 16)).isEqualTo("الغائبين");
            assertThat(cell(sheet, 3, 19)).isEqualTo("النسبة المئوية للنجاح");
            assertThat(cell(sheet, 4, 4)).isEqualTo("ذكور");
            assertThat(cell(sheet, 4, 5)).isEqualTo("إناث");
            assertThat(cell(sheet, 4, 6)).isEqualTo("المجموع");
            assertThat(mergedRegions(sheet)).contains("A4:A5", "T4:T5", "E4:G4", "H4:J4", "K4:M4", "N4:P4", "Q4:S4");

            // corps : la 1re ligne de données est « المهاجرين » de la 1re filière de la 1re province
            assertThat(cell(sheet, 5, 0)).isEqualTo("المديرية الجهوية للدار البيضاء-سطات");
            assertThat(cell(sheet, 5, 1)).isEqualTo("المديرية الإقليمية للدار البيضاء");
            assertThat(cell(sheet, 5, 3)).isEqualTo("المهاجرين");
            // fusions : région (toutes ses lignes sauf son total), province (avec son total), filière (4 catégories)
            assertThat(mergedRegions(sheet)).contains("A6:A27", "B6:B18", "C6:C9");

            // ligne « مجموع الجهة » : 19 inscrits, 9 diplômés, 3 recalés, 75 %
            Row regionTotal = findRow(sheet, "مجموع الجهة");
            assertThat(regionTotal.getCell(6).getNumericCellValue()).isEqualTo(19.0);
            assertThat(regionTotal.getCell(12).getNumericCellValue()).isEqualTo(9.0);
            assertThat(regionTotal.getCell(15).getNumericCellValue()).isEqualTo(3.0);
            assertThat(regionTotal.getCell(19).getNumericCellValue()).isEqualTo(0.75);
            assertThat(findRow(sheet, "مجموع الإقليم")).isNotNull();
        }
    }

    @Test
    void excelExportInFrenchIsLeftToRight() throws Exception {
        byte[] body = mvc.perform(get("/api/statistics/report.xlsx").param("lang", "fr").with(provincial("0601")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.isRightToLeft()).isFalse();
            assertThat(cell(sheet, 3, 0)).isEqualTo("Région");
            assertThat(cell(sheet, 3, 4)).isEqualTo("Inscrits");
            assertThat(cell(sheet, 5, 1)).isEqualTo("Direction provinciale de Casablanca");
            assertThat(findRow(sheet, "Total région")).isNotNull();
        }
    }

    @Test
    void excelExportFollowsTheScope() throws Exception {
        byte[] body = mvc.perform(get("/api/statistics/report.xlsx").with(provincial("0602")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(findRow(sheet, "مجموع الجهة").getCell(6).getNumericCellValue()).isEqualTo(5.0);
            // un seul niveau de région : pas de « المجموع العام »
            assertThat(findRowOrNull(sheet, "المجموع العام")).isNull();
        }
    }

    // ------------------------------------------------------------------ export PDF

    @Test
    void pdfExportIsAValidPdf() throws Exception {
        MvcResult result = mvc.perform(get("/api/statistics/report.pdf").with(regional("06"))).andExpect(status().isOk()).andReturn();
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(10_000); // police arabe embarquée
        byte[] french = mvc.perform(get("/api/statistics/report.pdf").param("lang", "fr").with(admin()))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(french).isNotEmpty();
        // échantillons pour contrôle visuel (target/export-samples)
        java.nio.file.Path dir = java.nio.file.Path.of("target", "export-samples");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.write(dir.resolve("statistiques-ar.pdf"), pdf);
        java.nio.file.Files.write(dir.resolve("statistiques-fr.pdf"), french);
        java.nio.file.Files.write(dir.resolve("statistiques-ar.xlsx"),
                mvc.perform(get("/api/statistics/report.xlsx").with(admin())).andReturn().getResponse().getContentAsByteArray());
    }

    // ------------------------------------------------------------------ utilitaires

    private static String cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        return r == null || r.getCell(col) == null ? null : r.getCell(col).getStringCellValue();
    }

    private static List<String> mergedRegions(Sheet sheet) {
        return sheet.getMergedRegions().stream().map(org.apache.poi.ss.util.CellRangeAddress::formatAsString).toList();
    }

    private static Row findRow(Sheet sheet, String label) {
        Row row = findRowOrNull(sheet, label);
        assertThat(row).as("ligne « %s »", label).isNotNull();
        return row;
    }

    private static Row findRowOrNull(Sheet sheet, String label) {
        for (Row row : sheet) {
            for (int c = 0; c < 4; c++) {
                if (row.getCell(c) != null && row.getCell(c).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && label.equals(row.getCell(c).getStringCellValue())) {
                    return row;
                }
            }
        }
        return null;
    }
}
