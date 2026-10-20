package ma.entraidenationale.cef;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;

/**
 * Base des tests d'API (MockMvc). Chaque test s'exécute dans une transaction annulée à la fin : le jeu de
 * données de développement reste intact. Les utilisateurs sont construits sur les données du seed :
 * régions 06 (Casablanca-Settat) et 04 (Rabat-Salé-Kénitra), provinces 0601, 0602 et 0401.
 */
@Transactional
public abstract class AbstractApiIT extends AbstractMysqlIT {

    @Autowired
    private WebApplicationContext context;

    /** Client avec jeton CSRF valide sur chaque requête (comme le ferait le frontend). */
    protected MockMvc mvc;

    /** Client sans jeton CSRF, pour vérifier que les écritures sans jeton sont refusées. */
    protected MockMvc mvcWithoutCsrf;

    @BeforeEach
    void buildClients() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(csrf())).build();
        mvcWithoutCsrf = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    protected RequestPostProcessor admin() {
        return user(new AppUserPrincipal(1L, "admin", AppRole.ADMIN, null, null, null));
    }

    protected RequestPostProcessor regional(String regionCode) {
        return user(new AppUserPrincipal(2L, "regional-" + regionCode, AppRole.REGIONAL, regionId(regionCode), null, null));
    }

    protected RequestPostProcessor provincial(String provinceCode) {
        Long region = jdbc.queryForObject(
                "SELECT regional_directorate_id FROM provincial_directorate WHERE code = ?", Long.class, provinceCode);
        return user(new AppUserPrincipal(3L, "provincial-" + provinceCode, AppRole.PROVINCIAL,
                region, provinceId(provinceCode), null));
    }

    protected long regionId(String code) {
        return jdbc.queryForObject("SELECT id FROM regional_directorate WHERE code = ?", Long.class, code);
    }

    protected long provinceId(String code) {
        return jdbc.queryForObject("SELECT id FROM provincial_directorate WHERE code = ?", Long.class, code);
    }

    protected long centerId(String code) {
        return jdbc.queryForObject("SELECT id FROM center WHERE code = ?", Long.class, code);
    }

    protected long communeId(String provinceCode, String nameAr) {
        return jdbc.queryForObject(
                "SELECT c.id FROM commune c JOIN provincial_directorate p ON p.id = c.provincial_directorate_id WHERE p.code = ? AND c.name_ar = ?",
                Long.class, provinceCode, nameAr);
    }

    protected long filiereId(String code) {
        return jdbc.queryForObject("SELECT id FROM filiere WHERE code = ?", Long.class, code);
    }

    protected long spaceTypeId(String code) {
        return jdbc.queryForObject("SELECT id FROM space_type WHERE code = ?", Long.class, code);
    }
}
