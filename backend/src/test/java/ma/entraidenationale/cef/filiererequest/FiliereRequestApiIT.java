package ma.entraidenationale.cef.filiererequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ma.entraidenationale.cef.AbstractApiIT;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;

/** Demandes de filière : circuit province → région → national, pièces PDF, périmètre, droits, application de la décision. */
class FiliereRequestApiIT extends AbstractApiIT {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private RequestPostProcessor prov0601;
    private RequestPostProcessor prov0602;
    private RequestPostProcessor reg06;
    private RequestPostProcessor reg04;
    private RequestPostProcessor boss;

    @BeforeEach
    void actors() {
        // de vrais comptes en base : les clés étrangères (auteur, décideur, historique) les exigent
        prov0601 = actor(AppRole.PROVINCIAL, "p0601", regionId("06"), provinceId("0601"));
        prov0602 = actor(AppRole.PROVINCIAL, "p0602", regionId("06"), provinceId("0602"));
        reg06 = actor(AppRole.REGIONAL, "r06", regionId("06"), null);
        reg04 = actor(AppRole.REGIONAL, "r04", regionId("04"), null);
        boss = actor(AppRole.ADMIN, "boss", null, null);
    }

    private RequestPostProcessor actor(AppRole role, String username, Long region, Long province) {
        jdbc.update("INSERT INTO app_user (username, password_hash, full_name, role, regional_directorate_id, provincial_directorate_id) VALUES (?, '{noop}x', ?, ?, ?, ?)",
                username, username, role.name(), role == AppRole.ADMIN ? null : region, role == AppRole.PROVINCIAL ? province : null);
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
        return user(new AppUserPrincipal(id, username, role, role == AppRole.ADMIN ? null : region, province, null));
    }

    // ------------------------------------------------------------------ outils

    private String createBody(String center, String extra) {
        return """
                {"requestType": "CREATE", "centerId": %d, "proposedNameAr": "الصباغة والديكور", "proposedNameFr": "Peinture et décoration",
                 "durationMonths": 10, "expectedBeneficiaries": 15%s}""".formatted(centerId(center), extra.isEmpty() ? "" : ", " + extra);
    }

    private long createRequest(RequestPostProcessor who, String json) throws Exception {
        String res = mvc.perform(post("/api/filiere-requests").with(who).contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idOf(res);
    }

    private List<String> typeCodes() {
        return jdbc.queryForList("SELECT code FROM request_document_type ORDER BY position", String.class);
    }

    private ResultActions upload(RequestPostProcessor who, long requestId, String typeCode, String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/filiere-requests/{id}/documents/{t}", requestId, typeCode)
                .file(new MockMultipartFile("file", filename, "application/pdf", bytes)).with(who).with(csrf()));
    }

    private void uploadAll(RequestPostProcessor who, long requestId) throws Exception {
        for (String code : typeCodes()) {
            upload(who, requestId, code, code + ".pdf", PDF).andExpect(status().isCreated());
        }
    }

    private ResultActions act(RequestPostProcessor who, long id, String action, String body) throws Exception {
        return mvc.perform(post("/api/filiere-requests/" + id + "/" + action).with(who).contentType(APPLICATION_JSON).content(body));
    }

    private long submitted(String center) throws Exception {
        long id = createRequest(prov0601, createBody(center, ""));
        uploadAll(prov0601, id);
        act(prov0601, id, "submit", "{}").andExpect(status().isOk());
        return id;
    }

    // ------------------------------------------------------------------ circuit complet

