package ma.entraidenationale.cef.enrollment;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ma.entraidenationale.cef.AbstractApiIT;

/** Inscriptions, cycle de vie, sessions d'examen (normale / rattrapage), bulletin et diplôme. */
class EnrollmentApiIT extends AbstractApiIT {

    /** Bénéficiaire du seed, inscrit en « خياطة » à MOH-01, sans aucun résultat : terrain de jeu des tests. */
    private static final String FREE = "2025-0202";

    private long freeEnrollment() {
        return enrollmentOf(beneficiaryId(FREE));
    }

    private List<Long> sewingModules() {
        return jdbc.queryForList("SELECT id FROM module WHERE filiere_id = ? ORDER BY position", Long.class, filiereId("SEW"));
    }

    /** {"finalExamStatus": "TAKEN", "finalExamGrade": ..., "grades": [6 modules à la même note]} */
    private String session(double moduleGrade, Double finalGrade, String extra) {
        String grades = sewingModules().stream()
                .map(id -> "{\"moduleId\": %d, \"status\": \"TAKEN\", \"grade\": %s}".formatted(id, moduleGrade))
                .collect(Collectors.joining(","));
        String finalPart = finalGrade == null ? "\"finalExamStatus\": \"NOT_TAKEN\"" : "\"finalExamStatus\": \"TAKEN\", \"finalExamGrade\": " + finalGrade;
        return "{\"examNumber\": \"EX-T-1\", %s, \"grades\": [%s]%s}".formatted(finalPart, grades, extra.isEmpty() ? "" : ", " + extra);
    }

    private ResultActions putSession(RequestPostProcessor who, long enrollment, String type, String json) throws Exception {
        return mvc.perform(put("/api/enrollments/" + enrollment + "/sessions/" + type).contentType(APPLICATION_JSON).content(json).with(who));
    }

    private String statusOf(long enrollment) {
        return jdbc.queryForObject("SELECT status FROM enrollment WHERE id = ?", String.class, enrollment);
    }

    // ------------------------------------------------------------------ inscription

