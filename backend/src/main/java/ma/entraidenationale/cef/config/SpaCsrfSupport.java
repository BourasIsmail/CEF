package ma.entraidenationale.cef.config;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protection CSRF adaptée à une SPA (Next.js) : le jeton est publié dans le cookie {@code XSRF-TOKEN}
 * (lisible par le JavaScript) et renvoyé par le client dans l'en-tête {@code X-XSRF-TOKEN} à chaque écriture.
 * Les cookies d'authentification restent httpOnly ; ce mécanisme vient en plus de {@code SameSite}.
 */
final class SpaCsrfSupport {

    private SpaCsrfSupport() {
    }

    /** Gestionnaire recommandé par Spring Security pour les SPA (jeton masqué BREACH-safe dans les formulaires). */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            // En-tête : valeur brute lue dans le cookie ; paramètre de formulaire : valeur masquée.
            return StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
                    ? this.plain.resolveCsrfTokenValue(request, csrfToken)
                    : this.xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /** Force le chargement du jeton différé pour que le cookie XSRF-TOKEN soit écrit dès la première réponse. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
