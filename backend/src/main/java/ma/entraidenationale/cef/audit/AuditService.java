package ma.entraidenationale.cef.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.security.CurrentUser;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal d'audit (création / modification / suppression) : qui, quand, quoi. L'écriture a lieu dans la
 * transaction de l'opération métier : si celle-ci est annulée, la trace l'est aussi.
 *
 * <p>Les valeurs sont des « instantanés » (champ → valeur simple) ; seules les différences sont conservées.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final JsonMapper json;

    /** Trace une création. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void created(String entityType, Long entityId, String rootType, Long rootId, Map<String, Object> after) {
        write(entityType, entityId, rootType, rootId, AuditAction.CREATE, diff(Map.of(), after));
    }

    /** Trace une modification ; ne fait rien si rien n'a changé. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updated(String entityType, Long entityId, String rootType, Long rootId,
                        Map<String, Object> before, Map<String, Object> after) {
        Map<String, Map<String, Object>> changes = diff(before, after);
        if (!changes.isEmpty()) {
            write(entityType, entityId, rootType, rootId, AuditAction.UPDATE, changes);
        }
    }

    /** Trace une suppression (valeurs avant suppression). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleted(String entityType, Long entityId, String rootType, Long rootId, Map<String, Object> before) {
        write(entityType, entityId, rootType, rootId, AuditAction.DELETE, diff(before, Map.of()));
    }

    /** Historique d'une entité racine (elle-même et tout ce qui lui est rattaché), du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<AuditEntry> historyOfRoot(String rootType, Long rootId, int limit) {
        return jdbc.query("""
                SELECT id, entity_type, entity_id, root_type, root_id, action, username, occurred_at, changes
                FROM audit_log WHERE root_type = :t AND root_id = :id
                ORDER BY occurred_at DESC, id DESC LIMIT :limit
                """, new MapSqlParameterSource("t", rootType).addValue("id", rootId).addValue("limit", limit),
                (rs, i) -> new AuditEntry(rs.getLong("id"), rs.getString("entity_type"), rs.getLong("entity_id"),
                        rs.getString("root_type"), rs.getLong("root_id"), AuditAction.valueOf(rs.getString("action")),
                        rs.getString("username"), rs.getTimestamp("occurred_at").toInstant(), json.readTree(rs.getString("changes"))));
    }

    /** Recherche administrateur dans tout le journal. */
    @Transactional(readOnly = true)
    public List<AuditEntry> search(String entityType, Long entityId, String username, Instant from, Instant to,
                                   int limit, int offset) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit).addValue("offset", offset);
        StringBuilder sql = new StringBuilder("""
                SELECT id, entity_type, entity_id, root_type, root_id, action, username, occurred_at, changes
                FROM audit_log WHERE 1 = 1""");
        if (entityType != null) {
            sql.append(" AND entity_type = :entityType");
            p.addValue("entityType", entityType);
        }
        if (entityId != null) {
            sql.append(" AND entity_id = :entityId");
            p.addValue("entityId", entityId);
        }
        if (username != null) {
            sql.append(" AND username = :username");
            p.addValue("username", username);
        }
        if (from != null) {
            sql.append(" AND occurred_at >= :from");
            p.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND occurred_at < :to");
            p.addValue("to", Timestamp.from(to));
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset");
        return jdbc.query(sql.toString(), p,
                (rs, i) -> new AuditEntry(rs.getLong("id"), rs.getString("entity_type"), rs.getLong("entity_id"),
                        rs.getString("root_type"), (Long) rs.getObject("root_id"), AuditAction.valueOf(rs.getString("action")),
                        rs.getString("username"), rs.getTimestamp("occurred_at").toInstant(), json.readTree(rs.getString("changes"))));
    }

    // ------------------------------------------------------------------

    private void write(String entityType, Long entityId, String rootType, Long rootId, AuditAction action,
                       Map<String, Map<String, Object>> changes) {
        var user = currentUser.find();
        jdbc.update("""
                INSERT INTO audit_log (entity_type, entity_id, root_type, root_id, action, user_id, username, changes, ip_address)
                VALUES (:type, :id, :rootType, :rootId, :action, :userId, :username, CAST(:changes AS JSON), :ip)
                """, new MapSqlParameterSource()
                .addValue("type", entityType).addValue("id", entityId)
                .addValue("rootType", rootType).addValue("rootId", rootId)
                .addValue("action", action.name())
                .addValue("userId", user.map(u -> u.id()).orElse(null))
                .addValue("username", currentUser.usernameOrSystem())
                .addValue("changes", json.writeValueAsString(changes))
                .addValue("ip", clientIp()));
    }

    /** Différences champ par champ ; une valeur absente équivaut à {@code null}. */
    static Map<String, Map<String, Object>> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.putAll(before);
        keys.putAll(after);
        for (String key : keys.keySet()) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                if (oldValue != null) {
                    change.put("old", oldValue);
                }
                if (newValue != null) {
                    change.put("new", newValue);
                }
                changes.put(key, change);
            }
        }
        return changes;
    }

    private static String clientIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr() : null;
    }
}
