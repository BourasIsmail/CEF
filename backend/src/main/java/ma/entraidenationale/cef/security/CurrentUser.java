package ma.entraidenationale.cef.security;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Accès à l'utilisateur authentifié courant. */
@Component
public class CurrentUser {

    public Optional<AppUserPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public AppUserPrincipal require() {
        return find().orElseThrow(() -> new AccessDeniedException("Utilisateur non authentifié"));
    }

    public UserScope scope() {
        return require().scope();
    }

    /** Nom utilisé pour renseigner created_by / updated_by. */
    public String usernameOrSystem() {
        return find().map(AppUserPrincipal::username).orElse("system");
    }
}
