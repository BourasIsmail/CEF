package ma.entraidenationale.cef.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import ma.entraidenationale.cef.AbstractMysqlIT;

/** Le compte initial n'est créé qu'à partir des variables d'environnement, une seule fois, sans mot de passe en clair. */
@TestPropertySource(properties = {
        "cef.security.bootstrap-admin-username=boot.admin",
        "cef.security.bootstrap-admin-password=Bootstrap-Passw0rd-1"})
class BootstrapAdminIT extends AbstractMysqlIT {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void cleanUp(@Autowired JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM app_user WHERE username = 'boot.admin'");
    }

    @Test
    void createsTheFirstAdminOnlyWhenNoUserExists() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username = 'boot.admin' AND role = 'ADMIN' AND enabled", Long.class))
                .isEqualTo(1L);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = 'boot.admin'", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("Bootstrap-Passw0rd-1");
    }
}
