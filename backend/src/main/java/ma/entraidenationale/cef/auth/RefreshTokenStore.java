package ma.entraidenationale.cef.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Jetons de rafraîchissement : valeur aléatoire de 256 bits ; seule son empreinte SHA-256 est stockée. Chaque
 * utilisation en émet un nouveau (rotation) et révoque l'ancien.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    record Issued(long id, String rawToken) {
    }

    record Stored(long id, long userId, Instant expiresAt, Instant revokedAt) {
        boolean revoked() {
            return revokedAt != null;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    Issued issue(long userId, Duration ttl, Instant now, String ip, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO refresh_token (user_id, token_hash, issued_at, expires_at, user_agent, ip_address)
                VALUES (:user, :hash, :issued, :expires, :ua, :ip)""",
                new MapSqlParameterSource("user", userId).addValue("hash", hash(raw))
                        .addValue("issued", Timestamp.from(now)).addValue("expires", Timestamp.from(now.plus(ttl)))
                        .addValue("ua", truncate(userAgent, 255)).addValue("ip", truncate(ip, 45)), keys);
        return new Issued(keys.getKey().longValue(), raw);
    }

    Optional<Stored> find(String rawToken) {
        return jdbc.query("SELECT id, user_id, expires_at, revoked_at FROM refresh_token WHERE token_hash = :hash",
                new MapSqlParameterSource("hash", hash(rawToken)),
                (rs, i) -> new Stored(rs.getLong("id"), rs.getLong("user_id"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant())).stream().findFirst();
    }

    /** Révoque un jeton (rotation : {@code replacedBy} désigne son successeur). */
    void revoke(long id, Instant now, Long replacedBy) {
        jdbc.update("UPDATE refresh_token SET revoked_at = :now, replaced_by_id = :next WHERE id = :id AND revoked_at IS NULL",
                new MapSqlParameterSource("now", Timestamp.from(now)).addValue("next", replacedBy).addValue("id", id));
    }

    void revokeAllOf(long userId, Instant now) {
        jdbc.update("UPDATE refresh_token SET revoked_at = :now WHERE user_id = :user AND revoked_at IS NULL",
                new MapSqlParameterSource("now", Timestamp.from(now)).addValue("user", userId));
    }

    int deleteExpiredBefore(Instant limit) {
        return jdbc.update("DELETE FROM refresh_token WHERE expires_at < :limit", new MapSqlParameterSource("limit", Timestamp.from(limit)));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
