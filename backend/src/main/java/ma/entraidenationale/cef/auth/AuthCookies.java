package ma.entraidenationale.cef.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.config.CefProperties;

/**
 * Cookies d'authentification : toujours httpOnly (illisibles par le JavaScript), « Secure » hors développement
 * local, SameSite=Lax par défaut. Le cookie de rafraîchissement n'est envoyé qu'aux routes /api/auth.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "cef_access";
    public static final String REFRESH = "cef_refresh";
    public static final String SESSION_ONLY = "cef_session_only";
    private static final String REFRESH_PATH = "/api/auth";

    private final CefProperties.Security props;

    AuthCookies(CefProperties properties) {
        this.props = properties.security();
    }

    ResponseCookie access(String token) {
        return build(ACCESS, token, "/", props.accessTokenTtl());
    }

    ResponseCookie refresh(String token) {
        return build(REFRESH, token, REFRESH_PATH, props.refreshTokenTtl());
    }

    /** Session « non mémorisée » : le cookie disparaît à la fermeture du navigateur. */
    ResponseCookie refreshSession(String token) {
        return ResponseCookie.from(REFRESH, token).httpOnly(true).secure(props.cookieSecure())
                .sameSite(props.cookieSameSite()).path(REFRESH_PATH).build();
    }

    /** Marqueur d'une session non mémorisée, relu au rafraîchissement ; vide = session mémorisée. */
    ResponseCookie sessionOnlyMarker(boolean sessionOnly) {
        return sessionOnly
                ? ResponseCookie.from(SESSION_ONLY, "1").httpOnly(true).secure(props.cookieSecure())
                        .sameSite(props.cookieSameSite()).path(REFRESH_PATH).build()
                : build(SESSION_ONLY, "", REFRESH_PATH, Duration.ZERO);
    }

    ResponseCookie clearAccess() {
        return build(ACCESS, "", "/", Duration.ZERO);
    }

    ResponseCookie clearRefresh() {
        return build(REFRESH, "", REFRESH_PATH, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(props.cookieSecure())
                .sameSite(props.cookieSameSite()).path(path).maxAge(maxAge).build();
    }
}
