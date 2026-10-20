package ma.entraidenationale.cef.filiererequest.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.config.CefProperties;

/**
 * Stockage des pièces sur disque (volume monté). Le nom du fichier sur disque est généré (UUID) : aucun élément
 * fourni par l'utilisateur n'entre dans le chemin, et chaque chemin est revérifié contre la racine de stockage.
 */
@Component
class RequestFileStorage {

    private final Path root;

    RequestFileStorage(CefProperties properties) {
        this.root = Paths.get(properties.storage().dir()).toAbsolutePath().normalize();
    }

    /** Écrit le fichier et renvoie sa clé de stockage (relative à la racine). */
    String store(long requestId, byte[] content) {
        String key = "filiere-requests/" + requestId + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "upload-", ".part");
            Files.write(tmp, content);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture de la pièce impossible", e);
        }
        return key;
    }

    Path path(String key) {
        return resolve(key);
    }

    /** Suppression au mieux : une pièce orpheline sur disque est préférable à une transaction qui échoue. */
    void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException ignored) {
            // nettoyage ultérieur possible : la clé n'est plus référencée en base
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Clé de stockage invalide");
        }
        return p;
    }
}
