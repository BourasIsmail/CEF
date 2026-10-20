package ma.entraidenationale.cef.hr;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ma.entraidenationale.cef.AbstractApiIT;

/** Ressources humaines : périmètre, droits, règles par صفة / وضعية إدارية, mutations, enseignement, prestations, audit. */
class HrApiIT extends AbstractApiIT {

    private static final String PEDAGOGICAL_BASE = """
            "employeeType": "PEDAGOGICAL", "administrativeStatus": "ASSOCIATION", "fullNameAr": "سعاد المراكشي",
            "fullNameFr": "Souad Marrakchi", "gender": "F", "birthDate": "1992-02-02\"""";

    private String body(Long centerId, String extra) {
        return "{" + (centerId == null ? "" : "\"centerId\": " + centerId + ", ") + PEDAGOGICAL_BASE.stripTrailing()
                + (extra.isEmpty() ? "" : ", " + extra) + "}";
    }

    private ResultActions create(RequestPostProcessor who, String json) throws Exception {
        return mvc.perform(post("/api/employees").contentType(APPLICATION_JSON).content(json).with(who));
    }

    private long employeeId(String fullNameAr) {
        return jdbc.queryForObject("SELECT id FROM employee WHERE full_name_ar = ?", Long.class, fullNameAr);
    }

    private long seasonId() {
        return jdbc.queryForObject("SELECT id FROM training_season WHERE label = '2025-2026'", Long.class);
    }

