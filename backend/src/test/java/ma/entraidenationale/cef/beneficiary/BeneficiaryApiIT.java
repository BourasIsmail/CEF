package ma.entraidenationale.cef.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ma.entraidenationale.cef.AbstractApiIT;

/** Fiches des bénéficiaires : périmètre, droits, situation sociale multiple, numérotation, audit. */
class BeneficiaryApiIT extends AbstractApiIT {

    private String body(long centerId, String extra) {
        return """
                {"centerId": %d, "registrationDate": "2025-10-20", "gender": "F", "fullNameAr": "سعيدة الإدريسي",
                 "fullNameFr": "Saida Idrissi", "birthDate": "2000-05-05"%s}
                """.formatted(centerId, extra.isEmpty() ? "" : ", " + extra);
    }

    private ResultActions create(RequestPostProcessor who, String json) throws Exception {
        return mvc.perform(post("/api/beneficiaries").contentType(APPLICATION_JSON).content(json).with(who));
    }

    // ------------------------------------------------------------------ périmètre

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/beneficiaries")).andExpect(status().isUnauthorized());
    }

    @Test
    void listIsScopedByProfile() throws Exception {
        mvc.perform(get("/api/beneficiaries").with(admin())).andExpect(jsonPath("$.totalElements").value(20));
        mvc.perform(get("/api/beneficiaries").with(regional("06"))).andExpect(jsonPath("$.totalElements").value(19));
        mvc.perform(get("/api/beneficiaries").with(regional("04"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/beneficiaries").with(provincial("0601"))).andExpect(jsonPath("$.totalElements").value(14));
        mvc.perform(get("/api/beneficiaries").with(provincial("0602"))).andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void scopeCannotBeWidenedWithFilters() throws Exception {
        mvc.perform(get("/api/beneficiaries").param("centerId", String.valueOf(centerId("RAB-01"))).with(provincial("0601")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/beneficiaries").param("regionId", String.valueOf(regionId("04"))).with(regional("06")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailOutsideScopeIsNotFound() throws Exception {
        long rabat = beneficiaryId("2025-0401");
        mvc.perform(get("/api/beneficiaries/" + rabat).with(provincial("0601"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/beneficiaries/" + rabat).with(regional("06"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/beneficiaries/" + rabat).with(regional("04"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ filtres, tri, pagination

    @Test
    void listFilters() throws Exception {
        mvc.perform(get("/api/beneficiaries").param("filiereId", String.valueOf(filiereId("SEW"))).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(8));
        mvc.perform(get("/api/beneficiaries").param("status", "DEPARTED").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/beneficiaries").param("status", "ABSENT").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/beneficiaries").param("category", "MIGRANT").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/beneficiaries").param("category", "REMAINING").param("gender", "M").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(3)); // 8, 13, 15 (le 20 est « autre »)
        mvc.perform(get("/api/beneficiaries").param("vulnerable", "true").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(4)); // 3, 5, 13, 20
    }

    @Test
    void listSearch() throws Exception {
        mvc.perform(get("/api/beneficiaries").param("search", "تراوري").with(admin()))
                .andExpect(jsonPath("$.content[*].registrationNumber", contains("2025-0101")));
        mvc.perform(get("/api/beneficiaries").param("search", "Mansouri").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/beneficiaries").param("search", "AB123456").with(admin()))  // numéro de pièce d'identité
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/beneficiaries").param("search", "%").with(admin()))          // joker neutralisé
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void enrollmentColumnsFollowTheRequestedSeason() throws Exception {
        mvc.perform(get("/api/beneficiaries").param("sort", "rank").with(provincial("0602")))
                .andExpect(jsonPath("$.content[0].status").value("ABSENT"))
                .andExpect(jsonPath("$.content[0].filiereNameAr").value("خياطة وتفصيل عصري"));
        long previous = jdbc.queryForObject("SELECT id FROM training_season WHERE label = '2024-2025'", Long.class);
        mvc.perform(get("/api/beneficiaries").param("seasonId", String.valueOf(previous)).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.content[0].status").doesNotExist());
        mvc.perform(get("/api/beneficiaries").param("seasonId", String.valueOf(previous))
                        .param("withoutEnrollment", "true").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(20));
    }

    @Test
    void sortingAndPaging() throws Exception {
        mvc.perform(get("/api/beneficiaries").param("sort", "nameAr,asc").param("size", "3").with(admin()))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalPages").value(7))
                .andExpect(jsonPath("$.totalElements").value(20));
        mvc.perform(get("/api/beneficiaries").param("sort", "birthDate,desc").param("size", "1").with(admin()))
                .andExpect(jsonPath("$.content[0].registrationNumber").value("2025-0205")); // né en 2007
        mvc.perform(get("/api/beneficiaries").param("size", "5000").with(admin()))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/beneficiaries").param("sort", "password;DROP TABLE beneficiary").with(admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_sort"));
    }

    // ------------------------------------------------------------------ fiche

    @Test
    void detailShowsAgeCategoryAndEnrollments() throws Exception {
        long awa = beneficiaryId("2025-0101");
        mvc.perform(get("/api/beneficiaries/" + awa).with(provincial("0601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MIGRANT"))
                .andExpect(jsonPath("$.vulnerable").value(true))   // précarité + migrante : comptée « migrant »
                .andExpect(jsonPath("$.migrationStatus").value("REFUGEE"))
                .andExpect(jsonPath("$.age").isNumber())
                .andExpect(jsonPath("$.enrollments", hasSize(1)))
                .andExpect(jsonPath("$.enrollments[0].filiereCode").value("SEW"))
                .andExpect(jsonPath("$.enrollments[0].status").value("GRADUATED"))
                .andExpect(jsonPath("$.enrollments[0].sessions[0].type").value("NORMAL"))
                .andExpect(jsonPath("$.enrollments[0].diploma.serialNumber").value("CEF-2026-000004"));
    }

    @Test
    void javaAndDatabaseAgreeOnTheStatisticalCategory() throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, stat_category FROM beneficiary");
        assertThat(rows).hasSize(20);
        for (Map<String, Object> row : rows) {
            mvc.perform(get("/api/beneficiaries/" + row.get("id")).with(admin()))
                    .andExpect(jsonPath("$.category").value(row.get("stat_category")));
        }
    }

    // ------------------------------------------------------------------ création

    @Test
    void provincialCreatesWithGeneratedRankAndRegistrationNumber() throws Exception {
        create(provincial("0601"), body(centerId("CAS-01"), ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rankNumber").value(7))
                .andExpect(jsonPath("$.registrationNumber").value("2025-0007"))
                .andExpect(jsonPath("$.category").value("REMAINING"))
                .andExpect(jsonPath("$.enrollments", hasSize(0)))
                .andExpect(jsonPath("$.createdBy").value("provincial-0601"));
        create(provincial("0601"), body(centerId("CAS-01"), ""))
                .andExpect(jsonPath("$.rankNumber").value(8)).andExpect(jsonPath("$.registrationNumber").value("2025-0008"));
    }

    @Test
    void creationCanEnrollImmediately() throws Exception {
        create(provincial("0601"), body(centerId("CAS-01"),
                "\"enrollment\": {\"filiereId\": %d, \"startDate\": \"2025-10-20\"}".formatted(filiereId("SEW"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollments", hasSize(1)))
                .andExpect(jsonPath("$.enrollments[0].status").value("ENROLLED"))
                .andExpect(jsonPath("$.enrollments[0].seasonLabel").value("2025-2026"));
    }

    @Test
    void creationRespectsTheProfile() throws Exception {
        create(provincial("0601"), body(centerId("MOH-01"), "")).andExpect(status().isNotFound());  // autre province
        create(regional("06"), body(centerId("CAS-01"), "")).andExpect(status().isForbidden());     // lecture seule
        create(admin(), body(centerId("MOH-01"), "")).andExpect(status().isCreated());
    }

    @Test
    void severalSocialSituationsAreCountedInOneCategory() throws Exception {
        create(provincial("0601"), body(centerId("CAS-01"), """
                "vulnerable": true, "disabilityType": "MOTOR", "migrationStatus": "ASYLUM_SEEKER",
                "originCountry": "سوريا", "otherSocialSituation": "أم عازبة"
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("MIGRANT"))   // priorité : migrant > handicap > autre
                .andExpect(jsonPath("$.disabilityType").value("MOTOR"))
                .andExpect(jsonPath("$.originCountry").value("سوريا"));
        create(provincial("0601"), body(centerId("CAS-01"), "\"disabilityType\": \"VISUAL\", \"otherSocialSituation\": \"يتيمة\""))
                .andExpect(jsonPath("$.category").value("DISABLED"));
        create(provincial("0601"), body(centerId("CAS-01"), "\"vulnerable\": true"))
                .andExpect(jsonPath("$.category").value("REMAINING")); // précarité seule : « باقي المستفيدين »
    }

    @Test
    void otherValuesRequireASpecification() throws Exception {
        long c = centerId("CAS-01");
        create(provincial("0601"), body(c, "\"disabilityType\": \"OTHER\""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("beneficiary.disability_text_required"));
        create(provincial("0601"), body(c, "\"migrationStatus\": \"OTHER\""))
                .andExpect(jsonPath("$.code").value("beneficiary.migration_text_required"));
        create(provincial("0601"), body(c, "\"familyStatus\": \"OTHER\""))
                .andExpect(jsonPath("$.code").value("beneficiary.family_status_text_required"));
        create(provincial("0601"), body(c, "\"disabilityType\": \"OTHER\", \"disabilityOtherText\": \"متلازمة نادرة\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.disabilityOtherText").value("متلازمة نادرة"));
    }

    @Test
    void nonApplicableDetailsAreCleared() throws Exception {
        create(provincial("0601"), body(centerId("CAS-01"), """
                "disabilityType": "MOTOR", "disabilityOtherText": "x", "originCountry": "مالي", "familyStatus": "SINGLE",
                "familyStatusOtherText": "y"
                """))
                .andExpect(jsonPath("$.disabilityOtherText").doesNotExist())
                .andExpect(jsonPath("$.originCountry").doesNotExist())       // pas de migration : pas de pays d'origine
                .andExpect(jsonPath("$.familyStatusOtherText").doesNotExist());
    }

    @Test
    void validationErrors() throws Exception {
        create(provincial("0601"), "{\"centerId\": " + centerId("CAS-01") + ", \"birthDate\": \"2999-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullNameAr").exists())
                .andExpect(jsonPath("$.errors.gender").exists())
                .andExpect(jsonPath("$.errors.registrationDate").exists())
                .andExpect(jsonPath("$.errors.birthDate").exists());
    }

    @Test
    void rankAndRegistrationNumberAreUniquePerCenter() throws Exception {
        long c = centerId("CAS-01");
        create(provincial("0601"), body(c, "\"rankNumber\": 1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("beneficiary.rank_exists"));
        create(provincial("0601"), body(c, "\"registrationNumber\": \"2025-0001\"")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("beneficiary.registration_number_exists"));
        // le même numéro est libre dans un autre centre
        create(provincial("0601"), body(centerId("CAS-02"), "\"registrationNumber\": \"2025-0001\", \"rankNumber\": 50"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ modification / suppression

    @Test
    void updateAndCenterImmutability() throws Exception {
        long id = beneficiaryId("2025-0001");
        long cas01 = centerId("CAS-01");
        mvc.perform(put("/api/beneficiaries/" + id).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(cas01, "\"phone\": \"0612345678\", \"educationLevel\": \"UNIVERSITY\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullNameAr").value("سعيدة الإدريسي"))
                .andExpect(jsonPath("$.phone").value("0612345678"))
                .andExpect(jsonPath("$.rankNumber").value(1))          // inchangé
                .andExpect(jsonPath("$.registrationNumber").value("2025-0001"))
                .andExpect(jsonPath("$.updatedBy").value("provincial-0601"));
        mvc.perform(put("/api/beneficiaries/" + id).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(centerId("CAS-02"), "")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("beneficiary.center_immutable"));
    }

    @Test
    void updateRespectsTheProfile() throws Exception {
        long id = beneficiaryId("2025-0001");
        String json = body(centerId("CAS-01"), "");
        mvc.perform(put("/api/beneficiaries/" + id).contentType(APPLICATION_JSON).content(json).with(regional("06")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/beneficiaries/" + id).contentType(APPLICATION_JSON).content(json).with(provincial("0602")))
                .andExpect(status().isNotFound());
    }

    @Test
    void beneficiaryWithResultsCannotBeDeleted() throws Exception {
        mvc.perform(delete("/api/beneficiaries/" + beneficiaryId("2025-0001")).with(provincial("0601")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("beneficiary.has_results"));
    }

    @Test
    void beneficiaryWithoutResultsIsDeletedWithItsEnrollments() throws Exception {
        long id = beneficiaryId("2025-0202"); // inscrit, sans résultat
        mvc.perform(delete("/api/beneficiaries/" + id).with(provincial("0602"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/beneficiaries/" + id).with(admin())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enrollment WHERE beneficiary_id = ?", Long.class, id)).isZero();
    }

    // ------------------------------------------------------------------ audit

    @Test
    void everyChangeIsAuditedWithWhoWhenWhat() throws Exception {
        String created = create(provincial("0601"), body(centerId("CAS-01"), "\"phone\": \"0600000000\""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = idOf(created);
        mvc.perform(put("/api/beneficiaries/" + id).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(centerId("CAS-01"), "\"phone\": \"0611111111\"").replace("سعيدة الإدريسي", "سعيدة العلوي")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/beneficiaries/" + id + "/history").with(provincial("0601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].username").value("provincial-0601"))
                .andExpect(jsonPath("$[0].occurredAt").exists())
                .andExpect(jsonPath("$[0].changes.phone.old").value("0600000000"))
                .andExpect(jsonPath("$[0].changes.phone.new").value("0611111111"))
                .andExpect(jsonPath("$[0].changes.fullNameAr.new").value("سعيدة العلوي"))
                .andExpect(jsonPath("$[0].changes.gender").doesNotExist())   // seules les différences sont tracées
                .andExpect(jsonPath("$[1].action").value("CREATE"))
                .andExpect(jsonPath("$[1].changes.fullNameAr.new").value("سعيدة الإدريسي"));

        // l'historique suit le périmètre
        mvc.perform(get("/api/beneficiaries/" + id + "/history").with(provincial("0602"))).andExpect(status().isNotFound());
        // une modification sans changement ne laisse pas de trace
        mvc.perform(put("/api/beneficiaries/" + id).contentType(APPLICATION_JSON).with(provincial("0601"))
                .content(body(centerId("CAS-01"), "\"phone\": \"0611111111\"").replace("سعيدة الإدريسي", "سعيدة العلوي")));
        mvc.perform(get("/api/beneficiaries/" + id + "/history").with(provincial("0601"))).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void deletionIsAudited() throws Exception {
        long id = beneficiaryId("2025-0202");
        mvc.perform(delete("/api/beneficiaries/" + id).with(provincial("0602"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/audit").param("entityType", "BENEFICIARY").param("entityId", String.valueOf(id)).with(admin()))
                .andExpect(jsonPath("$[0].action").value("DELETE"))
                .andExpect(jsonPath("$[0].changes.fullNameAr.old").exists());
        // le journal global est réservé à l'administrateur
        mvc.perform(get("/api/audit").with(provincial("0602"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").with(regional("06"))).andExpect(status().isForbidden());
    }
}
