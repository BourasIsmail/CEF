package ma.entraidenationale.cef.audit;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** Une ligne du journal d'audit : qui, quand, quoi ({@code changes} = {champ: {old, new}}). */
public record AuditEntry(Long id, String entityType, Long entityId, String rootType, Long rootId, AuditAction action,
                         String username, Instant occurredAt, JsonNode changes) {
}