    // ------------------------------------------------------------------ périmètre et droits

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void listIsScopedByProfile() throws Exception {
        mvc.perform(get("/api/employees").with(admin())).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/employees").with(regional("06"))).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/employees").with(regional("04"))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/employees").with(provincial("0601"))).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/employees").with(provincial("0602"))).andExpect(jsonPath("$.totalElements").value(0));
        // le cadre provincial (sans centre) reste visible de sa province même avec un filtre de centre ailleurs
        mvc.perform(get("/api/employees").param("centerId", String.valueOf(centerId("RAB-01"))).with(provincial("0601")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailOutsideScopeIsNotFound() throws Exception {
        long id = employeeId("نادية الشرقاوي");
        mvc.perform(get("/api/employees/" + id).with(provincial("0602"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/employees/" + id).with(regional("04"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/employees/" + id).with(regional("06"))).andExpect(status().isOk());
    }

    @Test
    void regionalIsReadOnlyAndProvincialManagesOnlyOwnProvince() throws Exception {
        create(regional("06"), body(centerId("CAS-01"), "")).andExpect(status().isForbidden());
        // un provincial d'une autre province ne voit même pas le centre : refus
        create(provincial("0602"), body(centerId("CAS-01"), "")).andExpect(status().is4xxClientError());
        create(provincial("0601"), body(centerId("CAS-01"), "")).andExpect(status().isCreated());
        mvc.perform(put("/api/employees/" + employeeId("نادية الشرقاوي")).with(regional("06"))
                .contentType(APPLICATION_JSON).content(body(centerId("CAS-01"), ""))).andExpect(status().isForbidden());
    }

    @Test
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        mvcWithoutCsrf.perform(post("/api/employees").contentType(APPLICATION_JSON)
                .content(body(centerId("CAS-01"), "")).with(admin())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ règles de la fiche

    @Test
    void createSetsProvinceFromCenterAndOpensFirstAssignment() throws Exception {
        create(provincial("0601"), body(centerId("CAS-01"), "\"hireDate\": \"2024-09-01\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provincialDirectorateId").value(provinceId("0601")))
                .andExpect(jsonPath("$.activityStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignments", hasSize(1)))
                .andExpect(jsonPath("$.assignments[0].fromDate").value("2024-09-01"))
                .andExpect(jsonPath("$.assignments[0].toDate").doesNotExist())
                .andExpect(jsonPath("$.createdBy").value("provincial-0601"));
    }

    @Test
    void centerRuleDependsOnEmployeeType() throws Exception {
        create(provincial("0601"), body(null, "")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("employee.center_required"));
        // le coordonnateur n'a pas de centre ; la province est celle du compte provincial
        create(provincial("0601"), body(null, "").replace("\"PEDAGOGICAL\"", "\"PEDAGOGICAL_COORDINATOR\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.centerId").doesNotExist());
        create(provincial("0601"), body(centerId("CAS-01"), "").replace("\"PEDAGOGICAL\"", "\"PEDAGOGICAL_COORDINATOR\""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.coordinator_no_center"));
        // administratif : centre facultatif ; l'administrateur doit alors indiquer la province
        String admin = body(null, "").replace("\"PEDAGOGICAL\"", "\"ADMINISTRATIVE\"");
        create(admin(), admin).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("employee.province_required"));
        create(admin(), body(null, "\"provincialDirectorateId\": " + provinceId("0601")).replace("\"PEDAGOGICAL\"", "\"ADMINISTRATIVE\""))
                .andExpect(status().isCreated());
    }

    @Test
    void administrativeStatusDrivesRequiredFields() throws Exception {
        long c = centerId("CAS-01");
        create(provincial("0601"), body(c, "").replace("ASSOCIATION", "OTHER_SECTOR"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.other_sector_required"));
        create(provincial("0601"), body(c, "\"otherSectorText\": \"وزارة التربية الوطنية\"").replace("ASSOCIATION", "OTHER_SECTOR"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.otherSectorText").value("وزارة التربية الوطنية"));
        create(provincial("0601"), body(c, "").replace("ASSOCIATION", "FOREIGN_VOLUNTEER"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.delegating_body_required"));
        create(provincial("0601"), body(c, "\"delegatingBody\": \"JICA\"").replace("ASSOCIATION", "FOREIGN_VOLUNTEER"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.delegatingBody").value("JICA"));
        create(provincial("0601"), body(c, "").replace("ASSOCIATION", "ENTRAIDE_NATIONALE"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.rank_required"));
        create(provincial("0601"), body(c, "\"rankNumber\": \"M-99001\", \"enGrade\": \"مكوّن\", \"enScale\": \"السلم 9\"")
                .replace("ASSOCIATION", "ENTRAIDE_NATIONALE"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rankNumber").value("M-99001"));
        create(provincial("0601"), body(c, "\"rankNumber\": \"M-99001\"").replace("ASSOCIATION", "ENTRAIDE_NATIONALE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("employee.rank_exists"));
    }

    @Test
    void enSpecificFieldsAreIgnoredForOtherStatuses() throws Exception {
        create(provincial("0601"), body(centerId("CAS-01"), "\"rankNumber\": \"M-1\", \"enGrade\": \"x\", \"enScale\": \"y\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rankNumber").doesNotExist())
                .andExpect(jsonPath("$.enGrade").doesNotExist())
                .andExpect(jsonPath("$.enScale").doesNotExist());
    }

    @Test
    void updateKeepsCenterAndProvince() throws Exception {
        long id = employeeId("نادية الشرقاوي");
        String ok = """
                {"centerId": %d, "employeeType": "PEDAGOGICAL", "administrativeStatus": "ENTRAIDE_NATIONALE",
                 "rankNumber": "M-10482", "fullNameAr": "نادية الشرقاوي", "gender": "F", "phone": "0600000000"}
                """.formatted(centerId("CAS-01"));
        mvc.perform(put("/api/employees/" + id).with(provincial("0601")).contentType(APPLICATION_JSON).content(ok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("0600000000"));
        mvc.perform(put("/api/employees/" + id).with(provincial("0601")).contentType(APPLICATION_JSON)
                .content(ok.replace(String.valueOf(centerId("CAS-01")), String.valueOf(centerId("CAS-02")))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.use_transfer"));
    }

    @Test
    void validationErrorsAreListed() throws Exception {
        create(admin(), "{\"employeeType\": \"PEDAGOGICAL\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullNameAr").exists()).andExpect(jsonPath("$.errors.gender").exists());
    }

    // ------------------------------------------------------------------ affectations

    @Test
    void transferClosesPreviousAssignmentAndMovesEmployee() throws Exception {
        long id = employeeId("حفصة الودغيري"); // CAS-01, aucune affectation dans le seed
        long cas2 = centerId("CAS-02");
        String json = "{\"fromDate\": \"2025-11-01\", \"centerId\": " + cas2 + ", \"positionTitle\": \"مكوّنة\", \"decisionReference\": \"قرار 2025/9\"}";
        mvc.perform(post("/api/employees/" + id + "/transfers").with(provincial("0601")).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].fromDate").value("2025-11-01"))
                .andExpect(jsonPath("$[0].centerId").value(cas2));
        mvc.perform(get("/api/employees/" + id).with(admin()))
                .andExpect(jsonPath("$.centerId").value(cas2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_assignment WHERE employee_id = ? AND to_date IS NULL", Long.class, id))
                .isLessThanOrEqualTo(1L);
    }

    @Test
    void transferChainKeepsHistoryContiguous() throws Exception {
        long id = employeeId("نادية الشرقاوي"); // dernière affectation ouverte depuis 2021-09-01 au CAS-01
        long cas3 = centerId("CAS-03");
        mvc.perform(post("/api/employees/" + id + "/transfers").with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"fromDate\": \"2026-01-01\", \"centerId\": " + cas3 + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].centerId").value(cas3))
                .andExpect(jsonPath("$[1].toDate").value("2025-12-31"));
    }

    @Test
    void transferRules() throws Exception {
        long id = employeeId("نادية الشرقاوي");
        RequestPostProcessor who = provincial("0601");
        // même centre
        mvc.perform(post("/api/employees/" + id + "/transfers").with(who).contentType(APPLICATION_JSON)
                .content("{\"fromDate\": \"2026-01-01\", \"centerId\": " + centerId("CAS-01") + "}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.transfer_same_center"));
        // date antérieure à la dernière affectation (2021-09-01)
        mvc.perform(post("/api/employees/" + id + "/transfers").with(who).contentType(APPLICATION_JSON)
                .content("{\"fromDate\": \"2021-09-01\", \"centerId\": " + centerId("CAS-02") + "}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.transfer_date"));
        // centre d'une autre province (l'admin passe le contrôle de droits, la règle métier s'applique)
        mvc.perform(post("/api/employees/" + id + "/transfers").with(admin()).contentType(APPLICATION_JSON)
                .content("{\"fromDate\": \"2026-01-01\", \"centerId\": " + centerId("MOH-01") + "}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.transfer_other_province"));
        // un pédagogique ne peut pas devenir « sans centre »
        mvc.perform(post("/api/employees/" + id + "/transfers").with(who).contentType(APPLICATION_JSON)
                .content("{\"fromDate\": \"2026-01-01\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.center_required"));
    }

    // ------------------------------------------------------------------ هيئة التكوين

    @Test
    void teachingRules() throws Exception {
        long nadia = employeeId("نادية الشرقاوي"); // CAS-01 : SEW, HAIR, PAST
        long season = seasonId();
        long pastModule = jdbc.queryForObject("SELECT m.id FROM module m JOIN filiere f ON f.id = m.filiere_id WHERE f.code = 'PAST' LIMIT 1", Long.class);
        long sewModule = jdbc.queryForObject("SELECT m.id FROM module m JOIN filiere f ON f.id = m.filiere_id WHERE f.code = 'SEW' AND m.position = 5", Long.class);
        RequestPostProcessor who = provincial("0601");
        String url = "/api/employees/" + nadia + "/teachings";

        mvc.perform(post(url).with(who).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d, \"moduleId\": %d}".formatted(season, filiereId("SEW"), sewModule)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.moduleNameAr").exists());
        // doublon
        mvc.perform(post(url).with(who).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d, \"moduleId\": %d}".formatted(season, filiereId("SEW"), sewModule)))
                .andExpect(status().isConflict());
        // module d'une autre filière
        mvc.perform(post(url).with(who).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d, \"moduleId\": %d}".formatted(season, filiereId("SEW"), pastModule)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("teaching.module_not_in_filiere"));
        // toute la filière alors que des modules sont déjà affectés
        mvc.perform(post(url).with(who).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(season, filiereId("SEW"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("teaching.modules_exist"));
        // filière non proposée par le centre (CAS-02 n'a pas PAST)
        long ctr2 = employeeId("كريم بنجلون");
        mvc.perform(post("/api/employees/" + ctr2 + "/teachings").with(who).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(season, filiereId("PAST"))))
                .andExpect(status().isUnprocessableContent());
        // un directeur n'enseigne pas
        mvc.perform(post("/api/employees/" + ctr2 + "/teachings").with(who).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(season, filiereId("SEW"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.not_a_trainer"));
    }

    @Test
    void teachingCanBeAddedWholeFiliereThenRemoved() throws Exception {
        long hafsa = employeeId("حفصة الودغيري"); // enseigne déjà toute la filière PAST
        long season = seasonId();
        String url = "/api/employees/" + hafsa + "/teachings";
        mvc.perform(post(url).with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(season, filiereId("PAST"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("teaching.already_assigned"));
        long tid = jdbc.queryForObject("SELECT id FROM employee_teaching WHERE employee_id = ?", Long.class, hafsa);
        mvc.perform(delete(url + "/" + tid).with(provincial("0601"))).andExpect(status().isNoContent());
        mvc.perform(post(url).with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(season, filiereId("HAIR"))))
                .andExpect(status().isCreated());
        mvc.perform(delete(url + "/" + tid).with(provincial("0601"))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ المكونون المستضافون

    @Test
    void guestContractLifecycle() throws Exception {
        long rachid = employeeId("رشيد الأزرق");
        long season = seasonId();
        String url = "/api/employees/" + rachid + "/guest-contracts";
        RequestPostProcessor who = provincial("0601");

        mvc.perform(get("/api/employees/" + rachid).with(who))
                .andExpect(jsonPath("$.guestContracts", hasSize(1)))
                .andExpect(jsonPath("$.guestContracts[0].estimatedAnnualCostMad").value(43200.00));

        String json = "{\"seasonId\": %d, \"filiereId\": %d, \"diplomaYear\": 2016, \"hourlyRateMad\": 100.5, \"monthsPerYear\": 8, \"hoursPerMonth\": 30, \"beneficiariesCount\": 10}"
                .formatted(season, filiereId("PAST"));
        String created = mvc.perform(post(url).with(who).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estimatedAnnualCostMad").value(24120.00))
                .andReturn().getResponse().getContentAsString();
        long contractId = idOf(created);
        mvc.perform(post(url).with(who).contentType(APPLICATION_JSON).content(json)).andExpect(status().isConflict());

        mvc.perform(put(url + "/" + contractId).with(who).contentType(APPLICATION_JSON)
                .content(json.replace("100.5", "110").replace("\"monthsPerYear\": 8", "\"monthsPerYear\": 10")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estimatedAnnualCostMad").value(33000.00));
        // on ne peut pas déplacer la prestation sur une combinaison déjà existante
        mvc.perform(put(url + "/" + contractId).with(who).contentType(APPLICATION_JSON)
                .content(json.replace(String.valueOf(filiereId("PAST")), String.valueOf(filiereId("HAIR")))))
                .andExpect(status().isConflict());
        // SEW n'est pas proposée au CAS-03
        mvc.perform(post(url).with(who).contentType(APPLICATION_JSON)
                .content(json.replace(String.valueOf(filiereId("PAST")), String.valueOf(filiereId("SEW")))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("filiere.not_at_center"));

        mvc.perform(delete(url + "/" + contractId).with(who)).andExpect(status().isNoContent());
        mvc.perform(delete(url + "/" + contractId).with(who)).andExpect(status().isNotFound());
    }

    @Test
    void guestContractRequiresGuestTrainer() throws Exception {
        long nadia = employeeId("نادية الشرقاوي");
        mvc.perform(post("/api/employees/" + nadia + "/guest-contracts").with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(seasonId(), filiereId("SEW"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("employee.not_guest_trainer"));
    }

    @Test
    void contractValuesAreValidated() throws Exception {
        long rachid = employeeId("رشيد الأزرق");
        mvc.perform(post("/api/employees/" + rachid + "/guest-contracts").with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d, \"monthsPerYear\": 13, \"hourlyRateMad\": -1}"
                        .formatted(seasonId(), filiereId("PAST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.monthsPerYear").exists()).andExpect(jsonPath("$.errors.hourlyRateMad").exists());
    }

    @Test
    void cannotLeaveGuestTrainerTypeWhileContractsExist() throws Exception {
        long rachid = employeeId("رشيد الأزرق");
        String json = """
                {"centerId": %d, "employeeType": "PEDAGOGICAL", "administrativeStatus": "ASSOCIATION",
                 "fullNameAr": "رشيد الأزرق", "gender": "M"}""".formatted(centerId("CAS-03"));
        mvc.perform(put("/api/employees/" + rachid).with(provincial("0601")).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("employee.has_guest_contracts"));
    }

    // ------------------------------------------------------------------ liste, effectifs

    @Test
    void listFiltersSearchAndSort() throws Exception {
        mvc.perform(get("/api/employees").param("type", "PEDAGOGICAL").with(admin())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/employees").param("administrativeStatus", "ASSOCIATION").with(admin())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/employees").param("centerId", String.valueOf(centerId("CAS-01"))).with(admin())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/employees").param("withoutCenter", "true").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].employeeType").value("PEDAGOGICAL_COORDINATOR"));
        mvc.perform(get("/api/employees").param("gender", "M").with(admin())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/employees").param("search", "M-10517").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].fullNameFr").value("Karim Benjelloun"));
        mvc.perform(get("/api/employees").param("search", "%").with(admin())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/employees").param("sort", "rankNumber,desc").with(admin()))
                .andExpect(jsonPath("$.content[0].rankNumber").value("M-12755"));
        mvc.perform(get("/api/employees").param("sort", "id;DROP TABLE employee").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void listShowsGradeAndCurrentTeachingAndFiltersByFiliere() throws Exception {
        mvc.perform(get("/api/employees").param("filiereId", String.valueOf(filiereId("SEW"))).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullNameFr").value("Nadia Charkaoui"))
                .andExpect(jsonPath("$.content[0].enGrade").value("مكوّن"))
                .andExpect(jsonPath("$.content[0].enScale").value("السلم 9"))
                .andExpect(jsonPath("$.content[0].teachingFilieresFr").value("Couture et mode moderne"));
        mvc.perform(get("/api/employees").param("filiereId", String.valueOf(filiereId("PAST"))).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].fullNameAr").value("حفصة الودغيري"));
        mvc.perform(get("/api/employees").param("filiereId", String.valueOf(filiereId("HAIR"))).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staffSummaryIsComputedNotTyped() throws Exception {
        mvc.perform(get("/api/employees/summary").with(admin()))
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.male").value(2)).andExpect(jsonPath("$.female").value(3))
                .andExpect(jsonPath("$.byType.PEDAGOGICAL").value(2)).andExpect(jsonPath("$.byType.GUEST_TRAINER").value(1))
                .andExpect(jsonPath("$.byType.ADMINISTRATIVE").value(0))
                .andExpect(jsonPath("$.byAdministrativeStatus.ENTRAIDE_NATIONALE").value(3))
                .andExpect(jsonPath("$.byAdministrativeStatus.ASSOCIATION").value(2))
                .andExpect(jsonPath("$.matrix.PEDAGOGICAL.ENTRAIDE_NATIONALE").value(1))
                .andExpect(jsonPath("$.matrix.PEDAGOGICAL.ASSOCIATION").value(1))
                .andExpect(jsonPath("$.matrix.CENTER_DIRECTOR.ENTRAIDE_NATIONALE").value(1))
                .andExpect(jsonPath("$.matrix.GUEST_TRAINER.ASSOCIATION").value(1))
                .andExpect(jsonPath("$.matrix.ADMINISTRATIVE.OTHER_SECTOR").value(0));
        mvc.perform(get("/api/employees/summary").param("centerId", String.valueOf(centerId("CAS-01"))).with(provincial("0601")))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/employees/summary").with(provincial("0602"))).andExpect(jsonPath("$.total").value(0));
        // les retraités ne comptent plus dans les effectifs
        jdbc.update("UPDATE employee SET activity_status = 'RETIRED' WHERE full_name_ar = 'رشيد الأزرق'");
        mvc.perform(get("/api/employees/summary").with(admin())).andExpect(jsonPath("$.total").value(4));
    }

    // ------------------------------------------------------------------ suppression, audit

    @Test
    void deleteRemovesChildrenAndIsAudited() throws Exception {
        long rachid = employeeId("رشيد الأزرق");
        mvc.perform(delete("/api/employees/" + rachid).with(regional("06"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/employees/" + rachid).with(provincial("0602"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/employees/" + rachid).with(provincial("0601"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/employees/" + rachid).with(admin())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_guest_contract WHERE employee_id = ?", Long.class, rachid)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE root_type = 'EMPLOYEE' AND root_id = ? AND action = 'DELETE'",
                Long.class, rachid)).isEqualTo(1L);
    }

    @Test
    void historyCoversRecordAndChildren() throws Exception {
        String created = create(provincial("0601"), body(centerId("CAS-01"), "")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = idOf(created);
        mvc.perform(post("/api/employees/" + id + "/transfers").with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"fromDate\": \"2030-01-01\", \"centerId\": " + centerId("CAS-02") + "}")).andExpect(status().isCreated());
        mvc.perform(post("/api/employees/" + id + "/teachings").with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"seasonId\": %d, \"filiereId\": %d}".formatted(seasonId(), filiereId("SEW")))).andExpect(status().isCreated());
        mvc.perform(get("/api/employees/" + id + "/history").with(provincial("0601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entityType", hasItem("EMPLOYEE")))
                .andExpect(jsonPath("$[*].entityType", hasItem("EMPLOYEE_ASSIGNMENT")))
                .andExpect(jsonPath("$[*].entityType", hasItem("EMPLOYEE_TEACHING")));
        mvc.perform(get("/api/employees/" + id + "/history").with(provincial("0602"))).andExpect(status().isNotFound());
    }

    @Test
    void employeesExportToExcelFollowsFiltersAndScope() throws Exception {
        byte[] all = mvc.perform(get("/api/employees/export.xlsx").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("ressources-humaines-cef.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] teachers = mvc.perform(get("/api/employees/export.xlsx").param("type", "PEDAGOGICAL").with(provincial("0601")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var a = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(all));
             var b = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(teachers))) {
            org.junit.jupiter.api.Assertions.assertTrue(a.getSheetAt(0).isRightToLeft());
            org.junit.jupiter.api.Assertions.assertEquals("الرقم الترتيبي", a.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertTrue(a.getSheetAt(0).getLastRowNum() >= b.getSheetAt(0).getLastRowNum());
        }
    }
}
