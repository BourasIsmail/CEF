package ma.entraidenationale.cef.hr.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.common.BadRequestException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Filter;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.StaffSummary;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Summary;
import ma.entraidenationale.cef.hr.domain.ActivityStatus;
import ma.entraidenationale.cef.hr.domain.AdministrativeStatus;
import ma.entraidenationale.cef.hr.domain.EmployeeType;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.ScopeSql;

/** Liste et effectifs des agents en SQL natif ; le périmètre de l'utilisateur est appliqué avant les filtres. */
@Component
@RequiredArgsConstructor
class EmployeeQuery {

    /** Tris autorisés (nom d'API → colonne) : aucun SQL ne vient du client. */
    private static final Map<String, String> SORTS = Map.of(
            "nameAr", "e.full_name_ar",
            "nameFr", "e.full_name_fr",
            "type", "e.employee_type",
            "administrativeStatus", "e.administrative_status",
            "center", "c.name_ar",
            "rankNumber", "e.rank_number",
            "hireDate", "e.hire_date",
            "activityStatus", "e.activity_status");

    private static final String FROM = """
            FROM employee e
            JOIN provincial_directorate p ON p.id = e.provincial_directorate_id
            LEFT JOIN center c ON c.id = e.center_id
            WHERE 1 = 1""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;

    PageResponse<Summary> search(Filter f, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = scoped(params, f.regionId(), f.provinceId(), f.centerId());
        if (f.type() != null) {
            where.append(" AND e.employee_type = :type");
            params.addValue("type", f.type().name());
        }
        if (f.administrativeStatus() != null) {
            where.append(" AND e.administrative_status = :adminStatus");
            params.addValue("adminStatus", f.administrativeStatus().name());
        }
        if (f.activityStatus() != null) {
            where.append(" AND e.activity_status = :activity");
            params.addValue("activity", f.activityStatus().name());
        }
        if (f.gender() != null) {
            where.append(" AND e.gender = :gender");
            params.addValue("gender", f.gender().name());
        }
        if (Boolean.TRUE.equals(f.withoutCenter())) {
            where.append(" AND e.center_id IS NULL");
        }
        if (f.filiereId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM employee_teaching t JOIN training_season s ON s.id = t.season_id AND s.is_current"
                    + " WHERE t.employee_id = e.id AND t.filiere_id = :filiereId)");
            params.addValue("filiereId", f.filiereId());
        }
        if (f.search() != null && !f.search().isBlank()) {
            where.append(" AND (e.full_name_ar LIKE :q ESCAPE '\\\\' OR e.full_name_fr LIKE :q ESCAPE '\\\\'")
                    .append(" OR e.rank_number LIKE :q ESCAPE '\\\\' OR e.id_document_number LIKE :q ESCAPE '\\\\'")
                    .append(" OR e.phone LIKE :q ESCAPE '\\\\')");
            params.addValue("q", "%" + escapeLike(f.search().trim()) + "%");
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<Summary> rows = jdbc.query("""
                SELECT e.id, e.provincial_directorate_id, e.center_id, c.name_ar AS center_name, e.employee_type,
                       e.administrative_status, e.full_name_ar, e.full_name_fr, e.gender, e.phone, e.rank_number,
                       e.hire_date, e.activity_status, e.en_grade, e.en_scale,
                       (SELECT GROUP_CONCAT(DISTINCT f2.name_ar ORDER BY f2.name_ar SEPARATOR '، ') FROM employee_teaching t2
                          JOIN training_season s2 ON s2.id = t2.season_id AND s2.is_current JOIN filiere f2 ON f2.id = t2.filiere_id
                          WHERE t2.employee_id = e.id) AS teaching_ar,
                       (SELECT GROUP_CONCAT(DISTINCT f3.name_fr ORDER BY f3.name_fr SEPARATOR ', ') FROM employee_teaching t3
                          JOIN training_season s3 ON s3.id = t3.season_id AND s3.is_current JOIN filiere f3 ON f3.id = t3.filiere_id
                          WHERE t3.employee_id = e.id) AS teaching_fr
                """ + FROM + where + " ORDER BY " + orderBy(pageable.getSort()) + " LIMIT :limit OFFSET :offset", params,
                (rs, i) -> new Summary(rs.getLong("id"), rs.getLong("provincial_directorate_id"),
                        (Long) rs.getObject("center_id"), rs.getString("center_name"),
                        EmployeeType.valueOf(rs.getString("employee_type")),
                        AdministrativeStatus.valueOf(rs.getString("administrative_status")),
                        rs.getString("full_name_ar"), rs.getString("full_name_fr"), Gender.valueOf(rs.getString("gender")),
                        rs.getString("phone"), rs.getString("rank_number"),
                        rs.getDate("hire_date") == null ? null : rs.getDate("hire_date").toLocalDate(),
                        ActivityStatus.valueOf(rs.getString("activity_status")),
                        rs.getString("en_grade"), rs.getString("en_scale"), rs.getString("teaching_ar"), rs.getString("teaching_fr")));
        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    /** Effectifs des agents non retraités, dans le périmètre et les filtres demandés. */
    StaffSummary summary(Long regionId, Long provinceId, Long centerId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = scoped(params, regionId, provinceId, centerId) + " AND e.activity_status <> 'RETIRED'";

        Map<EmployeeType, Long> byType = new EnumMap<>(EmployeeType.class);
        for (EmployeeType t : EmployeeType.values()) {
            byType.put(t, 0L);
        }
        Map<AdministrativeStatus, Long> byStatus = new EnumMap<>(AdministrativeStatus.class);
        for (AdministrativeStatus s : AdministrativeStatus.values()) {
            byStatus.put(s, 0L);
        }
        jdbc.query("SELECT e.employee_type AS k, COUNT(*) AS n " + FROM + where + " GROUP BY e.employee_type", params,
                rs -> { byType.put(EmployeeType.valueOf(rs.getString("k")), rs.getLong("n")); });
        jdbc.query("SELECT e.administrative_status AS k, COUNT(*) AS n " + FROM + where + " GROUP BY e.administrative_status", params,
                rs -> { byStatus.put(AdministrativeStatus.valueOf(rs.getString("k")), rs.getLong("n")); });
        Map<EmployeeType, Map<AdministrativeStatus, Long>> matrix = new EnumMap<>(EmployeeType.class);
        for (EmployeeType t : EmployeeType.values()) {
            Map<AdministrativeStatus, Long> row = new EnumMap<>(AdministrativeStatus.class);
            for (AdministrativeStatus st : AdministrativeStatus.values()) {
                row.put(st, 0L);
            }
            matrix.put(t, row);
        }
        jdbc.query("SELECT e.employee_type AS t, e.administrative_status AS s, COUNT(*) AS n " + FROM + where
                + " GROUP BY e.employee_type, e.administrative_status", params,
                rs -> { matrix.get(EmployeeType.valueOf(rs.getString("t"))).put(AdministrativeStatus.valueOf(rs.getString("s")), rs.getLong("n")); });
        long[] gender = jdbc.queryForObject("SELECT COALESCE(SUM(e.gender = 'M'), 0) AS m, COALESCE(SUM(e.gender = 'F'), 0) AS f "
                + FROM + where, params, (rs, i) -> new long[] {rs.getLong("m"), rs.getLong("f")});
        return new StaffSummary(gender[0] + gender[1], gender[0], gender[1], byType, byStatus, matrix);
    }

    private StringBuilder scoped(MapSqlParameterSource params, Long regionId, Long provinceId, Long centerId) {
        StringBuilder where = new StringBuilder(ScopeSql.employeePredicate(currentUser.scope(), params, "e", "p"));
        if (regionId != null) {
            where.append(" AND p.regional_directorate_id = :regionId");
            params.addValue("regionId", regionId);
        }
        if (provinceId != null) {
            where.append(" AND e.provincial_directorate_id = :provinceId");
            params.addValue("provinceId", provinceId);
        }
        if (centerId != null) {
            where.append(" AND e.center_id = :centerId");
            params.addValue("centerId", centerId);
        }
        return where;
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
            sb.append("e.full_name_ar ASC, ");
        }
        return sb.append("e.id ASC").toString();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
