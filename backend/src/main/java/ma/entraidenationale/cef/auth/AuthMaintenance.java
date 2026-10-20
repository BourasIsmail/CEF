package ma.entraidenationale.cef.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;

/** Purge quotidienne des jetons de rafraîchissement expirés. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
class AuthMaintenance {

    private final AuthService auth;

    @Scheduled(cron = "0 30 3 * * *")
    void purge() {
        auth.purgeExpired();
    }
}
