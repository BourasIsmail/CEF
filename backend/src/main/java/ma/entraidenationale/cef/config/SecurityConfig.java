package ma.entraidenationale.cef.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ma.entraidenationale.cef.auth.JwtCookieFilter;
import ma.entraidenationale.cef.auth.TokenService;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Sécurité : API sans session, authentifiée par le cookie httpOnly {@code cef_access} (JWT, voir le package
 * {@code auth}), protégée contre le CSRF par cookie + en-tête, CORS restreint aux origines configurées.
 * Seuls les sondes de santé et les routes de connexion sont publiques ; les droits fins sont portés par
 * {@code @PreAuthorize} (profil) et par le périmètre appliqué dans les services (région / province / centre).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize sur les contrôleurs
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens) throws Exception {
        http
                .addFilterBefore(new JwtCookieFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfSupport.SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new SpaCsrfSupport.CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .cors(Customizer.withDefaults()) // utilise le bean « corsConfigurationSource » ci-dessous
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CefProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true); // cookies httpOnly
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Hash bcrypt (préfixe {bcrypt}, ré-évolutif : un autre algorithme pourra coexister plus tard). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Aucun utilisateur en mémoire : évite le mot de passe généré par défaut au démarrage. */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
