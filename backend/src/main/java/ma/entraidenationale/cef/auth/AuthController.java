package ma.entraidenationale.cef.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.user.api.UserDtos.LocaleRequest;
import ma.entraidenationale.cef.user.api.UserDtos.View;
import ma.entraidenationale.cef.user.service.UserService;

/**
 * Authentification par cookies httpOnly. Les jetons ne sont jamais renvoyés dans le corps des réponses.
 * Le frontend appelle d'abord {@code GET /api/auth/csrf} pour obtenir le cookie XSRF-TOKEN, puis renvoie sa valeur
 * dans l'en-tête {@code X-XSRF-TOKEN} sur chaque POST / PUT / DELETE (connexion comprise).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification")
class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    private final AuthService auth;
    private final AuthCookies cookies;
    private final UserService users;
    private final CurrentUser currentUser;

    /** Ne fait rien d'autre que provoquer l'émission du cookie XSRF-TOKEN. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<View> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http,
                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return respond(auth.login(req.username(), req.password(), http.getRemoteAddr(), userAgent));
    }

    @PostMapping("/refresh")
    ResponseEntity<View> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
                                 HttpServletRequest http,
                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return respond(auth.refresh(refreshToken, http.getRemoteAddr(), userAgent));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .build();
    }

    @GetMapping("/me")
    View me() {
        return users.viewOf(currentUser.require().id());
    }

    @PutMapping("/me/locale")
    View locale(@Valid @RequestBody LocaleRequest req) {
        return users.updateOwnLocale(currentUser.require().id(), req.preferredLocale());
    }

    @PutMapping("/password")
    ResponseEntity<View> changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http,
                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return respond(auth.changePassword(currentUser.require().id(), req.currentPassword(), req.newPassword(),
                http.getRemoteAddr(), userAgent));
    }

    private ResponseEntity<View> respond(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(session.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(session.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(users.viewOf(session.principal().id()));
    }
}
