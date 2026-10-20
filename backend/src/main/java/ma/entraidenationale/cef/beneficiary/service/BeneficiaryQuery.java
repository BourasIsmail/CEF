package ma.entraidenationale.cef.beneficiary.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Filter;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Summary;
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.beneficiary.domain.StatCategory;
import ma.entraidenationale.cef.common.BadRequestException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.ScopeSql;
import ma.entraidenationale.cef.training.domain.TrainingSeasonRepository;

/**
 * Liste paginée des bénéficiaires en SQL natif : une seule requête (plus un COUNT), jointure avec l'inscription de
 * la saison demandée. Le périmètre de l'utilisateur est TOUJOURS appliqué avant les filtres du client.
 */
@Component
@RequiredArgsConstructor
class BeneficiaryQuery {

    /** Critères de tri autorisés (nom d'API → colonne). Tout autre critère est refusé : aucun SQL venu du client. */
    private static final Map<String, String> SORTS = Map.ofEntries(
            Map.entry("rank", "b.rank_number"),
            Map.entry("registrationNumber", "b.registration_number"),
            Map.entry("registrationDate", "b.registration_date"),
            Map.entry("nameAr", "b.full_name_ar"),
            Map.entry("nameFr", "b.full_name_fr"),
            Map.entry("birthDate", "b.birth_date"),
            Map.entry("center", "c.name_ar"),
            Map.entry("filiere", "f.name_ar"),
            Map.entry("status", "e.status"),
            Map.entry("category", "b.stat_category"));

    private static final String FROM = """
            FROM beneficiary b
            JOIN center c ON c.id = b.center_id
            JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
            LEFT JOIN enrollment e ON e.beneficiary_id = b.id AND e.season_id = :season
            LEFT JOIN filiere f ON f.id = e.filiere_id
            WHERE 1 = 1""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final TrainingSeasonRepository seasons;

    PageResponse<Summary> search(Filter f, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long season = f.seasonId() != null ? f.seasonId() : seasons.findByCurrentTrue().map(s -> s.getId()).orElse(null);
        params.addValue("season", season);

        StringBuilder where = new StringBuilder(ScopeSql.centerPredicate(currentUser.scope(), params, "c", "p"));
        if (f.regionId() != null) {
            where.append(" AND p.regional_directorate_id = :regionId");
            params.addValue("regionId", f.regionId());
        }
        if (f.provinceId() != null) {
            where.append(" AND c.provincial_directorate_id = :provinceId");
            params.addValue("provinceId", f.provinceId());
        }
        if (f.centerId() != null) {
            where.append(" AND b.center_id = :centerId");
            params.addValue("centerId", f.centerId());
        }
        if (f.filiereId() != null) {
            where.append(" AND e.filiere_id = :filiereId");
            params.addValue("filiereId", f.filiereId());
        }
        if (f.status() != null) {
            where.append(" AND e.status = :status");
            params.addValue("status", f.status().name());
        }
        if (f.category() != null) {
            where.append(" AND b.stat_category = :category");
            params.addValue("category", f.category().name());
        }
        if (f.gender() != null) {
            where.append(" AND b.gender = :gender");
            params.addValue("gender", f.gender().name());
        }
        if (f.vulnerable() != null) {
            where.append(" AND b.is_vulnerable = :vulnerable");
            params.addValue("vulnerable", f.vulnerable());
        }
        if (Boolean.TRUE.equals(f.withoutEnrollment())) {
            where.append(" AND e.id IS NULL");
        }
        if (f.search() != null && !f.search().isBlank()) {
            where.append(" AND (b.full_name_ar LIKE :q ESCAPE '\\\\' OR b.full_name_fr LIKE :q ESCAPE '\\\\'")
                    .append(" OR b.registration_number LIKE :q ESCAPE '\\\\' OR b.id_document_number LIKE :q ESCAPE '\\\\'")
                    .append(" OR b.phone LIKE :q ESCAPE '\\\\')");
            params.addValue("q", "%" + escapeLike(f.search().trim()) + "%");
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<Summary> rows = jdbc.query("""
                SELECT b.id, b.center_id, c.name_ar AS center_name, b.rank_number, b.registration_number,
                       b.registration_date, b.gender, b.full_name_ar, b.full_name_fr,
                       TIMESTAMPDIFF(YEAR, b.birth_date, CURDATE()) AS age, b.stat_category, b.is_vulnerable,
                       e.id AS enrollment_id, e.filiere_id, f.name_ar AS filiere_name, e.season_id, e.status
                """ + FROM + where + " ORDER BY " + orderBy(pageable.getSort()) + " LIMIT :limit OFFSET :offset", params,
                (rs, i) -> new Summary(
                        rs.getLong("id"), rs.getLong("center_id"), rs.getString("center_name"), rs.getInt("rank_number"),
                        rs.getString("registration_number"), rs.getDate("registration_date").toLocalDate(),
                        Gender.valueOf(rs.getString("gender")), rs.getString("full_name_ar"), rs.getString("full_name_fr"),
                        rs.getInt("age"), StatCategory.valueOf(rs.getString("stat_category")), rs.getBoolean("is_vulnerable"),
                        (Long) rs.getObject("enrollment_id"), (Long) rs.getObject("filiere_id"), rs.getString("filiere_name"),
                        (Long) rs.getObject("season_id"),
                        rs.getString("status") == null ? null : EnrollmentStatus.valueOf(rs.getString("status"))));

        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
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
            sb.append("b.center_id ASC, b.rank_number ASC, ");
        }
        return sb.append("b.id ASC").toString();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
