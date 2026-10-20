package ma.entraidenationale.cef.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import ma.entraidenationale.cef.AbstractApiIT;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;

/** Gestion des comptes : droits, profils et portées, protections (dernier admin, auto-désactivation), audit. */
class UserApiIT extends AbstractApiIT {

    private static final String PASSWORD = "Str0ng-Passw0rd!";

    private ResultActions create(String username, String role, String extra) throws Exception {
        return mvc.perform(post("/api/users").with(admin()).contentType(APPLICATION_JSON).content("""
                {"username": "%s", "password": "%s", "fullName": "Utilisateur Test", "role": "%s"%s}
                """.formatted(username, PASSWORD, role, extra.isEmpty() ? "" : ", " + extra)));
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    /** Administrateur « réel » en base (id connu) pour tester les protections liées à soi-même. */
    private org.springframework.test.web.servlet.request.RequestPostProcessor realAdmin(String username) throws Exception {
        create(username, "ADMIN", "").andExpect(status().isCreated());
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(new AppUserPrincipal(userId(username), username, AppRole.ADMIN, null, null, null));
    }

    // ------------------------------------------------------------------ droits

    @Test
    void onlyAdminsManageAccounts() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").with(regional("06"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(provincial("0601"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/users").with(provincial("0601")).contentType(APPLICATION_JSON)
                .content("{\"username\": \"x.y\", \"password\": \"%s\", \"fullName\": \"X\", \"role\": \"ADMIN\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ création

    @Test
    void scopeFollowsTheProfile() throws Exception {
        create("admin.two", "ADMIN", "\"regionId\": " + regionId("06"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.regionId").doesNotExist()).andExpect(jsonPath("$.provinceId").doesNotExist());
        create("reg.six", "REGIONAL", "\"regionId\": " + regionId("06"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.regionNameAr").exists()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.preferredLocale").value("ar"));
        // la région d'un provincial est déduite de sa province, quoi qu'on envoie
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601") + ", \"regionId\": " + regionId("04"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.regionId").value(regionId("06")))
                .andExpect(jsonPath("$.provinceNameAr").exists());
    }

    @Test
    void creationRules() throws Exception {
        create("reg.six", "REGIONAL", "").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.region_required"));
        create("prov.x", "PROVINCIAL", "").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.province_required"));
        create("prov.x", "PROVINCIAL", "\"provinceId\": 999999").andExpect(status().isUnprocessableContent());
        create("mgr.x", "CENTER_MANAGER", "").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.role_not_available"));
        create("a b", "ADMIN", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.username").exists());
        create("ok.user", "ADMIN", "").andExpect(status().isCreated());
        create("OK.USER", "ADMIN", "").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("user.username_exists"));
    }

    @Test
    void passwordPolicyIsEnforced() throws Exception {
        for (String weak : new String[] {"court1", "uniquementdeslettres", "123456789012345", "ok.user-Abc12345"}) {
            mvc.perform(post("/api/users").with(admin()).contentType(APPLICATION_JSON)
                    .content("{\"username\": \"ok.user\", \"password\": \"%s\", \"fullName\": \"X\", \"role\": \"ADMIN\"}".formatted(weak)))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("password.weak"));
        }
    }

    // ------------------------------------------------------------------ liste

    @Test
    void listFiltersAndHidesSecrets() throws Exception {
        create("reg.six", "REGIONAL", "\"regionId\": " + regionId("06")).andExpect(status().isCreated());
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        mvc.perform(get("/api/users").with(admin())).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/users").with(admin()).param("role", "REGIONAL")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/users").with(admin()).param("search", "PROV")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/users").with(admin()).param("enabled", "false")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/users").with(admin()).param("sort", "inexistant")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/" + userId("reg.six")).with(admin())).andExpect(jsonPath("$.username").value("reg.six"));
        mvc.perform(get("/api/users/999999").with(admin())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ modification, désactivation

    @Test
    void changingProfileOrScopeRevokesSessions() throws Exception {
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        long id = userId("prov.cas");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Long.class, id)).isEqualTo(1L);

        mvc.perform(put("/api/users/" + id).with(admin()).contentType(APPLICATION_JSON).content("""
                {"fullName": "Nouveau Nom", "role": "PROVINCIAL", "provinceId": %d}""".formatted(provinceId("0602"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provinceId").value(provinceId("0602")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Long.class, id)).isZero();
    }

    @Test
    void renamingAloneKeepsSessions() throws Exception {
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        long id = userId("prov.cas");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)));
        mvc.perform(put("/api/users/" + id).with(admin()).contentType(APPLICATION_JSON).content("""
                {"fullName": "Autre Nom", "role": "PROVINCIAL", "provinceId": %d}""".formatted(provinceId("0601")))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void disablingBlocksLoginAndRevokesSessions() throws Exception {
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        long id = userId("prov.cas");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)));
        mvc.perform(put("/api/users/" + id + "/enabled").with(admin()).contentType(APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Long.class, id)).isZero();
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/" + id + "/enabled").with(admin()).contentType(APPLICATION_JSON).content("{\"enabled\": true}"))
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void reenablingClearsALock() throws Exception {
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"Mauvais-mdp-123\"}"));
        }
        mvc.perform(get("/api/users/" + userId("prov.cas")).with(admin())).andExpect(jsonPath("$.locked").value(true));
        mvc.perform(put("/api/users/" + userId("prov.cas") + "/enabled").with(admin()).contentType(APPLICATION_JSON).content("{\"enabled\": true}"))
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    void safeguardsAgainstLockingEveryoneOut() throws Exception {
        var me = realAdmin("boss.one");
        long meId = userId("boss.one");
        // acteur distinct de boss.one même si l'auto-incrément a donné l'id 1 à ce dernier
        var root = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                new ma.entraidenationale.cef.security.AppUserPrincipal(999_999L, "root", ma.entraidenationale.cef.security.AppRole.ADMIN, null, null, null));
        // pas d'auto-désactivation, pas de changement de son propre profil
        mvc.perform(put("/api/users/" + meId + "/enabled").with(me).contentType(APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.self_disable"));
        mvc.perform(put("/api/users/" + meId).with(me).contentType(APPLICATION_JSON)
                .content("{\"fullName\": \"Boss\", \"role\": \"REGIONAL\", \"regionId\": %d}".formatted(regionId("06"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.self_role"));

        // le dernier administrateur actif ne peut être ni désactivé ni rétrogradé (par un autre compte)
        create("boss.two", "ADMIN", "").andExpect(status().isCreated());
        long otherAdmin = userId("boss.two");
        mvc.perform(put("/api/users/" + otherAdmin + "/enabled").with(me).contentType(APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isOk());                                    // boss.one reste actif
        mvc.perform(put("/api/users/" + meId + "/enabled").with(root).contentType(APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.last_admin"));
        mvc.perform(put("/api/users/" + meId).with(root).contentType(APPLICATION_JSON)
                .content("{\"fullName\": \"Boss\", \"role\": \"REGIONAL\", \"regionId\": %d}".formatted(regionId("06"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("user.last_admin"));
    }

    @Test
    void adminResetsAPasswordAndSessionsAreCut() throws Exception {
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        long id = userId("prov.cas");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)));
        mvc.perform(put("/api/users/" + id + "/password").with(admin()).contentType(APPLICATION_JSON).content("{\"newPassword\": \"faible\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/users/" + id + "/password").with(admin()).contentType(APPLICATION_JSON).content("{\"newPassword\": \"Autre-Passw0rd-9\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Long.class, id)).isZero();
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{\"username\": \"prov.cas\", \"password\": \"Autre-Passw0rd-9\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ audit

    @Test
    void accountChangesAreAuditedWithoutSecrets() throws Exception {
        create("prov.cas", "PROVINCIAL", "\"provinceId\": " + provinceId("0601")).andExpect(status().isCreated());
        long id = userId("prov.cas");
        mvc.perform(put("/api/users/" + id + "/enabled").with(admin()).contentType(APPLICATION_JSON).content("{\"enabled\": false}"));
        mvc.perform(put("/api/users/" + id + "/password").with(admin()).contentType(APPLICATION_JSON).content("{\"newPassword\": \"Autre-Passw0rd-9\"}"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type = 'APP_USER' AND entity_id = ?", Long.class, id)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type = 'APP_USER' AND (changes LIKE '%bcrypt%' OR changes LIKE '%Passw0rd%')", Long.class)).isZero();
    }
}
