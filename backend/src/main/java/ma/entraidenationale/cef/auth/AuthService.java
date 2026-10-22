package ma.entraidenationale.cef.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.common.UnauthorizedException;
import ma.entraidenationale.cef.config.CefProperties;
import ma.entraidenationale.cef.security.AppUserPrincipal;
import ma.entraidenationale.cef.user.domain.AppUser;
import ma.entraidenationale.cef.user.domain.AppUserRepository;

/**
 * Connexion, rotation des jetons de rafraîchissement, déconnexion et changement de mot de passe.
 *
 * <ul>
 *   <li>Toute erreur d'identification renvoie le même message (identifiant inconnu, mauvais mot de passe, compte
 *       désactivé ou verrouillé) : on ne révèle pas quels comptes existent.</li>
 *   <li>Après {@code maxFailedAttempts} échecs consécutifs, le compte est verrouillé pendant {@code lockDuration}.</li>
 *   <li>Un jeton de rafraîchissement déjà utilisé qui revient plus de 10 s après sa rotation signale un vol
 *       possible : toutes les sessions de l'utilisateur sont révoquées.</li>
 * </ul>
 */
@Service
public class AuthService {

    /** Marge pour deux rafraîchissements simultanés du même navigateur (onglets multiples). */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    public record Session(AppUserPrincipal principal, String accessToken, String refreshToken) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final RefreshTokenStore refreshTokens;
    private final CefProperties.Security props;
    private final String dummyHash;

    AuthService(AppUserRepository users, PasswordEncoder encoder, TokenService tokens,
                RefreshTokenStore refreshTokens, CefProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.props = properties.security();
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Session login(String email, String password, String ip, String userAgent) {
        Instant now = Instant.now();
        AppUser user = users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT)).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash); // même coût qu'un vrai compte
            throw invalidCredentials();
        }
        boolean matches = encoder.matches(password, user.getPasswordHash());
        if (!user.isEnabled() || user.isLocked(now)) {
            throw invalidCredentials();
        }
        if (!matches) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= props.maxFailedAttempts()) {
                user.setLockedUntil(now.plus(props.lockDuration()));
                attempts = 0;
            }
            user.setFailedLoginAttempts(attempts);
            throw invalidCredentials();
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return newSession(user, now, ip, userAgent);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Session refresh(String rawToken, String ip, String userAgent) {
        Instant now = Instant.now();
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("auth.no_refresh_token", "Session expirée");
        }
        RefreshTokenStore.Stored stored = refreshTokens.find(rawToken)
                .orElseThrow(() -> new UnauthorizedException("auth.invalid_refresh_token", "Session expirée"));
        if (stored.revoked()) {
            if (stored.revokedAt().plus(ROTATION_GRACE).isBefore(now)) {
                refreshTokens.revokeAllOf(stored.userId(), now); // réutilisation : on coupe toutes les sessions
            }
            throw new UnauthorizedException("auth.invalid_refresh_token", "Session expirée");
        }
        if (!stored.expiresAt().isAfter(now)) {
            throw new UnauthorizedException("auth.invalid_refresh_token", "Session expirée");
        }
        AppUser user = users.findById(stored.userId()).orElse(null);
        if (user == null || !user.isEnabled() || user.isLocked(now)) {
            refreshTokens.revoke(stored.id(), now, null);
            throw new UnauthorizedException("auth.invalid_refresh_token", "Session expirée");
        }
        RefreshTokenStore.Issued next = refreshTokens.issue(user.getId(), props.refreshTokenTtl(), now, ip, userAgent);
        refreshTokens.revoke(stored.id(), now, next.id());
        AppUserPrincipal principal = principalOf(user);
        return new Session(principal, tokens.issueAccessToken(principal, now), next.rawToken());
    }

    /** Déconnexion : révoque le jeton de rafraîchissement présenté (sans erreur s'il est inconnu). */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokens.find(rawToken).ifPresent(t -> refreshTokens.revoke(t.id(), Instant.now(), null));
        }
    }

    /** Change son propre mot de passe : toutes les autres sessions sont révoquées, une nouvelle est ouverte. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Session changePassword(Long userId, String currentPassword, String newPassword, String ip, String userAgent) {
        AppUser user = users.findById(userId).orElseThrow(() -> NotFoundException.of("Utilisateur", userId));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("auth.wrong_password", "Mot de passe actuel incorrect");
        }
        PasswordPolicy.check(newPassword, user.getEmail());
        user.setPasswordHash(encoder.encode(newPassword));
        Instant now = Instant.now();
        refreshTokens.revokeAllOf(userId, now);
        return newSession(user, now, ip, userAgent);
    }

    /** Révoque toutes les sessions d'un utilisateur (désactivation, changement de profil, réinitialisation). */
    @Transactional
    public void revokeSessions(Long userId) {
        refreshTokens.revokeAllOf(userId, Instant.now());
    }

    @Transactional
    public int purgeExpired() {
        return refreshTokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }

    private Session newSession(AppUser user, Instant now, String ip, String userAgent) {
        AppUserPrincipal principal = principalOf(user);
        RefreshTokenStore.Issued refresh = refreshTokens.issue(user.getId(), props.refreshTokenTtl(), now, ip, userAgent);
        return new Session(principal, tokens.issueAccessToken(principal, now), refresh.rawToken());
    }

    private static AppUserPrincipal principalOf(AppUser u) {
        return new AppUserPrincipal(u.getId(), u.getUsername(), u.getRole(), u.getRegionId(), u.getProvinceId(), u.getCenterId());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("auth.invalid_credentials", "Identifiant ou mot de passe incorrect");
    }
}
