package ma.entraidenationale.cef.training;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import ma.entraidenationale.cef.AbstractApiIT;

/** Filières, modules, saisons et affectation aux centres. */
class TrainingApiIT extends AbstractApiIT {

    private static final String NEW_FILIERE = """
            {"code": "cook", "nameAr": "الطبخ العصري", "nameFr": "Cuisine moderne", "durationMonths": 12}""";

    // ------------------------------------------------------------------ lecture

    @Test
    void filiereListShowsModuleTotalsAndScopedCenterCounts() throws Exception {
        mvc.perform(get("/api/filieres").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.code=='SEW')].modulesCount").value(6))
                .andExpect(jsonPath("$.content[?(@.code=='SEW')].totalCoefficient").value(14.0))
                .andExpect(jsonPath("$.content[?(@.code=='SEW')].totalHours").value(490))
                .andExpect(jsonPath("$.content[?(@.code=='SEW')].centersCount").value(4))
                .andExpect(jsonPath("$.content[?(@.code=='SEW')].programCode").value("CEF"));
        // un provincial ne compte que les centres de son périmètre (CAS-01 et CAS-02)
        mvc.perform(get("/api/filieres").with(provincial("0601")))
                .andExpect(jsonPath("$.content[?(@.code=='SEW')].centersCount").value(2))
                .andExpect(jsonPath("$.content[?(@.code=='PAST')].centersCount").value(2));
    }

    @Test
    void filiereDetailListsOrderedModulesAndCentersInScope() throws Exception {
        long sew = filiereId("SEW");
        mvc.perform(get("/api/filieres/" + sew).with(provincial("0601")))
                .andExpect(jsonPath("$.modules", hasSize(6)))
                .andExpect(jsonPath("$.modules[*].position", contains(1, 2, 3, 4, 5, 6)))
                .andExpect(jsonPath("$.modules[2].coefficient").value(4.0))
                .andExpect(jsonPath("$.centers[*].code", contains("CAS-01", "CAS-02")));
        mvc.perform(get("/api/filieres/" + sew).with(admin()))
                .andExpect(jsonPath("$.centers", hasSize(4)));
    }

    @Test
    void filiereSearch() throws Exception {
        mvc.perform(get("/api/filieres").param("q", "حلويات").with(regional("06")))
                .andExpect(jsonPath("$.content[*].code", contains("PAST")));
        mvc.perform(get("/api/filieres").param("q", "coiffure").with(regional("06")))
                .andExpect(jsonPath("$.content[*].code", contains("HAIR")));
    }

    // ------------------------------------------------------------------ écriture : droits

    @Test
    void onlyAdminWritesTheCatalogue() throws Exception {
        mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).content(NEW_FILIERE).with(provincial("0601")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).content(NEW_FILIERE).with(regional("06")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).content(NEW_FILIERE).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.code").value("COOK"))
                .andExpect(jsonPath("$.summary.programCode").value("CEF"))
                .andExpect(jsonPath("$.summary.active").value(true))
                .andExpect(jsonPath("$.modules", hasSize(0)));
    }

    @Test
    void filiereValidation() throws Exception {
        mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"code\": \"a b\", \"nameAr\": \"\", \"durationMonths\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.nameAr").exists())
                .andExpect(jsonPath("$.errors.nameFr").exists())
                .andExpect(jsonPath("$.errors.durationMonths").exists());
        mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"code\": \"sew\", \"nameAr\": \"x\", \"nameFr\": \"x\", \"durationMonths\": 6}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("filiere.code_exists"));
    }

    @Test
    void filiereUpdateAndDeactivation() throws Exception {
        long id = filiereId("PAST");
        mvc.perform(put("/api/filieres/" + id).contentType(APPLICATION_JSON).with(admin())
                        .content("{\"code\": \"PAST\", \"nameAr\": \"الحلويات والمعجنات\", \"nameFr\": \"Pâtisserie\", \"durationMonths\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.nameAr").value("الحلويات والمعجنات"))
                .andExpect(jsonPath("$.summary.durationMonths").value(10))
                .andExpect(jsonPath("$.updatedBy").value("admin"));
        mvc.perform(patch("/api/filieres/" + id + "/active").param("value", "false").with(admin()))
                .andExpect(jsonPath("$.summary.active").value(false));
        mvc.perform(get("/api/filieres").param("active", "true").with(admin()))
                .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("SEW", "HAIR")));
    }

    @Test
    void usedFiliereCannotBeDeletedButUnusedCan() throws Exception {
        mvc.perform(delete("/api/filieres/" + filiereId("SEW")).with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("filiere.in_use"));

        String created = mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).content(NEW_FILIERE).with(admin()))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        mvc.perform(post("/api/filieres/" + id + "/modules").contentType(APPLICATION_JSON).with(admin())
                .content("{\"nameAr\": \"م\", \"coefficient\": 1, \"durationHours\": 10}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/filieres/" + id).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/filieres/" + id).with(admin())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ modules

    @Test
    void moduleLifecycle() throws Exception {
        long hair = filiereId("HAIR");
        // ajout en dernière position
        String added = mvc.perform(post("/api/filieres/" + hair + "/modules").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"nameAr\": \"الإسعافات الأولية\", \"coefficient\": 1.5, \"durationHours\": 20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(6))
                .andExpect(jsonPath("$.coefficient").value(1.5))
                .andReturn().getResponse().getContentAsString();
        long moduleId = Long.parseLong(added.replaceAll(".*?\"id\":(\\d+).*", "$1"));

        // modification
        mvc.perform(put("/api/filieres/" + hair + "/modules/" + moduleId).contentType(APPLICATION_JSON).with(admin())
                        .content("{\"nameAr\": \"الإسعافات الأولية\", \"coefficient\": 2, \"durationHours\": 25}"))
                .andExpect(jsonPath("$.coefficient").value(2.0)).andExpect(jsonPath("$.durationHours").value(25));
        mvc.perform(get("/api/filieres/" + hair).with(admin()))
                .andExpect(jsonPath("$.summary.modulesCount").value(6))
                .andExpect(jsonPath("$.summary.totalCoefficient").value(14.0))   // 4+3+3+1+1 + 2
                .andExpect(jsonPath("$.summary.totalHours").value(445));          // 140+110+100+40+30 + 25

        // suppression : les positions restent contiguës
        mvc.perform(delete("/api/filieres/" + hair + "/modules/" + moduleId).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/filieres/" + hair + "/modules").with(admin()))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5)));
    }

    @Test
    void moduleValidation() throws Exception {
        long hair = filiereId("HAIR");
        mvc.perform(post("/api/filieres/" + hair + "/modules").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"nameAr\": \"\", \"coefficient\": 0, \"durationHours\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nameAr").exists())
                .andExpect(jsonPath("$.errors.coefficient").exists())
                .andExpect(jsonPath("$.errors.durationHours").exists());
        mvc.perform(post("/api/filieres/" + hair + "/modules").contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content("{\"nameAr\": \"م\", \"coefficient\": 1, \"durationHours\": 1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void moduleWithGradesCannotBeDeleted() throws Exception {
        long sew = filiereId("SEW");
        long module = jdbc.queryForObject("SELECT id FROM module WHERE filiere_id = ? AND position = 1", Long.class, sew);
        mvc.perform(delete("/api/filieres/" + sew + "/modules/" + module).with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("module.in_use"));
    }

    @Test
    void moduleOfAnotherFiliereIsNotFound() throws Exception {
        long module = jdbc.queryForObject("SELECT id FROM module WHERE filiere_id = ? AND position = 1", Long.class, filiereId("SEW"));
        mvc.perform(put("/api/filieres/" + filiereId("HAIR") + "/modules/" + module).contentType(APPLICATION_JSON).with(admin())
                        .content("{\"nameAr\": \"م\", \"coefficient\": 1, \"durationHours\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modulesCanBeReordered() throws Exception {
        long past = filiereId("PAST");
        List<Long> ids = jdbc.queryForList("SELECT id FROM module WHERE filiere_id = ? ORDER BY position", Long.class, past);
        List<Long> reversed = ids.reversed();
        String json = "{\"moduleIds\": " + reversed + "}";
        mvc.perform(put("/api/filieres/" + past + "/modules/order").contentType(APPLICATION_JSON).content(json).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(reversed.stream().map(Long::intValue).toArray())))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3, 4, 5)));

        mvc.perform(put("/api/filieres/" + past + "/modules/order").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"moduleIds\": [" + ids.get(0) + "]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("module.reorder_mismatch"));
    }

    // ------------------------------------------------------------------ saisons

    @Test
    void seasonsAndCurrentSeason() throws Exception {
        mvc.perform(get("/api/seasons").with(provincial("0601")))
                .andExpect(jsonPath("$[*].label", contains("2025-2026", "2024-2025")));
        mvc.perform(get("/api/seasons/current").with(provincial("0601")))
                .andExpect(jsonPath("$.label").value("2025-2026")).andExpect(jsonPath("$.current").value(true));
        mvc.perform(get("/api/programs").with(provincial("0601"))).andExpect(jsonPath("$[0].code").value("CEF"));
    }

    @Test
    void currentSeasonIsUnique() throws Exception {
        long previous = jdbc.queryForObject("SELECT id FROM training_season WHERE label = '2024-2025'", Long.class);
        mvc.perform(put("/api/seasons/" + previous + "/current").with(regional("06"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/seasons/" + previous + "/current").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("2024-2025"));
        mvc.perform(get("/api/seasons/current").with(admin())).andExpect(jsonPath("$.label").value("2024-2025"));
        mvc.perform(get("/api/seasons").with(admin())).andExpect(jsonPath("$[?(@.current==true)]", hasSize(1)));
    }

    @Test
    void seasonValidation() throws Exception {
        mvc.perform(post("/api/seasons").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"label\": \"2026-2028\", \"startDate\": \"2026-10-01\", \"endDate\": \"2027-09-30\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("season.label_invalid"));
        mvc.perform(post("/api/seasons").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"label\": \"2026-2027\", \"startDate\": \"2027-10-01\", \"endDate\": \"2027-09-30\"}"))
                .andExpect(jsonPath("$.code").value("season.dates_invalid"));
        mvc.perform(post("/api/seasons").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"label\": \"2025/2026\", \"startDate\": \"2026-10-01\", \"endDate\": \"2027-09-30\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/seasons").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"label\": \"2025-2026\", \"startDate\": \"2025-10-01\", \"endDate\": \"2026-09-30\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/seasons").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"label\": \"2026-2027\", \"startDate\": \"2026-10-01\", \"endDate\": \"2027-09-30\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.current").value(false));
    }

    // ------------------------------------------------------------------ filières d'un centre

    @Test
    void centerFilieresFollowTheCenterScope() throws Exception {
        long cas01 = centerId("CAS-01");
        mvc.perform(get("/api/centers/" + cas01 + "/filieres").with(provincial("0601")))
                .andExpect(jsonPath("$[*].code", contains("HAIR", "PAST", "SEW")));
        mvc.perform(get("/api/centers/" + centerId("RAB-01") + "/filieres").with(provincial("0601")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminAssignsFilieres() throws Exception {
        long cas02 = centerId("CAS-02");
        mvc.perform(put("/api/centers/" + cas02 + "/filieres/" + filiereId("PAST")).with(provincial("0601")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/centers/" + cas02 + "/filieres/" + filiereId("PAST")).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].code", contains("HAIR", "PAST", "SEW")));
        // idempotent
        mvc.perform(put("/api/centers/" + cas02 + "/filieres/" + filiereId("PAST")).with(admin()))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void inactiveFiliereCannotBeAssigned() throws Exception {
        long past = filiereId("PAST");
        mvc.perform(patch("/api/filieres/" + past + "/active").param("value", "false").with(admin()));
        mvc.perform(put("/api/centers/" + centerId("CAS-02") + "/filieres/" + past).with(admin()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center_filiere.filiere_inactive"));
    }

    @Test
    void filiereWithEnrollmentsCannotBeRemovedFromTheCenter() throws Exception {
        // CAS-01 a des inscrits en PAST (bénéficiaire 15)
        mvc.perform(delete("/api/centers/" + centerId("CAS-01") + "/filieres/" + filiereId("PAST")).with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("center_filiere.has_enrollments"));
        // CAS-02 n'a aucune inscription en PAST : assignation puis retrait possibles
        long cas02 = centerId("CAS-02");
        mvc.perform(put("/api/centers/" + cas02 + "/filieres/" + filiereId("PAST")).with(admin())).andExpect(status().isOk());
        mvc.perform(delete("/api/centers/" + cas02 + "/filieres/" + filiereId("PAST")).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].code", contains("HAIR", "SEW")));
    }

    @Test
    void replaceSetsTheWholeList() throws Exception {
        long cas02 = centerId("CAS-02"); // HAIR + SEW, tous deux avec des inscrits : on ne peut pas les retirer
        mvc.perform(put("/api/centers/" + cas02 + "/filieres").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"filiereIds\": [" + filiereId("SEW") + "]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/centers/" + cas02 + "/filieres").contentType(APPLICATION_JSON).with(admin())
                        .content("{\"filiereIds\": [" + filiereId("SEW") + "," + filiereId("HAIR") + "," + filiereId("PAST") + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void adminReplacesTheCentersOfAFiliereInOneCall() throws Exception {
        String created = mvc.perform(post("/api/filieres").contentType(APPLICATION_JSON).content(NEW_FILIERE).with(admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        long cas01 = centerId("CAS-01");
        long cas02 = centerId("CAS-02");
        String two = "{\"centerIds\": [%d, %d]}".formatted(cas01, cas02);

        mvc.perform(put("/api/filieres/" + id + "/centers").contentType(APPLICATION_JSON).content(two).with(provincial("0601")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/filieres/" + id + "/centers").contentType(APPLICATION_JSON).content(two).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centers[*].code", containsInAnyOrder("CAS-01", "CAS-02")));
        mvc.perform(put("/api/filieres/" + id + "/centers").contentType(APPLICATION_JSON)
                        .content("{\"centerIds\": [%d]}".formatted(cas02)).with(admin()))
                .andExpect(jsonPath("$.centers[*].code", contains("CAS-02")));
        mvc.perform(put("/api/filieres/" + id + "/centers").contentType(APPLICATION_JSON).content("{\"centerIds\": []}").with(admin()))
                .andExpect(jsonPath("$.centers", hasSize(0)));
    }
}
