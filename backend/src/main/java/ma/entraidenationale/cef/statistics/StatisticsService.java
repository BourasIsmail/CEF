package ma.entraidenationale.cef.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.beneficiary.domain.StatCategory;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.ScopeSql;
import ma.entraidenationale.cef.statistics.StatisticsDtos.CategoryFigures;
import ma.entraidenationale.cef.statistics.StatisticsDtos.CategoryRow;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Cell;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Dashboard;
import ma.entraidenationale.cef.statistics.StatisticsDtos.FiliereBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.FiliereFigures;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Filters;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Metrics;
import ma.entraidenationale.cef.statistics.StatisticsDtos.ProvinceBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.RegionBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Report;
import ma.entraidenationale.cef.statistics.StatisticsDtos.SeasonRef;
import ma.entraidenationale.cef.statistics.StatisticsDtos.ToDo;

/**
 * Rapport statistique officiel : région → province → filière → catégorie.
 *
 * <p>Le comptage est entièrement fait par la base : UNE requête d'agrégation ({@code SUM(CASE …)} groupée par
 * région, province, filière et catégorie, appuyée sur l'index {@code idx_enrollment_stats}) plus une requête de
 * « squelette » qui liste les filières de chaque province (pour afficher aussi les lignes à zéro). Le code Java ne
 * fait qu'assembler ces lignes déjà agrégées et additionner les sous-totaux.
 *
 * <p>Règles : « inscrits » = toutes les inscriptions de la saison ; « partis », « diplômés », « recalés » et « absents »
 * suivent le statut d'inscription ; chaque bénéficiaire n'est compté que dans UNE catégorie (priorité migrant > handicap >
 * autre > reste), donc « reste » = total de la filière − les trois premières catégories ; pourcentage de réussite =
 * diplômés ÷ (diplômés + recalés).
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final StatCategory[] CATEGORY_ORDER = {
            StatCategory.MIGRANT, StatCategory.DISABLED, StatCategory.OTHER, StatCategory.REMAINING};

    // indices du vecteur de comptage
    private static final int REG_M = 0, REG_F = 1, DEP_M = 2, DEP_F = 3, GRA_M = 4, GRA_F = 5,
            FAI_M = 6, FAI_F = 7, ABS_M = 8, ABS_F = 9, SIZE = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;

    // =====================================================================================
    // Rapport
    // =====================================================================================

    @Transactional(readOnly = true)
    public Report report(Long seasonId, Filters filters) {
        SeasonRef season = resolveSeason(seasonId);
        Skeleton skeleton = loadSkeleton(season.id(), filters);
        Map<String, Aggregate> counts = loadCounts(season.id(), filters);

        List<RegionBlock> regions = new ArrayList<>();
        long[] grand = new long[SIZE];
        for (RegionNode r : skeleton.regions.values()) {
            long[] regionTotal = new long[SIZE];
            List<ProvinceBlock> provinces = new ArrayList<>();
            for (ProvinceNode p : r.provinces.values()) {
                long[] provinceTotal = new long[SIZE];
                List<FiliereBlock> filieres = new ArrayList<>();
                for (FiliereNode f : p.filieres.values()) {
                    long[] filiereTotal = new long[SIZE];
                    List<CategoryRow> rows = new ArrayList<>();
                    for (StatCategory category : CATEGORY_ORDER) {
                        Aggregate a = counts.get(key(p.id, f.id, category));
                        long[] v = a == null ? new long[SIZE] : a.values;
                        add(filiereTotal, v);
                        rows.add(new CategoryRow(category, a == null ? null : a.specification, metrics(v)));
                    }
                    add(provinceTotal, filiereTotal);
                    filieres.add(new FiliereBlock(f.id, f.code, f.nameAr, f.nameFr, rows, metrics(filiereTotal)));
                }
                add(regionTotal, provinceTotal);
                provinces.add(new ProvinceBlock(p.id, p.code, p.nameAr, p.nameFr, filieres, metrics(provinceTotal)));
            }
            add(grand, regionTotal);
            regions.add(new RegionBlock(r.id, r.code, r.nameAr, r.nameFr, provinces, metrics(regionTotal)));
        }
        return new Report(season, filters, regions, metrics(grand), Instant.now());
    }

    // =====================================================================================
    // Tableau de bord
    // =====================================================================================

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long seasonId) {
        SeasonRef season = resolveSeason(seasonId);
        Report report = report(season.id(), new Filters(null, null, null, null));

        // par filière et par catégorie : sommes des lignes déjà agrégées (toutes provinces confondues)
        Map<Long, FiliereFigures> byFiliere = new LinkedHashMap<>();
        Map<StatCategory, Long> byCategory = new EnumMap<>(StatCategory.class);
        for (StatCategory c : CATEGORY_ORDER) {
            byCategory.put(c, 0L);
        }
        for (RegionBlock r : report.regions()) {
            for (ProvinceBlock p : r.provinces()) {
                for (FiliereBlock f : p.filieres()) {
                    byFiliere.merge(f.id(),
                            new FiliereFigures(f.id(), f.code(), f.nameAr(), f.nameFr(), f.total().registered().total(), f.total().graduates().total()),
                            (a, b) -> new FiliereFigures(a.id(), a.code(), a.nameAr(), a.nameFr(), a.registered() + b.registered(), a.graduates() + b.graduates()));
                    for (CategoryRow row : f.categories()) {
                        byCategory.merge(row.category(), row.metrics().registered().total(), Long::sum);
                    }
                }
            }
        }
        Metrics totals = report.grandTotal();
        long inProgress = totals.registered().total() - totals.departed().total() - totals.graduates().total()
                - totals.failed().total() - totals.absent().total();
        return new Dashboard(season, totals, inProgress,
                byFiliere.values().stream().filter(f -> f.registered() > 0).toList(),
                byCategory.entrySet().stream().map(e -> new CategoryFigures(e.getKey(), e.getValue())).toList(),
                toDo(season.id()));
    }

    /** Dossiers à compléter (périmètre de l'utilisateur) : diplômés sans diplôme, sessions sans n° d'examen, départs sans motif. */
    private ToDo toDo(Long seasonId) {
        MapSqlParameterSource p = new MapSqlParameterSource("season", seasonId);
        String scope = ScopeSql.centerPredicate(currentUser.scope(), p, "c", "p");
        String base = """
                FROM enrollment e
                JOIN center c ON c.id = e.center_id
                JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
                WHERE e.season_id = :season""" + scope;
        Long noDiploma = jdbc.queryForObject("SELECT COUNT(*) " + base
                + " AND e.status = 'GRADUATED' AND NOT EXISTS (SELECT 1 FROM diploma d WHERE d.enrollment_id = e.id)", p, Long.class);
        Long noExamNumber = jdbc.queryForObject("SELECT COUNT(*) " + base
                + " AND EXISTS (SELECT 1 FROM exam_session s WHERE s.enrollment_id = e.id AND (s.exam_number IS NULL OR s.exam_number = ''))", p, Long.class);
        Long noReason = jdbc.queryForObject("SELECT COUNT(*) " + base
                + " AND e.status = 'DEPARTED' AND (e.departure_reason IS NULL OR e.departure_reason = '')", p, Long.class);
        return new ToDo(noDiploma, noExamNumber, noReason);
    }

    // =====================================================================================
    // Requêtes
    // =====================================================================================

    private SeasonRef resolveSeason(Long seasonId) {
        List<SeasonRef> found = seasonId != null
                ? jdbc.query("SELECT id, label FROM training_season WHERE id = :id", new MapSqlParameterSource("id", seasonId),
                        (rs, i) -> new SeasonRef(rs.getLong(1), rs.getString(2)))
                : jdbc.query("SELECT id, label FROM training_season WHERE is_current", new MapSqlParameterSource(),
                        (rs, i) -> new SeasonRef(rs.getLong(1), rs.getString(2)));
        if (found.isEmpty()) {
            throw new BusinessRuleException(seasonId != null ? "statistics.season_unknown" : "statistics.no_current_season",
                    seasonId != null ? "Saison inconnue" : "Aucune saison courante n'est définie");
        }
        return found.get(0);
    }

    /** Prédicats communs : périmètre de l'utilisateur d'abord, puis filtres du client. */
    private String predicates(Filters f, MapSqlParameterSource params, String centerAlias, String provinceAlias, String filiereColumn) {
        StringBuilder sb = new StringBuilder(ScopeSql.centerPredicate(currentUser.scope(), params, centerAlias, provinceAlias));
        if (f.regionId() != null) {
            sb.append(" AND ").append(provinceAlias).append(".regional_directorate_id = :fRegion");
            params.addValue("fRegion", f.regionId());
        }
        if (f.provinceId() != null) {
            sb.append(" AND ").append(centerAlias).append(".provincial_directorate_id = :fProvince");
            params.addValue("fProvince", f.provinceId());
        }
        if (f.centerId() != null) {
            sb.append(" AND ").append(centerAlias).append(".id = :fCenter");
            params.addValue("fCenter", f.centerId());
        }
        if (f.filiereId() != null) {
            sb.append(" AND ").append(filiereColumn).append(" = :fFiliere");
            params.addValue("fFiliere", f.filiereId());
        }
        return sb.toString();
    }

    /** Les filières proposées dans chaque province : permet d'afficher aussi les lignes sans inscrit. */
    private Skeleton loadSkeleton(Long seasonId, Filters filters) {
        MapSqlParameterSource p = new MapSqlParameterSource("season", seasonId);
        String where = predicates(filters, p, "c", "p", "f.id");
        Skeleton skeleton = new Skeleton();
        jdbc.query("""
                SELECT DISTINCT r.id r_id, r.code r_code, r.name_ar r_ar, r.name_fr r_fr,
                       p.id p_id, p.code p_code, p.name_ar p_ar, p.name_fr p_fr,
                       f.id f_id, f.code f_code, f.name_ar f_ar, f.name_fr f_fr
                FROM center_filiere cf
                JOIN center c ON c.id = cf.center_id
                JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
                JOIN regional_directorate r ON r.id = p.regional_directorate_id
                JOIN filiere f ON f.id = cf.filiere_id
                WHERE (f.active = 1 OR EXISTS (
                          SELECT 1 FROM enrollment e2 JOIN center c2 ON c2.id = e2.center_id
                          WHERE e2.season_id = :season AND e2.filiere_id = f.id AND c2.provincial_directorate_id = p.id))"""
                + where + " ORDER BY r.code, p.code, f.name_ar", p,
                rs -> {
                    RegionNode r = skeleton.regions.computeIfAbsent(rs.getLong("r_id"),
                            id -> new RegionNode(id, rs2(rs, "r_code"), rs2(rs, "r_ar"), rs2(rs, "r_fr")));
                    ProvinceNode pr = r.provinces.computeIfAbsent(rs.getLong("p_id"),
                            id -> new ProvinceNode(id, rs2(rs, "p_code"), rs2(rs, "p_ar"), rs2(rs, "p_fr")));
                    pr.filieres.computeIfAbsent(rs.getLong("f_id"),
                            id -> new FiliereNode(id, rs2(rs, "f_code"), rs2(rs, "f_ar"), rs2(rs, "f_fr")));
                });
        return skeleton;
    }

    /** L'agrégation : une ligne par (province, filière, catégorie) avec tous les compteurs garçons / filles. */
    private Map<String, Aggregate> loadCounts(Long seasonId, Filters filters) {
        MapSqlParameterSource p = new MapSqlParameterSource("season", seasonId);
        String where = predicates(filters, p, "c", "p", "e.filiere_id");
        Map<String, Aggregate> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.id AS province_id, e.filiere_id, b.stat_category,
                       SUM(b.gender = 'M') AS reg_m, SUM(b.gender = 'F') AS reg_f,
                       SUM(e.status = 'DEPARTED'  AND b.gender = 'M') AS dep_m, SUM(e.status = 'DEPARTED'  AND b.gender = 'F') AS dep_f,
                       SUM(e.status = 'GRADUATED' AND b.gender = 'M') AS gra_m, SUM(e.status = 'GRADUATED' AND b.gender = 'F') AS gra_f,
                       SUM(e.status = 'FAILED'    AND b.gender = 'M') AS fai_m, SUM(e.status = 'FAILED'    AND b.gender = 'F') AS fai_f,
                       SUM(e.status = 'ABSENT'    AND b.gender = 'M') AS abs_m, SUM(e.status = 'ABSENT'    AND b.gender = 'F') AS abs_f,
                       GROUP_CONCAT(DISTINCT CASE WHEN b.stat_category = 'OTHER' THEN b.other_social_situation_text END
                                    ORDER BY b.other_social_situation_text SEPARATOR '، ') AS other_specs
                FROM enrollment e
                JOIN beneficiary b ON b.id = e.beneficiary_id
                JOIN center c ON c.id = e.center_id
                JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
                WHERE e.season_id = :season"""
                + where + " GROUP BY p.id, e.filiere_id, b.stat_category", p,
                rs -> {
                    long[] v = new long[SIZE];
                    String[] cols = {"reg_m", "reg_f", "dep_m", "dep_f", "gra_m", "gra_f", "fai_m", "fai_f", "abs_m", "abs_f"};
                    for (int i = 0; i < SIZE; i++) {
                        v[i] = rs.getLong(cols[i]);
                    }
                    result.put(key(rs.getLong("province_id"), rs.getLong("filiere_id"), StatCategory.valueOf(rs.getString("stat_category"))),
                            new Aggregate(v, rs.getString("other_specs")));
                });
        return result;
    }

    // =====================================================================================
    // Assemblage
    // =====================================================================================

    private static String rs2(java.sql.ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key(Long provinceId, Long filiereId, StatCategory category) {
        return provinceId + ":" + filiereId + ":" + category;
    }

    private static void add(long[] target, long[] source) {
        for (int i = 0; i < SIZE; i++) {
            target[i] += source[i];
        }
    }

    private static Metrics metrics(long[] v) {
        long graduates = v[GRA_M] + v[GRA_F];
        long failed = v[FAI_M] + v[FAI_F];
        BigDecimal rate = graduates + failed == 0 ? null
                : BigDecimal.valueOf(graduates * 100.0 / (graduates + failed)).setScale(1, RoundingMode.HALF_UP);
        return new Metrics(Cell.of(v[REG_M], v[REG_F]), Cell.of(v[DEP_M], v[DEP_F]), Cell.of(v[GRA_M], v[GRA_F]),
                Cell.of(v[FAI_M], v[FAI_F]), Cell.of(v[ABS_M], v[ABS_F]), rate);
    }

    private record Aggregate(long[] values, String specification) {
    }

    private static final class Skeleton {
        final Map<Long, RegionNode> regions = new LinkedHashMap<>();
    }

    private record RegionNode(Long id, String code, String nameAr, String nameFr, Map<Long, ProvinceNode> provinces) {
        RegionNode(Long id, String code, String nameAr, String nameFr) {
            this(id, code, nameAr, nameFr, new LinkedHashMap<>());
        }
    }

    private record ProvinceNode(Long id, String code, String nameAr, String nameFr, Map<Long, FiliereNode> filieres) {
        ProvinceNode(Long id, String code, String nameAr, String nameFr) {
            this(id, code, nameAr, nameFr, new LinkedHashMap<>());
        }
    }

    private record FiliereNode(Long id, String code, String nameAr, String nameFr) {
    }
}
