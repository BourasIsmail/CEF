package ma.entraidenationale.cef.auth;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authentifie la requête à partir du cookie {@code cef_access}. Un jeton invalide laisse la requête anonyme (401). */
public class JwtCookieFilter extends OncePerRequestFilter {

    private final TokenService tokens;

    public JwtCookieFilter(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AuthCookies.ACCESS.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    tokens.parse(cookie.getValue()).ifPresent(principal -> SecurityContextHolder.getContext()
                            .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                                    principal, null, principal.getAuthorities())));
                    break;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
