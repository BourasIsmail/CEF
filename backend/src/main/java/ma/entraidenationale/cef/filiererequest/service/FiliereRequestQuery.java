package ma.entraidenationale.cef.filiererequest.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BadRequestException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Counts;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Filter;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Summary;
import ma.entraidenationale.cef.filiererequest.domain.RequestStatus;
import ma.entraidenationale.cef.filiererequest.domain.RequestType;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.ScopeSql;

/** Liste des demandes en SQL natif, toujours restreinte au périmètre de l'utilisateur. */
@Component
@RequiredArgsConstructor
class FiliereRequestQuery {

    private static final Map<String, String> SORTS = Map.of(
            "number", "r.request_number",
            "status", "r.status",
            "type", "r.request_type",
            "center", "c.name_ar",
            "name", "r.proposed_name_ar",
            "createdAt", "r.created_at",
            "submittedAt", "r.submitted_at");

    private static final String FROM = """
            FROM filiere_request r
            JOIN center c ON c.id = r.center_id
            JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
            WHERE 1 = 1""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;

    PageResponse<Summary> search(Filter f, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = scoped(params);
        if (f.status() != null) {
            where.append(" AND r.status = :status");
            params.addValue("status", f.status().name());
        }
        if (f.type() != null) {
            where.append(" AND r.request_type = :type");
            params.addValue("type", f.type().name());
        }
        if (f.regionId() != null) {
            where.append(" AND p.regional_directorate_id = :regionId");
            params.addValue("regionId", f.regionId());
        }
        if (f.provinceId() != null) {
            where.append(" AND c.provincial_directorate_id = :provinceId");
            params.addValue("provinceId", f.provinceId());
        }
        if (f.centerId() != null) {
            where.append(" AND r.center_id = :centerId");
            params.addValue("centerId", f.centerId());
        }
        if (Boolean.TRUE.equals(f.actionable())) {
            where.append(" AND r.status IN (").append(actionableStatuses()).append(")");
        }
        if (f.search() != null && !f.search().isBlank()) {
            where.append(" AND (r.request_number LIKE :q ESCAPE '\\\\' OR r.proposed_name_ar LIKE :q ESCAPE '\\\\' OR c.name_ar LIKE :q ESCAPE '\\\\')");
            params.addValue("q", "%" + escapeLike(f.search().trim()) + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<Summary> rows = jdbc.query("""
                SELECT r.id, r.request_number, r.request_type, r.status, r.center_id, c.name_ar AS center_name,
                       p.id AS province_id, p.name_ar AS province_name, p.regional_directorate_id AS region_id,
                       r.proposed_name_ar, r.target_filiere_id, r.submitted_at, r.created_at, r.updated_at
                """ + FROM + where + " ORDER BY " + orderBy(pageable.getSort()) + " LIMIT :limit OFFSET :offset", params,
                (rs, i) -> new Summary(rs.getLong("id"), rs.getString("request_number"),
                        RequestType.valueOf(rs.getString("request_type")), RequestStatus.valueOf(rs.getString("status")),
                        rs.getLong("center_id"), rs.getString("center_name"), rs.getLong("province_id"), rs.getString("province_name"),
                        rs.getLong("region_id"), rs.getString("proposed_name_ar"), (Long) rs.getObject("target_filiere_id"),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    Counts counts() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = scoped(params).toString();
        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (RequestStatus s : RequestStatus.values()) {
            byStatus.put(s, 0L);
        }
        jdbc.query("SELECT r.status AS s, COUNT(*) AS n " + FROM + where + " GROUP BY r.status", params,
                rs -> { byStatus.put(RequestStatus.valueOf(rs.getString("s")), rs.getLong("n")); });
        return new Counts(byStatus, byStatus.values().stream().mapToLong(Long::longValue).sum());
    }

    /** Périmètre par centre ; un compte régional ne voit pas les brouillons des provinces. */
    private StringBuilder scoped(MapSqlParameterSource params) {
        var scope = currentUser.scope();
        StringBuilder where = new StringBuilder(ScopeSql.centerPredicate(scope, params, "c", "p"));
        if (scope.role() == AppRole.REGIONAL) {
            where.append(" AND r.status <> 'DRAFT'");
        }
        return where;
    }

    /** Statuts qui attendent une action du profil courant. */
    private String actionableStatuses() {
        return switch (currentUser.scope().role()) {
            case REGIONAL -> "'SUBMITTED'";
            case ADMIN -> "'REGIONAL_VALIDATED'";
            default -> "'DRAFT', 'RETURNED'";
        };
    }

    private static String orderBy(Sort sort) {
        StringBuilder sb = new StringBuilder();
        for (Sort.Order o : sort) {
            String column = SORTS.get(o.getProperty());
            if (column == null) {
                throw new BadRequestException("invalid_sort", "Critère de tri inconnu : " + o.getProperty());
            }
            sb.append(column).append(o.isAscending() ? " ASC, " : " DESC, ");
        }
        if (sb.isEmpty()) {
            sb.append("r.created_at DESC, ");
        }
        return sb.append("r.id DESC").toString();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
