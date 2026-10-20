package ma.entraidenationale.cef;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base des tests d'intégration : MySQL 8 réel (jamais de H2, le schéma utilise des colonnes générées,
 * des contraintes CHECK et du JSON propres à MySQL).
 *
 * <p>Par défaut, un conteneur Testcontainers est démarré. Si les variables {@code TEST_DB_URL},
 * {@code TEST_DB_USERNAME} et {@code TEST_DB_PASSWORD} sont définies (CI sans Docker), la base
 * indiquée est utilisée à la place : elle doit être vide.
 */
@SpringBootTest
@ActiveProfiles("dev") // charge aussi le jeu de données fictif db/seed-dev
public abstract class AbstractMysqlIT {

    private static final MySQLContainer MYSQL;

    /** Dossier de pièces jetable : les tests n'écrivent jamais dans le stockage réel. */
    protected static final java.nio.file.Path STORAGE;

    static {
        try {
            STORAGE = java.nio.file.Files.createTempDirectory("cef-test-uploads");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        if (System.getenv("TEST_DB_URL") == null) {
            MYSQL = new MySQLContainer("mysql:8.4")
                    .withDatabaseName("cef_test")
                    .withUsername("cef")
                    .withPassword("cef-test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            MYSQL.start();
        } else {
            MYSQL = null;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("cef.storage.dir", () -> STORAGE.toString());
        registry.add("cef.security.jwt-secret", () -> "test-only-jwt-secret-0123456789-abcdefghijklmnop");
        if (MYSQL != null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        }
    }
}
