package ma.entraidenationale.cef.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.auth.PasswordPolicy;
import ma.entraidenationale.cef.config.CefProperties;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.user.domain.AppUser;
import ma.entraidenationale.cef.user.domain.AppUserRepository;

/**
 * Premier démarrage : crée le compte administrateur à partir de BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD,
 * uniquement si la table des utilisateurs est vide. Aucun mot de passe n'est jamais committé ni journalisé.
 * Une fois connecté, l'administrateur crée les autres comptes et peut retirer ces variables.
 */
@Component
@RequiredArgsConstructor
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final CefProperties properties;
    private final TransactionTemplate tx;

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.security().bootstrapAdminEmail();
        String password = properties.security().bootstrapAdminPassword();
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (hasEmail != hasPassword) {
            log.warn("BOOTSTRAP_ADMIN_EMAIL et BOOTSTRAP_ADMIN_PASSWORD doivent être définis ensemble : aucun compte créé");
            return;
        }
        if (!hasEmail) {
            return;
        }
        tx.executeWithoutResult(status -> {
            if (users.count() > 0) {
                return;
            }
            PasswordPolicy.check(password, email);
            AppUser admin = new AppUser();
            String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
            admin.setUsername(normalized);
            admin.setEmail(normalized);
            admin.setPasswordHash(encoder.encode(password));
            admin.setFullName("Administrateur");
            admin.setRole(AppRole.ADMIN);
            users.save(admin);
            log.info("Compte administrateur initial « {} » créé", admin.getEmail());
        });
    }
}
