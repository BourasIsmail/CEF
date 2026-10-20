package ma.entraidenationale.cef.training.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrats d'API : programmes, saisons, filières, modules, affectation aux centres. */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    // ------------------------------------------------------------------ programmes / saisons

    public record ProgramView(Long id, String code, String nameAr, String nameFr) {
    }

    public record SeasonView(Long id, String label, LocalDate startDate, LocalDate endDate, boolean current) {
    }

    /** {@code label} au format 2025-2026 (années consécutives). */
    public record SeasonRequest(
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{4}", message = "Format attendu : 2025-2026") String label,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    // ------------------------------------------------------------------ filières et modules

    public record ModuleView(Long id, String nameAr, String nameFr, BigDecimal coefficient, int durationHours, int position) {
    }

    public record ModuleRequest(
            @NotBlank @Size(max = 200) String nameAr,
            @Size(max = 200) String nameFr,
            @NotNull @DecimalMin(value = "0.01", message = "Le coefficient doit être positif")
            @DecimalMax(value = "99.99") BigDecimal coefficient,
            @NotNull @Min(1) @Max(5000) Integer durationHours) {
    }

    /** Nouvel ordre des modules : tous les identifiants de la filière, dans l'ordre souhaité. */
    public record ReorderRequest(@NotEmpty List<Long> moduleIds) {
    }

    public record FiliereRequest(
            /** Programme ; par défaut « CEF ». */
            Long programId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,30}", message = "2 à 30 caractères : lettres, chiffres, - et _") String code,
            @NotBlank @Size(max = 200) String nameAr,
            @NotBlank @Size(max = 200) String nameFr,
            @NotNull @Min(1) @Max(60) Integer durationMonths,
            Boolean active) {
    }

    public record FiliereSummary(
            Long id, Long programId, String programCode, String code, String nameAr, String nameFr,
            int durationMonths, boolean active,
            int modulesCount, BigDecimal totalCoefficient, int totalHours,
            /** Nombre de centres assignés, dans le périmètre de l'utilisateur. */
            long centersCount) {
    }

    public record CenterRef(Long id, String code, String nameAr, Long provinceId) {
    }

    public record FiliereDetail(
            FiliereSummary summary, List<ModuleView> modules, List<CenterRef> centers,
            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    }

    // ------------------------------------------------------------------ filières d'un centre

    public record CenterFiliereView(Long filiereId, String code, String nameAr, String nameFr, int durationMonths,
                                    boolean active, Instant assignedAt) {
    }

    /** Ensemble des centres auxquels une filière est assignée (remplace l'existant). */
    public record FiliereCentersRequest(@NotNull List<Long> centerIds) {
    }

    public record CenterFilieresRequest(@NotNull List<Long> filiereIds) {
    }
}