    @Test
    void enrollmentRules() throws Exception {
        long b = beneficiaryId(FREE);
        long enrollment = freeEnrollment(); // avant d'en créer une seconde
        long previous = jdbc.queryForObject("SELECT id FROM training_season WHERE label = '2024-2025'", Long.class);
        String sew = "{\"filiereId\": %d, \"seasonId\": %d, \"startDate\": \"2024-10-14\"}".formatted(filiereId("SEW"), previous);

        // déjà inscrit à la saison courante
        mvc.perform(post("/api/beneficiaries/" + b + "/enrollments").contentType(APPLICATION_JSON).with(provincial("0602"))
                        .content("{\"filiereId\": %d, \"startDate\": \"2025-10-20\"}".formatted(filiereId("SEW"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("enrollment.exists"));
        // filière absente du centre (MOH-01 n'a pas HAIR)
        mvc.perform(post("/api/beneficiaries/" + b + "/enrollments").contentType(APPLICATION_JSON).with(provincial("0602"))
                        .content("{\"filiereId\": %d, \"seasonId\": %d, \"startDate\": \"2024-10-14\"}".formatted(filiereId("HAIR"), previous)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("enrollment.filiere_not_in_center"));
        // droits
        mvc.perform(post("/api/beneficiaries/" + b + "/enrollments").contentType(APPLICATION_JSON).content(sew).with(regional("06")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/beneficiaries/" + b + "/enrollments").contentType(APPLICATION_JSON).content(sew).with(provincial("0601")))
                .andExpect(status().isNotFound());
        // saison précédente : OK
        mvc.perform(post("/api/beneficiaries/" + b + "/enrollments").contentType(APPLICATION_JSON).content(sew).with(provincial("0602")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.seasonLabel").value("2024-2025"));
        // date d'examen avant le début
        mvc.perform(put("/api/enrollments/" + enrollment).contentType(APPLICATION_JSON).with(provincial("0602"))
                        .content("{\"filiereId\": %d, \"startDate\": \"2025-10-13\", \"examDate\": \"2025-09-01\"}".formatted(filiereId("SEW"))))
                .andExpect(jsonPath("$.code").value("enrollment.exam_before_start"));
    }

    @Test
    void departRequiresDateAndReason() throws Exception {
        long e = freeEnrollment();
        mvc.perform(post("/api/enrollments/" + e + "/depart").contentType(APPLICATION_JSON).with(provincial("0602"))
                        .content("{\"departureDate\": \"2026-01-10\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        mvc.perform(post("/api/enrollments/" + e + "/depart").contentType(APPLICATION_JSON).with(provincial("0602"))
                        .content("{\"departureDate\": \"2025-09-01\", \"reason\": \"مرض\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("enrollment.departure_before_start"));
        mvc.perform(post("/api/enrollments/" + e + "/depart").contentType(APPLICATION_JSON).with(provincial("0602"))
                        .content("{\"departureDate\": \"2026-01-10\", \"reason\": \"مرض\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPARTED"))
                .andExpect(jsonPath("$.departureDate").value("2026-01-10"))
                .andExpect(jsonPath("$.departureReason").value("مرض"));
        // pas de session pour un parti
        putSession(provincial("0602"), e, "NORMAL", session(14, 12.0, ""))
                .andExpect(jsonPath("$.code").value("exam.enrollment_departed"));
        // annulation
        mvc.perform(post("/api/enrollments/" + e + "/reinstate").with(provincial("0602")))
                .andExpect(jsonPath("$.status").value("ENROLLED")).andExpect(jsonPath("$.departureDate").doesNotExist());
        mvc.perform(post("/api/enrollments/" + e + "/reinstate").with(provincial("0602")))
                .andExpect(jsonPath("$.code").value("enrollment.not_departed"));
    }

    @Test
    void cannotDepartAfterExamResults() throws Exception {
        mvc.perform(post("/api/enrollments/" + enrollmentOf(beneficiaryId("2025-0001")) + "/depart").contentType(APPLICATION_JSON)
                        .with(provincial("0601")).content("{\"departureDate\": \"2026-10-01\", \"reason\": \"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ bulletin

    @Test
    void bulletinShowsBothSessionsWithModules() throws Exception {
        long e = enrollmentOf(beneficiaryId("2025-0003")); // échoue au normal (9,39), réussit au rattrapage (11,39)
        mvc.perform(get("/api/enrollments/" + e + "/bulletin").with(regional("06")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", hasSize(6)))
                .andExpect(jsonPath("$.sessions[*].type", contains("NORMAL", "MAKEUP")))
                .andExpect(jsonPath("$.sessions[0].result").value("FAILED"))
                .andExpect(jsonPath("$.sessions[0].overallAverage").value(9.39))
                .andExpect(jsonPath("$.sessions[0].grades[2].grade").value(8.0))
                .andExpect(jsonPath("$.sessions[1].result").value("PASSED"))
                .andExpect(jsonPath("$.sessions[1].mention").value("مقبول"))
                .andExpect(jsonPath("$.status").value("GRADUATED"))
                .andExpect(jsonPath("$.diploma.serialNumber").value("CEF-2026-000003"));
        mvc.perform(get("/api/enrollments/" + e + "/bulletin").with(provincial("0602"))).andExpect(status().isNotFound());
    }

    @Test
    void bulletinPdfIsGeneratedByTheServerInBothLanguages() throws Exception {
        long e = enrollmentOf(beneficiaryId("2025-0003")); // deux sessions + diplôme
        var ar = mvc.perform(get("/api/enrollments/" + e + "/bulletin.pdf").with(regional("06"))).andExpect(status().isOk()).andReturn();
        byte[] pdf = ar.getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(ar.getResponse().getContentType()).isEqualTo("application/pdf");
        org.assertj.core.api.Assertions.assertThat(ar.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("bulletin-2025-0003.pdf");
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        org.assertj.core.api.Assertions.assertThat(pdf.length).isGreaterThan(10_000); // police arabe embarquée
        byte[] fr = mvc.perform(get("/api/enrollments/" + e + "/bulletin.pdf").param("lang", "fr").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(fr).isNotEmpty();
        // un bénéficiaire sans aucune session donne aussi un document valide
        byte[] empty = mvc.perform(get("/api/enrollments/" + freeEnrollment() + "/bulletin.pdf").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(empty).isNotEmpty();
        java.nio.file.Path dir = java.nio.file.Path.of("target", "export-samples");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.write(dir.resolve("bulletin-ar.pdf"), pdf);
        java.nio.file.Files.write(dir.resolve("bulletin-fr.pdf"), fr);
        java.nio.file.Files.write(dir.resolve("bulletin-vide.pdf"), empty);
    }

    @Test
    void bulletinPdfRespectsScopeAndAuthentication() throws Exception {
        long e = enrollmentOf(beneficiaryId("2025-0003"));
        mvc.perform(get("/api/enrollments/" + e + "/bulletin.pdf").with(provincial("0602"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/enrollments/" + e + "/bulletin.pdf")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ saisie d'une session

    @Test
    void passingTheNormalSessionGraduates() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", session(14, 12.0, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].modulesAverage").value(14.0))
                .andExpect(jsonPath("$.sessions[0].overallAverage").value(13.0))
                .andExpect(jsonPath("$.sessions[0].result").value("PASSED"))
                .andExpect(jsonPath("$.sessions[0].mention").value("مستحسن"))
                .andExpect(jsonPath("$.status").value("GRADUATED"))
                .andExpect(jsonPath("$.makeupAllowed").value(false));
        org.assertj.core.api.Assertions.assertThat(statusOf(e)).isEqualTo("GRADUATED");
    }

    @Test
    void failingTheNormalSessionKeepsTheBeneficiaryEnrolledForTheMakeup() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", session(8, 9.0, ""))
                .andExpect(jsonPath("$.sessions[0].result").value("FAILED"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.makeupAllowed").value(true));
        // rattrapage : tous les examens sont repris
        putSession(provincial("0602"), e, "MAKEUP", session(12, 11.0, ""))
                .andExpect(jsonPath("$.sessions[1].result").value("PASSED"))
                .andExpect(jsonPath("$.status").value("GRADUATED"));
    }

    @Test
    void failingTheMakeupToo() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", session(8, 9.0, ""));
        putSession(provincial("0602"), e, "MAKEUP", session(9, 9.0, ""))
                .andExpect(jsonPath("$.sessions[1].result").value("FAILED")).andExpect(jsonPath("$.status").value("FAILED"));
        // supprimer le rattrapage ramène l'inscription à « inscrit »
        mvc.perform(delete("/api/enrollments/" + e + "/sessions/MAKEUP").with(provincial("0602")))
                .andExpect(jsonPath("$.status").value("ENROLLED")).andExpect(jsonPath("$.sessions", hasSize(1)));
        // on ne supprime pas la session normale tant que le rattrapage existe
        putSession(provincial("0602"), e, "MAKEUP", session(9, 9.0, ""));
        mvc.perform(delete("/api/enrollments/" + e + "/sessions/NORMAL").with(provincial("0602")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("exam.makeup_exists"));
    }

    @Test
    void makeupIsOnlyAllowedAfterAFailedOrAbsentNormalSession() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "MAKEUP", session(12, 11.0, ""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("exam.makeup_not_allowed"));
        putSession(provincial("0602"), e, "NORMAL", session(14, 12.0, ""));
        putSession(provincial("0602"), e, "MAKEUP", session(12, 11.0, ""))
                .andExpect(jsonPath("$.code").value("exam.makeup_not_allowed")); // déjà réussi
    }

    @Test
    void absentIsCountedApartAndNeverAsFailed() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"ABSENT\", \"grades\": []}")
                .andExpect(jsonPath("$.sessions[0].result").value("ABSENT"))
                .andExpect(jsonPath("$.sessions[0].overallAverage").doesNotExist())
                .andExpect(jsonPath("$.status").value("ENROLLED"))          // reste inscrit : le rattrapage est possible
                .andExpect(jsonPath("$.makeupAllowed").value(true));
        putSession(provincial("0602"), e, "MAKEUP", "{\"finalExamStatus\": \"ABSENT\", \"grades\": []}")
                .andExpect(jsonPath("$.status").value("ABSENT"));
        org.assertj.core.api.Assertions.assertThat(statusOf(e)).isEqualTo("ABSENT");
    }

    @Test
    void anAbsentModuleMakesTheSessionAbsent() throws Exception {
        long e = freeEnrollment();
        List<Long> modules = sewingModules();
        String grades = modules.stream().map(id -> id.equals(modules.get(1))
                        ? "{\"moduleId\": %d, \"status\": \"ABSENT\"}".formatted(id)
                        : "{\"moduleId\": %d, \"status\": \"TAKEN\", \"grade\": 14}".formatted(id))
                .collect(Collectors.joining(","));
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"TAKEN\", \"finalExamGrade\": 15, \"grades\": [" + grades + "]}")
                .andExpect(jsonPath("$.sessions[0].result").value("ABSENT"))
                .andExpect(jsonPath("$.sessions[0].grades[1].status").value("ABSENT"));
    }

    @Test
    void partialEntryIsSavedAsIncomplete() throws Exception {
        long e = freeEnrollment();
        long first = sewingModules().get(0);
        putSession(provincial("0602"), e, "NORMAL",
                "{\"finalExamStatus\": \"NOT_TAKEN\", \"grades\": [{\"moduleId\": %d, \"status\": \"TAKEN\", \"grade\": 12}]}".formatted(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].result").value("INCOMPLETE"))
                .andExpect(jsonPath("$.sessions[0].modulesAverage").doesNotExist())
                .andExpect(jsonPath("$.sessions[0].grades[0].grade").value(12.0))
                .andExpect(jsonPath("$.sessions[0].grades[1].status").value("NOT_TAKEN"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.makeupAllowed").value(false));
    }

    @Test
    void thresholdCanBeChangedByTheOperator() throws Exception {
        long e = freeEnrollment();
        // 9,5 sous le seuil par défaut (10) ...
        putSession(provincial("0602"), e, "NORMAL", session(9.5, 9.5, "")).andExpect(jsonPath("$.sessions[0].result").value("FAILED"));
        // ... mais réussi avec un seuil abaissé à 9 : « par indulgence »
        putSession(provincial("0602"), e, "NORMAL", session(9.5, 9.5, "\"passThreshold\": 9"))
                .andExpect(jsonPath("$.sessions[0].result").value("PASSED"))
                .andExpect(jsonPath("$.sessions[0].passThreshold").value(9.0))
                .andExpect(jsonPath("$.sessions[0].mention").doesNotExist())
                .andExpect(jsonPath("$.status").value("GRADUATED"));
    }

    @Test
    void exceptionalPassNeedsAReasonAndAFailingCompleteSession() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", session(9, 9.0, "\"exceptionalPass\": true"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("exam.exceptional_reason_required"));
        putSession(provincial("0602"), e, "NORMAL", session(14, 12.0, "\"exceptionalPass\": true, \"exceptionalPassReason\": \"ظروف صحية\""))
                .andExpect(jsonPath("$.code").value("exam.exceptional_not_applicable")); // il réussit déjà
        putSession(provincial("0602"), e, "NORMAL", session(9, 9.0, "\"exceptionalPass\": true, \"exceptionalPassReason\": \"ظروف صحية\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].result").value("PASSED"))
                .andExpect(jsonPath("$.sessions[0].exceptionalPass").value(true))
                .andExpect(jsonPath("$.sessions[0].exceptionalPassBy").value("provincial-0602"))
                .andExpect(jsonPath("$.status").value("GRADUATED"));
    }

    @Test
    void gradeValidation() throws Exception {
        long e = freeEnrollment();
        long first = sewingModules().get(0);
        long foreign = jdbc.queryForObject("SELECT id FROM module WHERE filiere_id = ? LIMIT 1", Long.class, filiereId("HAIR"));
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"TAKEN\", \"grades\": []}")
                .andExpect(jsonPath("$.code").value("exam.grade_required"));
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"ABSENT\", \"finalExamGrade\": 10, \"grades\": []}")
                .andExpect(jsonPath("$.code").value("exam.grade_forbidden"));
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"NOT_TAKEN\", \"grades\": [{\"moduleId\": %d, \"status\": \"TAKEN\"}]}".formatted(first))
                .andExpect(jsonPath("$.code").value("exam.grade_required"));
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"NOT_TAKEN\", \"grades\": [{\"moduleId\": %d, \"status\": \"ABSENT\", \"grade\": 5}]}".formatted(first))
                .andExpect(jsonPath("$.code").value("exam.grade_forbidden"));
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"NOT_TAKEN\", \"grades\": [{\"moduleId\": %d, \"status\": \"TAKEN\", \"grade\": 12}]}".formatted(foreign))
                .andExpect(jsonPath("$.code").value("exam.module_unknown"));
        putSession(provincial("0602"), e, "NORMAL", ("{\"finalExamStatus\": \"NOT_TAKEN\", \"grades\": [{\"moduleId\": %d, \"status\": \"TAKEN\", \"grade\": 12},"
                + "{\"moduleId\": %d, \"status\": \"TAKEN\", \"grade\": 13}]}").formatted(first, first))
                .andExpect(jsonPath("$.code").value("exam.duplicate_module"));
        putSession(provincial("0602"), e, "NORMAL", session(21, 12.0, ""))      // > 20
                .andExpect(status().isBadRequest());
    }

    @Test
    void sessionsRespectTheProfile() throws Exception {
        long e = freeEnrollment();
        putSession(regional("06"), e, "NORMAL", session(14, 12.0, "")).andExpect(status().isForbidden());
        putSession(provincial("0601"), e, "NORMAL", session(14, 12.0, "")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ clôture sans rattrapage

    @Test
    void finalizeWithoutMakeup() throws Exception {
        long e = freeEnrollment();
        mvc.perform(post("/api/enrollments/" + e + "/finalize").with(provincial("0602")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("enrollment.cannot_finalize"));
        putSession(provincial("0602"), e, "NORMAL", session(8, 9.0, ""));
        mvc.perform(post("/api/enrollments/" + e + "/finalize").with(provincial("0602")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void finalizeAfterAnAbsentNormalSessionGivesAbsent() throws Exception {
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", "{\"finalExamStatus\": \"ABSENT\", \"grades\": []}");
        mvc.perform(post("/api/enrollments/" + e + "/finalize").with(provincial("0602")))
                .andExpect(jsonPath("$.status").value("ABSENT"));
    }

    // ------------------------------------------------------------------ diplôme

    @Test
    void diplomaIsReservedToGraduates() throws Exception {
        long e = freeEnrollment();
        String diploma = "{\"serialNumber\": \"CEF-TEST-1\", \"receivedDate\": \"2026-10-10\"}";
        mvc.perform(put("/api/enrollments/" + e + "/diploma").contentType(APPLICATION_JSON).content(diploma).with(provincial("0602")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("diploma.not_graduated"));

        putSession(provincial("0602"), e, "NORMAL", session(14, 12.0, ""));
        mvc.perform(put("/api/enrollments/" + e + "/diploma").contentType(APPLICATION_JSON).content(diploma).with(provincial("0602")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serialNumber").value("CEF-TEST-1"));
        // numéro de série unique
        long other = enrollmentOf(beneficiaryId("2025-0001"));
        mvc.perform(put("/api/enrollments/" + other + "/diploma").contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content("{\"serialNumber\": \"CEF-TEST-1\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("diploma.serial_exists"));
        // un diplôme émis verrouille le résultat
        putSession(provincial("0602"), e, "NORMAL", session(8, 9.0, ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("exam.diploma_issued"));
        // suppression du diplôme, puis modification possible
        mvc.perform(delete("/api/enrollments/" + e + "/diploma").with(provincial("0602"))).andExpect(status().isNoContent());
        putSession(provincial("0602"), e, "NORMAL", session(8, 9.0, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    // ------------------------------------------------------------------ suppression d'une inscription + audit

    @Test
    void enrollmentWithResultsCannotBeDeleted() throws Exception {
        mvc.perform(delete("/api/enrollments/" + enrollmentOf(beneficiaryId("2025-0001"))).with(provincial("0601")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("enrollment.has_results"));
        mvc.perform(delete("/api/enrollments/" + freeEnrollment()).with(provincial("0602"))).andExpect(status().isNoContent());
    }

    @Test
    void examAndEnrollmentChangesAppearInTheBeneficiaryHistory() throws Exception {
        long b = beneficiaryId(FREE);
        long e = freeEnrollment();
        putSession(provincial("0602"), e, "NORMAL", session(14, 12.0, ""));
        mvc.perform(get("/api/beneficiaries/" + b + "/history").with(provincial("0602")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.entityType=='EXAM_SESSION' && @.action=='CREATE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.entityType=='EXAM_SESSION')].changes.overallAverage.new").value(contains("13.00")))
                .andExpect(jsonPath("$[?(@.entityType=='ENROLLMENT' && @.action=='UPDATE')].changes.status.new").value(contains("GRADUATED")))
                .andExpect(jsonPath("$[?(@.entityType=='ENROLLMENT')].changes.status.old").value(contains("ENROLLED")));
    }
}
