package ma.entraidenationale.cef.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import ma.entraidenationale.cef.AbstractApiIT;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;

/** Connexion, cookies httpOnly, rotation / réutilisation du refresh token, verrouillage, jetons invalides. */
class AuthApiIT extends AbstractApiIT {

    private static final String PASSWORD = "Str0ng-Passw0rd!";

    @Autowired
    private TokenService tokens;

    @PersistenceContext
    private EntityManager em;

    private void createUser(String username, String role, String extra) throws Exception {
        mvc.perform(post("/api/users").with(admin()).contentType(APPLICATION_JSON).content("""
                {"username": "%s", "password": "%s", "fullName": "Utilisateur Test", "role": "%s"%s}
                """.formatted(username, PASSWORD, role, extra.isEmpty() ? "" : ", " + extra))).andExpect(status().isCreated());
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password))).andReturn();
    }

    private static String cookie(MvcResult r, String name) {
        return setCookie(r, name) == null ? null : setCookie(r, name).split(";", 2)[0].substring(name.length() + 1);
    }

    private static String setCookie(MvcResult r, String name) {
        List<String> headers = r.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH, refreshToken))).andReturn();
    }

    // ------------------------------------------------------------------ connexion

    @Test
    void loginSetsHttpOnlyCookiesAndNeverReturnsTokens() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        MvcResult r = login("nadia.p", PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        String access = setCookie(r, AuthCookies.ACCESS);
        String refresh = setCookie(r, AuthCookies.REFRESH);
        assertThat(access).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/;").contains("Max-Age=900");
        assertThat(refresh).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/auth").contains("Max-Age=604800");
        assertThat(r.getResponse().getContentAsString())
                .doesNotContain(cookie(r, AuthCookies.ACCESS)).doesNotContain(cookie(r, AuthCookies.REFRESH)).doesNotContain("password");
        assertThat(r.getResponse().getContentAsString()).contains("\"username\":\"nadia.p\"").contains("\"role\":\"PROVINCIAL\"");
        assertThat(r.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void accessCookieAuthenticatesRealRequestsWithScope() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        createUser("sara.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0602"));
        Cookie nadia = new Cookie(AuthCookies.ACCESS, cookie(login("nadia.p", PASSWORD), AuthCookies.ACCESS));
        Cookie sara = new Cookie(AuthCookies.ACCESS, cookie(login("sara.p", PASSWORD), AuthCookies.ACCESS));

        mvc.perform(get("/api/beneficiaries").cookie(nadia)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(14));
        mvc.perform(get("/api/beneficiaries").cookie(sara)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/auth/me").cookie(nadia)).andExpect(jsonPath("$.provinceId").value(provinceId("0601")))
                .andExpect(jsonPath("$.regionId").value(regionId("06")));
        // droit d'écriture lié au profil : un provincial ne gère pas les comptes
        mvc.perform(get("/api/users").cookie(nadia)).andExpect(status().isForbidden());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        String wrongPassword = login("nadia.p", "Mauvais-mdp-123").getResponse().getContentAsString();
        String unknownUser = login("inconnu", "Mauvais-mdp-123").getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownUser).contains("auth.invalid_credentials");
        assertThat(login("nadia.p", "Mauvais-mdp-123").getResponse().getStatus()).isEqualTo(401);
        assertThat(setCookie(login("nadia.p", "Mauvais-mdp-123"), AuthCookies.ACCESS)).isNull();
    }

    @Test
    void accountLocksAfterRepeatedFailuresAndUnlocksWithTime() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        for (int i = 0; i < 5; i++) {
            assertThat(login("nadia.p", "Mauvais-mdp-123").getResponse().getStatus()).isEqualTo(401);
        }
        // verrouillé : même le bon mot de passe est refusé, avec la même réponse
        MvcResult locked = login("nadia.p", PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(locked.getResponse().getContentAsString()).contains("auth.invalid_credentials");
        mvc.perform(get("/api/users").with(admin()).param("search", "nadia")).andExpect(jsonPath("$.content[0].locked").value(true));

        jdbc.update("UPDATE app_user SET locked_until = ? WHERE username = 'nadia.p'", java.sql.Timestamp.from(Instant.now().minusSeconds(5)));
        em.clear();
        assertThat(login("nadia.p", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        for (int i = 0; i < 4; i++) {
            login("nadia.p", "Mauvais-mdp-123");
        }
        assertThat(login("nadia.p", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            login("nadia.p", "Mauvais-mdp-123");
        }
        assertThat(login("nadia.p", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mvcWithoutCsrf.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\": \"a\", \"password\": \"b\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    /** Public et sans effet de bord. (Le cookie XSRF-TOKEN lui-même est vérifié en bout en bout sur le jar : voir README.) */
    @Test
    void csrfEndpointIsPublic() throws Exception {
        mvcWithoutCsrf.perform(get("/api/auth/csrf")).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ rafraîchissement

    @Test
    void refreshRotatesTheTokenAndIssuesNewAccess() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        MvcResult first = login("nadia.p", PASSWORD);
        String oldRefresh = cookie(first, AuthCookies.REFRESH);

        MvcResult second = refresh(oldRefresh);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        String newRefresh = cookie(second, AuthCookies.REFRESH);
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, cookie(second, AuthCookies.ACCESS))))
                .andExpect(jsonPath("$.username").value("nadia.p"));
        // en base, seule l'empreinte est stockée
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE token_hash IN (?, ?)", Long.class, oldRefresh, newRefresh)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE token_hash = ?", Long.class, RefreshTokenStore.hash(newRefresh))).isEqualTo(1L);
    }

    @Test
    void reusedRefreshTokenRevokesEveryOneOfTheUsersSessions() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        String stolen = cookie(login("nadia.p", PASSWORD), AuthCookies.REFRESH);
        String legit = cookie(refresh(stolen), AuthCookies.REFRESH);
        // on simule le temps écoulé depuis la rotation (au-delà de la marge de 10 s)
        jdbc.update("UPDATE refresh_token SET revoked_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))), RefreshTokenStore.hash(stolen));

        assertThat(refresh(stolen).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(legit).getResponse().getStatus()).isEqualTo(401); // la session légitime est coupée aussi
    }

    @Test
    void simultaneousRefreshWithinGraceDoesNotKillTheSession() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        String old = cookie(login("nadia.p", PASSWORD), AuthCookies.REFRESH);
        String next = cookie(refresh(old), AuthCookies.REFRESH);
        assertThat(refresh(old).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(next).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refreshRejectsMissingUnknownAndExpiredTokens() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("auth.no_refresh_token"));
        assertThat(refresh("n-existe-pas").getResponse().getStatus()).isEqualTo(401);
        String token = cookie(login("nadia.p", PASSWORD), AuthCookies.REFRESH);
        jdbc.update("UPDATE refresh_token SET expires_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), RefreshTokenStore.hash(token));
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void disabledAccountCannotRefresh() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        String token = cookie(login("nadia.p", PASSWORD), AuthCookies.REFRESH);
        jdbc.update("UPDATE app_user SET enabled = FALSE WHERE username = 'nadia.p'");
        em.clear();
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("nadia.p", PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheSessionAndClearsCookies() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        String token = cookie(login("nadia.p", PASSWORD), AuthCookies.REFRESH);
        MvcResult r = mvc.perform(post("/api/auth/logout").cookie(new Cookie(AuthCookies.REFRESH, token)))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(setCookie(r, AuthCookies.ACCESS)).contains("Max-Age=0");
        assertThat(setCookie(r, AuthCookies.REFRESH)).contains("Max-Age=0");
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()); // idempotent, sans cookie
    }

    // ------------------------------------------------------------------ jetons d'accès invalides

    @Test
    void invalidAccessTokensAreRejected() throws Exception {
        AppUserPrincipal p = new AppUserPrincipal(99L, "ghost", AppRole.ADMIN, null, null, null);
        String valid = tokens.issueAccessToken(p, Instant.now());
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, "garbage"))).andExpect(status().isUnauthorized());
        // signature altérée
        String tampered = valid.substring(0, valid.length() - 4) + (valid.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/api/employees").cookie(new Cookie(AuthCookies.ACCESS, tampered))).andExpect(status().isUnauthorized());
        // jeton expiré
        String expired = tokens.issueAccessToken(p, Instant.now().minus(Duration.ofHours(1)));
        mvc.perform(get("/api/employees").cookie(new Cookie(AuthCookies.ACCESS, expired))).andExpect(status().isUnauthorized());
        // « alg: none » forgé : en-tête {"alg":"none"}, charge utile ADMIN
        String none = "eyJhbGciOiJub25lIn0." + valid.split("\\.")[1] + ".";
        mvc.perform(get("/api/employees").cookie(new Cookie(AuthCookies.ACCESS, none))).andExpect(status().isUnauthorized());
        // un jeton valide, lui, passe (preuve que les rejets ci-dessus viennent bien du jeton)
        mvc.perform(get("/api/employees").cookie(new Cookie(AuthCookies.ACCESS, valid))).andExpect(status().isOk());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ profil de l'utilisateur

    @Test
    void changePasswordRevokesOtherSessionsAndOpensANewOne() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        MvcResult l = login("nadia.p", PASSWORD);
        String oldRefresh = cookie(l, AuthCookies.REFRESH);
        Cookie access = new Cookie(AuthCookies.ACCESS, cookie(l, AuthCookies.ACCESS));
        String newPassword = "Nouveau-Passw0rd!";

        mvc.perform(put("/api/auth/password").cookie(access).contentType(APPLICATION_JSON)
                .content("{\"currentPassword\": \"faux-mot-de-passe1\", \"newPassword\": \"%s\"}".formatted(newPassword)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("auth.wrong_password"));
        mvc.perform(put("/api/auth/password").cookie(access).contentType(APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"court1\"}".formatted(PASSWORD)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("password.weak"));
        MvcResult changed = mvc.perform(put("/api/auth/password").cookie(access).contentType(APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, newPassword)))
                .andExpect(status().isOk()).andReturn();

        assertThat(setCookie(changed, AuthCookies.REFRESH)).isNotNull();
        assertThat(refresh(oldRefresh).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("nadia.p", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("nadia.p", newPassword).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void ownLocaleCanBeChanged() throws Exception {
        createUser("nadia.p", "PROVINCIAL", "\"provinceId\": " + provinceId("0601"));
        Cookie access = new Cookie(AuthCookies.ACCESS, cookie(login("nadia.p", PASSWORD), AuthCookies.ACCESS));
        mvc.perform(put("/api/auth/me/locale").cookie(access).contentType(APPLICATION_JSON).content("{\"preferredLocale\": \"fr\"}"))
                .andExpect(jsonPath("$.preferredLocale").value("fr"));
        mvc.perform(put("/api/auth/me/locale").cookie(access).contentType(APPLICATION_JSON).content("{\"preferredLocale\": \"en\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordsAreStoredHashedOnly() throws Exception {
        createUser("nadia.p", "ADMIN", "");
        String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = 'nadia.p'", String.class);
        assertThat(stored).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        mvc.perform(get("/api/users").with(admin()).param("search", "nadia"))
                .andExpect(content().string(not(containsString("bcrypt")))).andExpect(content().string(not(containsString("passwordHash"))));
    }
}
