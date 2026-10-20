package ma.entraidenationale.cef.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import ma.entraidenationale.cef.security.CurrentUser;

/** Renseigne automatiquement created_at / updated_at / created_by / updated_by. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class JpaConfig {

    @Bean
    AuditorAware<String> auditorAware(CurrentUser currentUser) {
        return () -> Optional.of(currentUser.usernameOrSystem());
    }
}