    @Test
    void createRequestGoesThroughTheWholeCircuitAndCreatesTheFiliereAtTheCenter() throws Exception {
        long id = createRequest(prov0601, createBody("CAS-02", ""));
        mvc.perform(get("/api/filiere-requests/" + id).with(prov0601))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requestNumber").value(matchesPattern("DF-\\d{4}-0001")))
                .andExpect(jsonPath("$.documents", hasSize(8)))
                .andExpect(jsonPath("$.documents[0].document").doesNotExist())
                .andExpect(jsonPath("$.availableActions", containsInAnyOrder("EDIT", "SUBMIT", "DELETE")));

        // les 8 pièces sont exigées
        act(prov0601, id, "submit", "{}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("request.documents_missing"))
                .andExpect(jsonPath("$.detail", containsString("TRAINERS_QUALIFICATIONS")));
        uploadAll(prov0601, id);
        act(prov0601, id, "submit", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.availableActions", contains("WITHDRAW")));

        // région
        mvc.perform(get("/api/filiere-requests/" + id).with(reg06))
                .andExpect(jsonPath("$.availableActions", contains("VALIDATE", "RETURN", "REJECT")));
        act(reg06, id, "regional-validation", "{\"comment\": \"Dossier conforme\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REGIONAL_VALIDATED"))
                .andExpect(jsonPath("$.regionalComment").value("Dossier conforme"));

        // national : la filière est créée ET affectée au centre
        mvc.perform(get("/api/filiere-requests/" + id).with(boss))
                .andExpect(jsonPath("$.availableActions", contains("APPROVE", "RETURN", "REJECT")));
        act(boss, id, "approval", "{\"comment\": \"Approuvée\", \"code\": \"paint\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.resultingFiliereId").isNumber())
                .andExpect(jsonPath("$.availableActions", hasSize(0)))
                .andExpect(jsonPath("$.history[*].toStatus", contains("APPROVED", "REGIONAL_VALIDATED", "SUBMITTED", "DRAFT")));

        var f = jdbc.queryForMap("SELECT id, code, name_ar, name_fr, duration_months, active FROM filiere WHERE code = 'PAINT'");
        assertThat(f.get("name_ar")).isEqualTo("الصباغة والديكور");
        assertThat(f.get("name_fr")).isEqualTo("Peinture et décoration");
        assertThat(((Number) f.get("duration_months")).intValue()).isEqualTo(10);
        assertThat(f.get("active")).isIn(true, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM center_filiere WHERE center_id = ? AND filiere_id = ?", Long.class,
                centerId("CAS-02"), f.get("id"))).isEqualTo(1L);
        // le catalogue est visible de tous les profils
        mvc.perform(get("/api/filieres").with(prov0602).param("q", "PAINT")).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void generatedCodeWhenNoneIsGiven() throws Exception {
        long id = submitted("CAS-02");
        act(reg06, id, "regional-validation", "{}").andExpect(status().isOk());
        act(boss, id, "approval", "").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT code FROM filiere WHERE id = (SELECT resulting_filiere_id FROM filiere_request WHERE id = ?)",
                String.class, id)).matches("F\\d{4}-0001");
    }

    @Test
    void returnedRequestIsCorrectedAndResubmitted() throws Exception {
        long id = submitted("CAS-02");
        act(reg06, id, "return", "{}").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.comment_required"));
        act(reg06, id, "return", "{\"comment\": \"Étude de faisabilité illisible\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.regionalComment").value("Étude de faisabilité illisible"));
        mvc.perform(get("/api/filiere-requests/" + id).with(prov0601))
                .andExpect(jsonPath("$.availableActions", containsInAnyOrder("EDIT", "SUBMIT")));

        // correction : remplacement de la pièce, puis nouveau dépôt qui repart de zéro
        upload(prov0601, id, "FEASIBILITY_STUDY", "etude-v2.pdf", PDF).andExpect(status().isCreated());
        mvc.perform(put("/api/filiere-requests/" + id).with(prov0601).contentType(APPLICATION_JSON).content(createBody("CAS-02", "\"modificationSummary\": \"v2\"")))
                .andExpect(status().isOk());
        act(prov0601, id, "submit", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.regionalComment").doesNotExist());
        mvc.perform(get("/api/filiere-requests/" + id).with(prov0601))
                .andExpect(jsonPath("$.history[*].toStatus", contains("SUBMITTED", "RETURNED", "SUBMITTED", "DRAFT")));
    }

    @Test
    void nationalCanReturnOrRejectAfterRegionalValidation() throws Exception {
        long id = submitted("CAS-02");
        act(reg06, id, "regional-validation", "{}").andExpect(status().isOk());
        act(boss, id, "rejection", "{}").andExpect(status().isUnprocessableContent());
        act(boss, id, "rejection", "{\"comment\": \"Hors programme\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.nationalComment").value("Hors programme"));
        // définitif
        act(prov0601, id, "submit", "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("request.invalid_state"));
        act(boss, id, "approval", "{}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM filiere WHERE name_ar = 'الصباغة والديكور'", Long.class)).isZero();
    }

    @Test
    void adminCanStandInForTheRegionAndProvinceCanWithdraw() throws Exception {
        long id = submitted("CAS-02");
        act(prov0601, id, "withdraw", "").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        act(prov0601, id, "submit", "{}").andExpect(status().isOk());
        act(boss, id, "regional-validation", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REGIONAL_VALIDATED"));
        // après la décision régionale, plus de retrait
        act(prov0601, id, "withdraw", "").andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ droits et périmètre

    @Test
    void eachStepBelongsToItsProfile() throws Exception {
        long id = submitted("CAS-02");
        act(prov0601, id, "regional-validation", "{}").andExpect(status().isForbidden());
        act(prov0601, id, "approval", "{}").andExpect(status().isForbidden());
        act(reg06, id, "approval", "{}").andExpect(status().isForbidden());
        act(reg06, id, "submit", "{}").andExpect(status().isForbidden());
        mvc.perform(post("/api/filiere-requests").with(reg06).contentType(APPLICATION_JSON).content(createBody("CAS-02", ""))).andExpect(status().isForbidden());
        // une région étrangère ne décide pas
        act(reg04, id, "regional-validation", "{}").andExpect(status().isNotFound());
        act(reg06, id, "regional-validation", "{}").andExpect(status().isOk());
        act(reg06, id, "regional-validation", "{}").andExpect(status().isConflict());
        act(reg06, id, "rejection", "{\"comment\": \"x\"}").andExpect(status().isForbidden()); // décision nationale en cours
    }

    @Test
    void requestsAreScopedAndRegionalNeverSeesDrafts() throws Exception {
        long draft = createRequest(prov0601, createBody("CAS-01", ""));
        long sent = submitted("CAS-02");

        mvc.perform(get("/api/filiere-requests").with(boss)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/filiere-requests").with(prov0601)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/filiere-requests").with(prov0602)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/filiere-requests").with(reg06)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sent));
        mvc.perform(get("/api/filiere-requests").with(reg04)).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/filiere-requests/" + draft).with(reg06)).andExpect(status().isNotFound());
        mvc.perform(get("/api/filiere-requests/" + draft).with(prov0602)).andExpect(status().isNotFound());
        mvc.perform(get("/api/filiere-requests/" + sent).with(reg04)).andExpect(status().isNotFound());
        mvc.perform(get("/api/filiere-requests/" + sent).with(reg06)).andExpect(status().isOk());
        // un provincial ne crée que pour sa province
        mvc.perform(post("/api/filiere-requests").with(prov0602).contentType(APPLICATION_JSON).content(createBody("CAS-01", "")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listFiltersCountsAndActionableQueue() throws Exception {
        createRequest(prov0601, createBody("CAS-01", ""));
        long sent = submitted("CAS-02");
        mvc.perform(get("/api/filiere-requests").with(boss).param("status", "SUBMITTED")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/filiere-requests").with(boss).param("type", "MODIFY")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/filiere-requests").with(boss).param("centerId", String.valueOf(centerId("CAS-02")))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/filiere-requests").with(boss).param("search", "DF-")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/filiere-requests").with(boss).param("search", "%")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/filiere-requests").with(boss).param("sort", "x;drop")).andExpect(status().isBadRequest());
        // file d'attente de chaque profil
        mvc.perform(get("/api/filiere-requests").with(prov0601).param("actionable", "true")).andExpect(jsonPath("$.totalElements").value(1)); // le brouillon
        mvc.perform(get("/api/filiere-requests").with(reg06).param("actionable", "true")).andExpect(jsonPath("$.content[0].id").value(sent));
        mvc.perform(get("/api/filiere-requests").with(boss).param("actionable", "true")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/filiere-requests/counts").with(boss)).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byStatus.DRAFT").value(1)).andExpect(jsonPath("$.byStatus.SUBMITTED").value(1))
                .andExpect(jsonPath("$.byStatus.APPROVED").value(0));
        mvc.perform(get("/api/filiere-requests/counts").with(reg06)).andExpect(jsonPath("$.total").value(1));
    }

    // ------------------------------------------------------------------ pièces

    @Test
    void onlyRealPdfsUpTo20MbAreAccepted() throws Exception {
        long id = createRequest(prov0601, createBody("CAS-02", ""));
        upload(prov0601, id, "FEASIBILITY_STUDY", "x.pdf", "MZ-not-a-pdf".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("document.not_pdf"));
        upload(prov0601, id, "FEASIBILITY_STUDY", "faux.pdf", "<html>%PDF-</html>".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableContent()); // l'en-tête doit être au début du fichier
        upload(prov0601, id, "FEASIBILITY_STUDY", "vide.pdf", new byte[0]).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("document.empty"));
        byte[] big = new byte[20 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(prov0601, id, "FEASIBILITY_STUDY", "gros.pdf", big).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("document.too_large"));
        byte[] max = new byte[20 * 1024 * 1024];
        System.arraycopy(PDF, 0, max, 0, PDF.length);
        upload(prov0601, id, "FEASIBILITY_STUDY", "limite.pdf", max).andExpect(status().isCreated()).andExpect(jsonPath("$.sizeBytes").value(20 * 1024 * 1024));
        upload(prov0601, id, "PIECE_INCONNUE", "x.pdf", PDF).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_document WHERE request_id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void uploadReplaceDownloadAndDelete() throws Exception {
        long id = createRequest(prov0601, createBody("CAS-02", ""));
        byte[] v1 = "%PDF-1.4 version un".getBytes(StandardCharsets.US_ASCII);
        byte[] v2 = "%PDF-1.7 version deux, plus longue".getBytes(StandardCharsets.US_ASCII);
        long first = idOf(upload(prov0601, id, "TRAINING_PROGRAM", "programme v1.pdf", v1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadedBy").value("p0601")).andReturn().getResponse().getContentAsString());
        long second = idOf(upload(prov0601, id, "TRAINING_PROGRAM", "programme v2.pdf", v2).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // une seule pièce par type : la seconde remplace la première
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_document WHERE request_id = ?", Long.class, id)).isEqualTo(1L);
        mvc.perform(get("/api/filiere-requests/" + id + "/documents/" + first).with(prov0601)).andExpect(status().isNotFound());
        String sha = jdbc.queryForObject("SELECT sha256 FROM request_document WHERE id = ?", String.class, second);
        assertThat(sha).hasSize(64);

        // lecture par les profils qui voient la demande, jamais en dehors
        act(prov0601, id, "submit", "{}").andExpect(status().isUnprocessableContent()); // dossier incomplet : reste en brouillon
        mvc.perform(get("/api/filiere-requests/" + id + "/documents/" + second).with(prov0601))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("programme")))
                .andExpect(content().bytes(v2));
        mvc.perform(get("/api/filiere-requests/" + id + "/documents/" + second).with(prov0602)).andExpect(status().isNotFound());
        mvc.perform(get("/api/filiere-requests/" + id + "/documents/" + second).with(reg06)).andExpect(status().isNotFound()); // brouillon

        mvc.perform(delete("/api/filiere-requests/" + id + "/documents/" + second).with(prov0601)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/filiere-requests/" + id + "/documents/" + second).with(prov0601)).andExpect(status().isNotFound());
    }

    @Test
    void filenamesNeverInfluenceTheStoragePath() throws Exception {
        long id = createRequest(prov0601, createBody("CAS-02", ""));
        upload(prov0601, id, "FEASIBILITY_STUDY", "../../../etc/passwd.pdf", PDF).andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("passwd.pdf"));
        String key = jdbc.queryForObject("SELECT storage_key FROM request_document WHERE request_id = ?", String.class, id);
        assertThat(key).startsWith("filiere-requests/" + id + "/").endsWith(".pdf").doesNotContain("passwd").doesNotContain("..");
        assertThat(Files.exists(STORAGE.resolve(key))).isTrue();
        assertThat(Files.readAllBytes(STORAGE.resolve(key))).isEqualTo(PDF);
    }

    @Test
    void documentsAreLockedOnceSubmitted() throws Exception {
        long id = submitted("CAS-02");
        long doc = jdbc.queryForObject("SELECT id FROM request_document WHERE request_id = ? LIMIT 1", Long.class, id);
        upload(prov0601, id, "FEASIBILITY_STUDY", "x.pdf", PDF).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("request.invalid_state"));
        mvc.perform(delete("/api/filiere-requests/" + id + "/documents/" + doc).with(prov0601)).andExpect(status().isConflict());
        // la région, elle, peut consulter les pièces
        mvc.perform(get("/api/filiere-requests/" + id + "/documents/" + doc).with(reg06)).andExpect(status().isOk());
        upload(reg06, id, "FEASIBILITY_STUDY", "x.pdf", PDF).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ brouillon

    @Test
    void draftRulesAndImmutableFields() throws Exception {
        long id = createRequest(prov0601, createBody("CAS-02", ""));
        mvc.perform(put("/api/filiere-requests/" + id).with(prov0601).contentType(APPLICATION_JSON).content(createBody("CAS-01", "")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.center_immutable"));
        mvc.perform(put("/api/filiere-requests/" + id).with(prov0601).contentType(APPLICATION_JSON)
                .content(createBody("CAS-02", "").replace("CREATE", "MODIFY")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.type_immutable"));
        mvc.perform(put("/api/filiere-requests/" + id).with(prov0601).contentType(APPLICATION_JSON)
                .content(createBody("CAS-02", "").replace("10", "18"))).andExpect(status().isOk()).andExpect(jsonPath("$.durationMonths").value(18));
        mvc.perform(put("/api/filiere-requests/" + id).with(reg06).contentType(APPLICATION_JSON).content(createBody("CAS-02", ""))).andExpect(status().isForbidden());
    }

    @Test
    void onlyDraftsCanBeDeleted() throws Exception {
        long draft = createRequest(prov0601, createBody("CAS-02", ""));
        upload(prov0601, draft, "FEASIBILITY_STUDY", "x.pdf", PDF).andExpect(status().isCreated());
        mvc.perform(delete("/api/filiere-requests/" + draft).with(prov0602)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/filiere-requests/" + draft).with(prov0601)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_document", Long.class)).isZero();
        mvc.perform(get("/api/filiere-requests/" + draft).with(prov0601)).andExpect(status().isNotFound());

        long sent = submitted("CAS-02");
        mvc.perform(delete("/api/filiere-requests/" + sent).with(prov0601)).andExpect(status().isConflict());
    }

    @Test
    void createValidations() throws Exception {
        // le nom existe déjà au catalogue
        mvc.perform(post("/api/filiere-requests").with(prov0601).contentType(APPLICATION_JSON)
                .content(createBody("CAS-02", "").replace("الصباغة والديكور", "خياطة وتفصيل عصري")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.filiere_exists"));
        // association non liée au centre
        long foreign = jdbc.queryForObject("SELECT id FROM association WHERE id NOT IN (SELECT association_id FROM center_association WHERE center_id = ?) LIMIT 1",
                Long.class, centerId("CAS-02"));
        mvc.perform(post("/api/filiere-requests").with(prov0601).contentType(APPLICATION_JSON).content(createBody("CAS-02", "\"associationId\": " + foreign)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.association_not_linked"));
        // nom obligatoire pour une création ; durée obligatoire au dépôt
        mvc.perform(post("/api/filiere-requests").with(prov0601).contentType(APPLICATION_JSON)
                .content("{\"requestType\": \"CREATE\", \"centerId\": %d}".formatted(centerId("CAS-02"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.name_required"));
        long noDuration = createRequest(prov0601, "{\"requestType\": \"CREATE\", \"centerId\": %d, \"proposedNameAr\": \"شعبة جديدة\"}".formatted(centerId("CAS-02")));
        uploadAll(prov0601, noDuration);
        act(prov0601, noDuration, "submit", "{}").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.duration_required"));
        mvc.perform(post("/api/filiere-requests").with(prov0601).contentType(APPLICATION_JSON)
                .content(createBody("CAS-02", "").replace("\"durationMonths\": 10", "\"durationMonths\": 99"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/filiere-requests/document-types").with(prov0601)).andExpect(jsonPath("$", hasSize(8)));
    }

    // ------------------------------------------------------------------ modification d'une filière existante

    @Test
    void modifyRequestUpdatesTheCatalogFiliere() throws Exception {
        long sew = filiereId("SEW");
        String body = """
                {"requestType": "MODIFY", "centerId": %d, "targetFiliereId": %d, "proposedNameFr": "Couture moderne",
                 "durationMonths": 14, "modificationSummary": "Allongement à 14 mois"}""".formatted(centerId("CAS-01"), sew);
        long id = createRequest(prov0601, body);
        // sans nom proposé, le nom actuel est repris
        mvc.perform(get("/api/filiere-requests/" + id).with(prov0601)).andExpect(jsonPath("$.proposedNameAr").value("خياطة وتفصيل عصري"))
                .andExpect(jsonPath("$.targetFiliereNameAr").value("خياطة وتفصيل عصري"));
        uploadAll(prov0601, id);
        act(prov0601, id, "submit", "{}").andExpect(status().isOk());
        act(reg06, id, "regional-validation", "{}").andExpect(status().isOk());
        act(boss, id, "approval", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.resultingFiliereId").value(sew));

        var f = jdbc.queryForMap("SELECT code, name_fr, duration_months FROM filiere WHERE id = ?", sew);
        assertThat(f.get("code")).isEqualTo("SEW");
        assertThat(f.get("name_fr")).isEqualTo("Couture moderne");
        assertThat(((Number) f.get("duration_months")).intValue()).isEqualTo(14);
    }

    @Test
    void modifyRules() throws Exception {
        // CAS-02 ne propose pas la pâtisserie
        mvc.perform(post("/api/filiere-requests").with(prov0601).contentType(APPLICATION_JSON)
                .content("{\"requestType\": \"MODIFY\", \"centerId\": %d, \"targetFiliereId\": %d}".formatted(centerId("CAS-02"), filiereId("PAST"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.target_not_at_center"));
        mvc.perform(post("/api/filiere-requests").with(prov0601).contentType(APPLICATION_JSON)
                .content("{\"requestType\": \"MODIFY\", \"centerId\": %d}".formatted(centerId("CAS-02"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.target_required"));
        long id = createRequest(prov0601, "{\"requestType\": \"MODIFY\", \"centerId\": %d, \"targetFiliereId\": %d}".formatted(centerId("CAS-02"), filiereId("SEW")));
        uploadAll(prov0601, id);
        act(prov0601, id, "submit", "{}").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("request.summary_required"));
    }

    // ------------------------------------------------------------------ audit

    @Test
    void everyStepIsAudited() throws Exception {
        long id = submitted("CAS-02");
        act(reg06, id, "regional-validation", "{}").andExpect(status().isOk());
        mvc.perform(get("/api/filiere-requests/" + id + "/history").with(prov0601)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entityType", org.hamcrest.Matchers.hasItems("FILIERE_REQUEST", "FILIERE_REQUEST_DOCUMENT")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE root_type = 'FILIERE_REQUEST' AND root_id = ? AND entity_type = 'FILIERE_REQUEST_DOCUMENT'",
                Long.class, id)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE root_type = 'FILIERE_REQUEST' AND entity_type = 'FILIERE_REQUEST' AND root_id = ? AND action = 'UPDATE'",
                Long.class, id)).isEqualTo(2L);
        mvc.perform(get("/api/filiere-requests/" + id + "/history").with(reg04)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/filiere-requests")).andExpect(status().isUnauthorized());
    }
}
