package ma.entraidenationale.cef.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration applicative (préfixe {@code cef}), alimentée par variables d'environnement.
 */
@ConfigurationProperties(prefix = "cef")
public record CefProperties(Cors cors, Storage storage) {

    /** Origines autorisées (frontend Next.js). */
    public record Cors(List<String> allowedOrigins) {
    }

    /** Dossier de stockage des pièces jointes (demandes de filière), monté en volume. */
    public record Storage(String dir) {
    }
}
