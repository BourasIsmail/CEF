package ma.entraidenationale.cef.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration applicative (préfixe {@code cef}), alimentée par variables d'environnement.
 */
@ConfigurationProperties(prefix = "cef")
public record CefProperties(Cors cors, Storage storage, Security security) {

    /** Origines autorisées (frontend Next.js). */
    public record Cors(List<String> allowedOrigins) {
    }

    /** Dossier de stockage des pièces jointes (demandes de filière), monté en volume. */
    public record Storage(String dir) {
    }

    /**
     * Authentification. {@code jwtSecret} (32 octets minimum) vient de la variable JWT_SECRET ; les cookies sont
     * « Secure » sauf en développement local. Le compte administrateur initial n'est créé que si la table des
     * utilisateurs est vide ET que les deux variables BOOTSTRAP_ADMIN_* sont définies.
     */
    public record Security(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl,
                           boolean cookieSecure, String cookieSameSite,
                           int maxFailedAttempts, Duration lockDuration,
                           String bootstrapAdminEmail, String bootstrapAdminPassword) {
    }
}
