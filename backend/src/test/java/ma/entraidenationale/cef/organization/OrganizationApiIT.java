package ma.entraidenationale.cef.organization;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
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

import ma.entraidenationale.cef.AbstractApiIT;

/** Organisation : périmètre de lecture, création de centres par un compte provincial, règles métier. */
class OrganizationApiIT extends AbstractApiIT {

    // ------------------------------------------------------------------ authentification

    @Test
    void anonymousIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/centers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/regions")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutCsrfTokenAreRejected() throws Exception {
        mvcWithoutCsrf.perform(post("/api/centers").contentType(APPLICATION_JSON)
                        .content(body(communeId("0601", "عين الشق"), "")).with(provincial("0601")))
                .andExpect(status().isForbidden());
        // les lectures n'ont pas besoin de jeton
        mvcWithoutCsrf.perform(get("/api/centers").with(provincial("0601"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ périmètre de lecture

    @Test
    void regionsAreFilteredByScope() throws Exception {
        mvc.perform(get("/api/regions").with(admin())).andExpect(jsonPath("$", hasSize(12)));
        mvc.perform(get("/api/regions").with(regional("06")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("06"));
        mvc.perform(get("/api/regions").with(provincial("0601")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("06"));
    }

    @Test
    void provincesAreFilteredByScope() throws Exception {
        mvc.perform(get("/api/provinces").with(admin())).andExpect(jsonPath("$", hasSize(82)));
        mvc.perform(get("/api/provinces").with(regional("06")))
                .andExpect(jsonPath("$", hasSize(16))).andExpect(jsonPath("$[*].code", hasItems("0601", "0602")));
        mvc.perform(get("/api/provinces").with(provincial("0601")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("0601"));
    }

    @Test
    void communesOfAnotherProvinceAreHidden() throws Exception {
        mvc.perform(get("/api/communes").param("provinceId", String.valueOf(provinceId("0601"))).with(provincial("0601")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].nameAr", hasItems("سيدي مومن", "عين الشق", "الحي الحسني")));
        mvc.perform(get("/api/communes").param("provinceId", String.valueOf(provinceId("0401"))).with(provincial("0601")))
                .andExpect(status().isNotFound());
    }

    @Test
    void centerListIsScoped() throws Exception {
        mvc.perform(get("/api/centers").with(admin())).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/centers").with(regional("06"))).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/centers").with(regional("04"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/centers").with(provincial("0601")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("CAS-01", "CAS-02", "CAS-03")));
        mvc.perform(get("/api/centers").with(provincial("0602")))
                .andExpect(jsonPath("$.content[*].code", contains("MOH-01")));
    }

    @Test
    void scopeCannotBeWidenedWithFilters() throws Exception {
        // un provincial qui demande une autre province ne reçoit rien : le périmètre s'applique en premier
        mvc.perform(get("/api/centers").param("provinceId", String.valueOf(provinceId("0401"))).with(provincial("0601")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void centerListFilters() throws Exception {
        mvc.perform(get("/api/centers").param("search", "سيدي").with(admin()))
                .andExpect(jsonPath("$.content[*].code", contains("CAS-01")));
        mvc.perform(get("/api/centers").param("search", "الأمل").with(admin())) // nom d'association
                .andExpect(jsonPath("$.content[*].code", contains("CAS-01")));
        mvc.perform(get("/api/centers").param("filiereId", String.valueOf(filiereId("PAST"))).with(admin()))
                .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("CAS-01", "CAS-03", "MOH-01")));
        mvc.perform(get("/api/centers").param("managementMode", "DIRECT_EN").with(admin()))
                .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("CAS-02", "RAB-01")));
        mvc.perform(get("/api/centers").param("search", "100%_").with(admin())) // jokers LIKE neutralisés
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownSortIsRejected() throws Exception {
        mvc.perform(get("/api/centers").param("sort", "passwordHash").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void centerDetailHasComputedFields() throws Exception {
        long id = centerId("CAS-01");
        mvc.perform(get("/api/centers/" + id).with(provincial("0601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAreaM2").value(400.0))
                .andExpect(jsonPath("$.spacesCount").value(8))
                .andExpect(jsonPath("$.associationsCount").value(1))
                .andExpect(jsonPath("$.filieresCount").value(3))
                .andExpect(jsonPath("$.beneficiariesCount").value(6))
                .andExpect(jsonPath("$.staffCount").value(2))
                .andExpect(jsonPath("$.associations[0].partners", hasSize(2)));
    }

    @Test
    void centerOutsideScopeIsReportedAsNotFound() throws Exception {
        mvc.perform(get("/api/centers/" + centerId("RAB-01")).with(provincial("0601"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/centers/" + centerId("MOH-01")).with(regional("04"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/centers/" + centerId("MOH-01")).with(regional("06"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ création

    private String body(long communeId, String extra) {
        return """
                {"communeId": %d, "nameAr": "مركز تجريبي", "nameFr": "Centre test", "areaType": "URBAN"%s}
                """.formatted(communeId, extra.isEmpty() ? "" : ", " + extra);
    }

    private ResultActions create(org.springframework.test.web.servlet.request.RequestPostProcessor who, String json) throws Exception {
        return mvc.perform(post("/api/centers").contentType(APPLICATION_JSON).content(json).with(who));
    }

    @Test
    void provincialCreatesInOwnProvinceWithGeneratedCode() throws Exception {
        long commune = communeId("0601", "عين الشق");
        create(provincial("0601"), body(commune, """
                "buildingType": "OTHER", "floorsCount": 4,
                "floorAreas": [{"floorNumber":1,"areaM2":220},{"floorNumber":2,"areaM2":180},{"floorNumber":3,"areaM2":150},{"floorNumber":4,"areaM2":90.5}],
                "landStatus": "OWNED_BY_EN", "managementMode": "DIRECT_EN"
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provincialDirectorateId").value(provinceId("0601")))
                .andExpect(jsonPath("$.code").value("0601-04"))
                .andExpect(jsonPath("$.totalAreaM2").value(640.5))
                .andExpect(jsonPath("$.floorAreas", hasSize(4)))
                .andExpect(jsonPath("$.createdBy").value("provincial-0601"));
    }

    @Test
    void provincialCannotUseACommuneOfAnotherProvince() throws Exception {
        create(provincial("0601"), body(communeId("0401", "الرباط"), ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("center.commune_not_in_province"));
    }

    @Test
    void provincialCannotTargetAnotherProvince() throws Exception {
        create(provincial("0601"), body(communeId("0401", "الرباط"), "\"provincialDirectorateId\": " + provinceId("0401")))
                .andExpect(status().isForbidden());
    }

    @Test
    void regionalIsReadOnly() throws Exception {
        create(regional("06"), body(communeId("0601", "عين الشق"), "")).andExpect(status().isForbidden());
        mvc.perform(put("/api/centers/" + centerId("CAS-01")).contentType(APPLICATION_JSON)
                        .content(body(communeId("0601", "سيدي مومن"), "")).with(regional("06")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/centers/" + centerId("CAS-01")).with(regional("06"))).andExpect(status().isForbidden());
    }

    @Test
    void adminMustChooseTheProvince() throws Exception {
        long commune = communeId("0601", "عين الشق");
        create(admin(), body(commune, "")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("center.province_required"));
        create(admin(), body(commune, "\"provincialDirectorateId\": " + provinceId("0601"))).andExpect(status().isCreated());
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        create(provincial("0601"), "{\"communeId\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.nameAr").exists())
                .andExpect(jsonPath("$.errors.areaType").exists());
    }

    @Test
    void duplicateCodeIsAConflict() throws Exception {
        create(provincial("0601"), body(communeId("0601", "عين الشق"), "\"code\": \"CAS-01\""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("center.code_exists"));
    }

    // ------------------------------------------------------------------ règles de bâtiment

    @Test
    void otherBuildingRequiresTheAreaOfEveryFloor() throws Exception {
        long commune = communeId("0601", "عين الشق");
        create(provincial("0601"), body(commune, """
                "buildingType": "OTHER", "floorsCount": 4,
                "floorAreas": [{"floorNumber":1,"areaM2":220},{"floorNumber":2,"areaM2":180}]
                """)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("center.floor_areas_incomplete"));
        create(provincial("0601"), body(commune, "\"buildingType\": \"OTHER\""))
                .andExpect(jsonPath("$.code").value("center.floors_count_required"));
    }

    @Test
    void fixedBuildingTypesRestrictTheFloors() throws Exception {
        long commune = communeId("0601", "عين الشق");
        create(provincial("0601"), body(commune, """
                "buildingType": "TWO_FLOORS", "floorAreas": [{"floorNumber":3,"areaM2":100}]
                """)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("center.floor_out_of_range"));
        create(provincial("0601"), body(commune, """
                "buildingType": "TWO_FLOORS", "floorAreas": [{"floorNumber":1,"areaM2":100},{"floorNumber":1,"areaM2":50}]
                """)).andExpect(jsonPath("$.code").value("center.duplicate_floor"));
        create(provincial("0601"), body(commune, "\"floorAreas\": [{\"floorNumber\":1,\"areaM2\":100}]"))
                .andExpect(jsonPath("$.code").value("center.building_type_required"));
        // surfaces facultatives pour un type fixe
        create(provincial("0601"), body(commune, "\"buildingType\": \"THREE_FLOORS\"")).andExpect(status().isCreated());
    }

    @Test
    void landOwnerIsRequiredForProvidedOrOtherSector() throws Exception {
        long commune = communeId("0601", "عين الشق");
        long stateDomain = jdbc.queryForObject("SELECT id FROM land_provider WHERE code = 'L06'", Long.class);   // أملاك الدولة
        long otherSector = jdbc.queryForObject("SELECT id FROM land_provider WHERE code = 'L13'", Long.class);   // قطاع آخر مع تحديده
        // mis à disposition : l'organisme est obligatoire ; « autre secteur » demande en plus une précision
        create(provincial("0601"), body(commune, "\"landStatus\": \"PROVIDED_TO_EN\""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.land_provider_required"));
        create(provincial("0601"), body(commune, "\"landStatus\": \"PROVIDED_TO_EN\", \"landProviderId\": 999999"))
                .andExpect(jsonPath("$.code").value("center.land_provider_unknown"));
        create(provincial("0601"), body(commune, "\"landStatus\": \"PROVIDED_TO_EN\", \"landProviderId\": %d".formatted(otherSector)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.land_owner_required"));
        create(provincial("0601"), body(commune, "\"landStatus\": \"PROVIDED_TO_EN\", \"landProviderId\": %d, \"landOwnerText\": \"وزارة الأوقاف\"".formatted(otherSector)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.landOwnerText").value("وزارة الأوقاف"));
        create(provincial("0601"), body(commune, "\"landStatus\": \"PROVIDED_TO_EN\", \"landProviderId\": %d, \"landOwnerText\": \"ignoré\"".formatted(stateDomain)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.landProviderNameAr").value("أملاك الدولة"))
                .andExpect(jsonPath("$.landOwnerText").doesNotExist());
        create(provincial("0601"), body(commune, "\"landStatus\": \"OTHER_SECTOR\""))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.land_owner_required"));
    }

    @Test
    void partnersThatNeedASpecificationRequireOne() throws Exception {
        long commune = communeId("0601", "عين الشق");
        long association = jdbc.queryForObject("SELECT id FROM association WHERE name_ar = 'جمعية النور للتنمية'", Long.class);
        long privateSector = jdbc.queryForObject("SELECT id FROM partner_type WHERE code = 'P17'", Long.class);   // القطاع الخاص (مع تحديده)
        long ruralCommune = jdbc.queryForObject("SELECT id FROM partner_type WHERE code = 'P05'", Long.class);
        create(provincial("0601"), body(commune, """
                "associations": [{"associationId": %d, "partnerTypeIds": [%d]}]""".formatted(association, privateSector)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.partner_specification_required"));
        create(provincial("0601"), body(commune, """
                "associations": [{"associationId": %d, "partnerTypeIds": [%d], "partnerSpecification": "شركة س"}]""".formatted(association, privateSector)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.associations[0].partnerSpecification").value("شركة س"));
        // sans partenaire « مع تحديده », la précision est ignorée
        create(provincial("0601"), body(commune, """
                "associations": [{"associationId": %d, "partnerTypeIds": [%d], "partnerSpecification": "ignorée"}]""".formatted(association, ruralCommune)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.associations[0].partnerSpecification").doesNotExist());
    }

    // ------------------------------------------------------------------ espaces et associations

    @Test
    void spacesRulesAndReplacement() throws Exception {
        long commune = communeId("0601", "عين الشق");
        long reception = spaceTypeId("RECEPTION"), other = spaceTypeId("OTHER"), waiting = spaceTypeId("WAITING");

        create(provincial("0601"), body(commune, "\"spaces\": [{\"spaceTypeId\": %d}]".formatted(other)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.space_specification_required"));
        create(provincial("0601"), body(commune, "\"spaces\": [{\"spaceTypeId\": %d, \"specification\": \"x\"}]".formatted(reception)))
                .andExpect(jsonPath("$.code").value("center.space_specification_forbidden"));
        create(provincial("0601"), body(commune, "\"spaces\": [{\"spaceTypeId\": %d},{\"spaceTypeId\": %d}]".formatted(reception, reception)))
                .andExpect(jsonPath("$.code").value("center.duplicate_space"));

        // mise à jour différentielle : on remplace RECEPTION + OTHER par WAITING + OTHER (précision modifiée)
        String created = create(provincial("0601"), body(commune,
                "\"spaces\": [{\"spaceTypeId\": %d},{\"spaceTypeId\": %d, \"specification\": \"salle de sport\"}]".formatted(reception, other)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.spacesCount").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        mvc.perform(put("/api/centers/" + id).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(commune, "\"spaces\": [{\"spaceTypeId\": %d},{\"spaceTypeId\": %d, \"specification\": \"bibliothèque\"}]"
                                .formatted(waiting, other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spacesCount").value(2))
                .andExpect(jsonPath("$.spaces[*].code", contains("WAITING", "OTHER")))
                .andExpect(jsonPath("$.spaces[1].specification").value("bibliothèque"));
    }

    @Test
    void associationLinksAreValidatedAndReplaced() throws Exception {
        long commune = communeId("0601", "عين الشق");
        long association = jdbc.queryForObject("SELECT id FROM association WHERE name_ar = 'جمعية النور للتنمية'", Long.class);
        long partnerLocal = jdbc.queryForObject("SELECT id FROM partner_type WHERE code = 'P05'", Long.class);

        create(provincial("0601"), body(commune, """
                "associations": [{"associationId": %d, "measureTaken": "OTHER"}]""".formatted(association)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.measure_text_required"));
        create(provincial("0601"), body(commune, """
                "associations": [{"associationId": 999999}]"""))
                .andExpect(jsonPath("$.code").value("center.association_unknown"));
        create(provincial("0601"), body(commune, """
                "associations": [{"associationId": %d, "partnerTypeIds": [999999]}]""".formatted(association)))
                .andExpect(jsonPath("$.code").value("center.partner_type_unknown"));

        create(provincial("0601"), body(commune, """
                "managementMode": "ASSOCIATION_FULL",
                "associations": [{"associationId": %d, "agreementSubject": "تسيير", "agreementDate": "2024-02-01",
                                  "agreementReference": "2024/9", "partnershipEvaluation": "GOOD",
                                  "measureTaken": "CONTINUE", "partnerTypeIds": [%d]}]""".formatted(association, partnerLocal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.associationsCount").value(1))
                .andExpect(jsonPath("$.associations[0].partnershipEvaluation").value("GOOD"))
                .andExpect(jsonPath("$.associations[0].partners[0].code").value("P05"));
    }

    @Test
    void associationLinkOfAnotherCenterIsRejected() throws Exception {
        long cas01 = centerId("CAS-01");
        long foreignLink = jdbc.queryForObject("SELECT id FROM center_association WHERE center_id = ?", Long.class, centerId("CAS-03"));
        long association = jdbc.queryForObject("SELECT id FROM association LIMIT 1", Long.class);
        mvc.perform(put("/api/centers/" + cas01).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(communeId("0601", "سيدي مومن"), "\"associations\": [{\"id\": %d, \"associationId\": %d}]"
                                .formatted(foreignLink, association))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("center.association_link_not_found"));
    }

    // ------------------------------------------------------------------ modification / suppression

    @Test
    void provincialCannotModifyACenterOfAnotherProvince() throws Exception {
        mvc.perform(put("/api/centers/" + centerId("MOH-01")).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(communeId("0602", "المحمدية"), "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void centerProvinceIsImmutable() throws Exception {
        mvc.perform(put("/api/centers/" + centerId("CAS-01")).contentType(APPLICATION_JSON).with(admin())
                        .content(body(communeId("0601", "سيدي مومن"), "\"provincialDirectorateId\": " + provinceId("0602"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("center.province_immutable"));
    }

    @Test
    void updateKeepsTheCodeForProvincialButAdminCanChangeIt() throws Exception {
        long id = centerId("CAS-02");
        long commune = communeId("0601", "عين الشق");
        mvc.perform(put("/api/centers/" + id).contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content(body(commune, "\"code\": \"HACK-1\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("CAS-02"))
                .andExpect(jsonPath("$.updatedBy").value("provincial-0601"));
        mvc.perform(put("/api/centers/" + id).contentType(APPLICATION_JSON).with(admin())
                        .content(body(commune, "\"code\": \"CAS-02B\"")))
                .andExpect(jsonPath("$.code").value("CAS-02B"));
    }

    @Test
    void usedCenterCannotBeDeletedButNewOneCan() throws Exception {
        mvc.perform(delete("/api/centers/" + centerId("CAS-01")).with(provincial("0601")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("center.in_use"));

        String created = create(provincial("0601"), body(communeId("0601", "عين الشق"), "\"buildingType\": \"ONE_FLOOR\""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        mvc.perform(delete("/api/centers/" + id).with(provincial("0601"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/centers/" + id).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void centerCanBeDeactivated() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/centers/" + centerId("CAS-01") + "/active").param("value", "false").with(provincial("0601")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/centers").param("active", "true").with(provincial("0601")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ------------------------------------------------------------------ référentiels et associations

    @Test
    void referenceListsAreAvailable() throws Exception {
        mvc.perform(get("/api/space-types").with(provincial("0601")))
                .andExpect(jsonPath("$", hasSize(13))).andExpect(jsonPath("$[12].requiresSpecification").value(true));
        mvc.perform(get("/api/partner-types").with(provincial("0601")))
                .andExpect(jsonPath("$", hasSize(23))).andExpect(jsonPath("$[16].requiresSpecification").value(true));
        mvc.perform(get("/api/land-providers").with(provincial("0601")))
                .andExpect(jsonPath("$", hasSize(13))).andExpect(jsonPath("$[12].requiresSpecification").value(true));
    }

    @Test
    void associationDirectoryRules() throws Exception {
        mvc.perform(post("/api/associations").contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content("{\"nameAr\": \"جمعية جديدة\", \"nameFr\": \"Nouvelle association\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/associations").contentType(APPLICATION_JSON).with(provincial("0601"))
                        .content("{\"nameAr\": \"جمعية الأمل للتنمية\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/associations").contentType(APPLICATION_JSON).with(regional("06"))
                        .content("{\"nameAr\": \"جمعية ثالثة\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/associations").param("q", "النور").with(regional("06")))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void onlyAdminManagesProvincesAndCommunes() throws Exception {
        String province = "{\"regionId\": %d, \"code\": \"9901\", \"nameAr\": \"المديرية الإقليمية لبرشيد\", \"nameFr\": \"Direction provinciale de Berrechid\"}"
                .formatted(regionId("06"));
        mvc.perform(post("/api/provinces").contentType(APPLICATION_JSON).content(province).with(provincial("0601")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/provinces").contentType(APPLICATION_JSON).content(province).with(admin()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/provinces").contentType(APPLICATION_JSON).content(province).with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("province.code_exists"));

        String commune = "{\"provinceId\": %d, \"nameAr\": \"سيدي مومن\", \"nameFr\": \"Sidi Moumen\"}".formatted(provinceId("0601"));
        mvc.perform(post("/api/communes").contentType(APPLICATION_JSON).content(commune).with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("commune.exists"));
    }

    @Test
    void centersExportToExcelHonoursFiltersAndScope() throws Exception {
        byte[] all = mvc.perform(get("/api/centers/export.xlsx").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("centres-cef.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] scoped = mvc.perform(get("/api/centers/export.xlsx").param("lang", "fr").with(provincial("0601")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var a = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(all));
             var b = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(scoped))) {
            org.junit.jupiter.api.Assertions.assertTrue(a.getSheetAt(0).isRightToLeft());
            org.junit.jupiter.api.Assertions.assertEquals("اسم المركز", a.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("Centre", b.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertTrue(a.getSheetAt(0).getLastRowNum() > b.getSheetAt(0).getLastRowNum());
            org.junit.jupiter.api.Assertions.assertTrue(b.getSheetAt(0).getLastRowNum() >= 1);
        }
        mvc.perform(get("/api/centers/export.xlsx").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
