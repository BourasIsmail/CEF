package ma.entraidenationale.cef.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import ma.entraidenationale.cef.beneficiary.domain.StatCategory;

/** Contrats d'API des statistiques. */
public final class StatisticsDtos {

    private StatisticsDtos() {
    }

    /** Garçons / filles / total. */
    public record Cell(long male, long female, long total) {
        static Cell of(long male, long female) {
            return new Cell(male, female, male + female);
        }
    }

    /**
     * Une ligne du tableau officiel : المسجلين, المغادرين, الخريجين, الراسبين, الغائبين et le pourcentage de réussite
     * (diplômés ÷ (diplômés + recalés), en %, 1 décimale ; {@code null} s'il n'y a eu ni diplômé ni recalé).
     */
    public record Metrics(Cell registered, Cell departed, Cell graduates, Cell failed, Cell absent, BigDecimal passRatePercent) {
    }

    /** Une catégorie de bénéficiaires ; {@code specification} liste les précisions de « فئة أخرى ». */
    public record CategoryRow(StatCategory category, String specification, Metrics metrics) {
    }

    public record FiliereBlock(Long id, String code, String nameAr, String nameFr, List<CategoryRow> categories, Metrics total) {
    }

    public record ProvinceBlock(Long id, String code, String nameAr, String nameFr, List<FiliereBlock> filieres, Metrics total) {
    }

    public record RegionBlock(Long id, String code, String nameAr, String nameFr, List<ProvinceBlock> provinces, Metrics total) {
    }

    public record SeasonRef(Long id, String label) {
    }

    /** Filtres appliqués (hors périmètre de l'utilisateur, toujours appliqué en plus). */
    public record Filters(Long regionId, Long provinceId, Long centerId, Long filiereId) {
    }

    /** Rapport hiérarchique : région → province → filière → catégorie, avec totaux à chaque niveau. */
    public record Report(SeasonRef season, Filters filters, List<RegionBlock> regions, Metrics grandTotal, Instant generatedAt) {
    }

    // ------------------------------------------------------------------ tableau de bord

    public record FiliereFigures(Long id, String code, String nameAr, String nameFr, long registered, long graduates) {
    }

    public record CategoryFigures(StatCategory category, long registered) {
    }

    /** Éléments à compléter dans les dossiers (qualité des données). */
    public record ToDo(long graduatesWithoutDiploma, long examsWithoutExamNumber, long departedWithoutReason) {
    }

    public record Dashboard(
            SeasonRef season, Metrics totals, long inProgress,
            List<FiliereFigures> byFiliere, List<CategoryFigures> byCategory, ToDo todo) {
    }
}
