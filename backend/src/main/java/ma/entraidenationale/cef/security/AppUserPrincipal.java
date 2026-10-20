package ma.entraidenationale.cef.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Utilisateur authentifié et son périmètre. Les identifiants de région / province / centre sont
 * tous renseignés jusqu'au niveau de rattachement (ex. un provincial porte aussi sa région),
 * ce qui permet de lister les niveaux parents. L'authentification JWT (étape sécurité) produira
 * ce principal.
 */
public record AppUserPrincipal(Long id, String username, AppRole role,
                               Long regionId, Long provinceId, Long centerId) implements UserDetails {

    public UserScope scope() {
        return new UserScope(role, regionId, provinceId, centerId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
