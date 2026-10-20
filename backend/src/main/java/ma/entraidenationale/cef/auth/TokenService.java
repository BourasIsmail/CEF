package ma.entraidenationale.cef.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import ma.entraidenationale.cef.config.CefProperties;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;

/**
 * Jetons d'accès : JWT signés HS256, courts (15 min par défaut), qui portent l'identité et le périmètre de
 * l'utilisateur. Un jeton déjà émis reste valable jusqu'à son expiration même si le compte est désactivé ensuite ;
 * le rafraîchissement, lui, revérifie le compte.
 */
@Component
public class TokenService {

    static final String ISSUER = "cef-manager";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final CefProperties.Security props;

    public TokenService(CefProperties properties) {
        this.props = properties.security();
        byte[] secret = props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET (cef.security.jwt-secret) doit faire au moins 32 octets");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(ISSUER);
        nimbus.setJwtValidator(validator);
        this.decoder = nimbus;
    }

    public String issueAccessToken(AppUserPrincipal user, Instant issuedAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.username())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(props.accessTokenTtl()))
                .claim("uid", user.id())
                .claim("role", user.role().name());
        if (user.regionId() != null) {
            claims.claim("region", user.regionId());
        }
        if (user.provinceId() != null) {
            claims.claim("province", user.provinceId());
        }
        if (user.centerId() != null) {
            claims.claim("center", user.centerId());
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    /** Principal décrit par le jeton, ou vide si le jeton est invalide, falsifié ou expiré. */
    public Optional<AppUserPrincipal> parse(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return Optional.of(new AppUserPrincipal(
                    number(jwt, "uid"), jwt.getSubject(), AppRole.valueOf(jwt.getClaimAsString("role")),
                    number(jwt, "region"), number(jwt, "province"), number(jwt, "center")));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static Long number(Jwt jwt, String claim) {
        Object v = jwt.getClaim(claim);
        return v instanceof Number n ? n.longValue() : null;
    }
}
