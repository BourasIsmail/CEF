package ma.entraidenationale.cef.hr.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ma.entraidenationale.cef.beneficiary.domain.EducationLevel;
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.beneficiary.domain.IdDocumentType;
import ma.entraidenationale.cef.hr.domain.ActivityStatus;
import ma.entraidenationale.cef.hr.domain.AdministrativeStatus;
import ma.entraidenationale.cef.hr.domain.EmployeeType;

/** Contrats d'API des ressources humaines. */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /**
     * Fiche d'un agent. La direction provinciale est celle du compte provincial (ou celle du centre) ; elle n'est
     * à fournir que par l'administrateur pour un cadre sans centre. Le centre est obligatoire sauf pour le
     * coordonnateur pédagogique (interdit) et l'administratif (facultatif).
     * Les données « Entraide Nationale » (cadre, échelle, numéro d'ordre) ne s'appliquent qu'aux agents EN.
     */
    public record Request(
            Long provincialDirectorateId,
            Long centerId,
            @NotNull EmployeeType employeeType,
            @NotNull AdministrativeStatus administrativeStatus,
            @Size(max = 255) String otherSectorText,
            @Size(max = 255) String delegatingBody,
            @NotBlank @Size(max = 200) String fullNameAr,
            @Size(max = 200) String fullNameFr,
            @NotNull Gender gender,
            LocalDate birthDate,
            @Size(max = 150) String birthPlace,
            IdDocumentType idDocumentType,
            @Size(max = 50) String idDocumentNumber,
            @Size(max = 300) String address,
            @Size(max = 30) String phone,
            @Email @Size(max = 200) String email,
            EducationLevel educationLevel,
            @Size(max = 200) String diplomaTitle,
            @Size(max = 200) String specialty,
            LocalDate diplomaDate,
            @Size(max = 500) String experienceDescription,
            String otherQualifications,
            @Size(max = 100) String enGrade,
            @Size(max = 20) String enScale,
            @Size(max = 30) String rankNumber,
            LocalDate hireDate,
            ActivityStatus activityStatus) {
    }

    public record Filter(
            String search, Long regionId, Long provinceId, Long centerId, EmployeeType type,
            AdministrativeStatus administrativeStatus, ActivityStatus activityStatus, Gender gender,
            Boolean withoutCenter) {
    }

    public record Summary(
            Long id, Long provincialDirectorateId, Long centerId, String centerNameAr,
            EmployeeType employeeType, AdministrativeStatus administrativeStatus,
            String fullNameAr, String fullNameFr, Gender gender, String phone, String rankNumber,
            LocalDate hireDate, ActivityStatus activityStatus) {
    }

    public record Detail(
            Long id, Long provincialDirectorateId, String provincialDirectorateNameAr, Long regionalDirectorateId,
            Long centerId, String centerNameAr,
            EmployeeType employeeType, AdministrativeStatus administrativeStatus, String otherSectorText, String delegatingBody,
            String fullNameAr, String fullNameFr, Gender gender, LocalDate birthDate, String birthPlace,
            IdDocumentType idDocumentType, String idDocumentNumber, String address, String phone, String email,
            EducationLevel educationLevel, String diplomaTitle, String specialty, LocalDate diplomaDate,
            String experienceDescription, String otherQualifications,
            String enGrade, String enScale, String rankNumber, LocalDate hireDate, ActivityStatus activityStatus,
            List<AssignmentView> assignments, List<TeachingView> teachings, List<GuestContractView> guestContracts,
            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    }

    /** Effectifs (les compteurs « عدد … » des fiches centre / province sont calculés, jamais saisis). */
    public record StaffSummary(
            long total, long male, long female,
            Map<EmployeeType, Long> byType, Map<AdministrativeStatus, Long> byAdministrativeStatus) {
    }

    // ---- affectations (historique)

    /** Mutation : clôt l'affectation en cours la veille de {@code fromDate} et en ouvre une nouvelle (même province). */
    public record TransferRequest(
            @NotNull LocalDate fromDate,
            Long centerId,
            @Size(max = 150) String positionTitle,
            @Size(max = 100) String decisionReference) {
    }

    public record AssignmentView(
            Long id, LocalDate fromDate, LocalDate toDate, Long provincialDirectorateId, Long centerId,
            String centerNameAr, String positionTitle, String decisionReference) {
    }

    // ---- هيئة التكوين : filières / modules enseignés

    /** {@code moduleId} vide = toute la filière. */
    public record TeachingRequest(@NotNull Long seasonId, @NotNull Long filiereId, Long moduleId) {
    }

    public record TeachingView(
            Long id, Long seasonId, String seasonLabel, Long filiereId, String filiereNameAr,
            Long moduleId, String moduleNameAr) {
    }

    // ---- المكونون المستضافون

    public record GuestContractRequest(
            @NotNull Long seasonId,
            @NotNull Long filiereId,
            @Min(1950) @Max(2100) Integer diplomaYear,
            LocalDate workStartDate,
            @PositiveOrZero BigDecimal hourlyRateMad,
            @Min(1) @Max(12) Integer monthsPerYear,
            @PositiveOrZero BigDecimal hoursPerMonth,
            @PositiveOrZero Integer beneficiariesCount) {
    }

    /** {@code estimatedAnnualCostMad} = tarif horaire × heures par mois × mois par an (si les trois sont connus). */
    public record GuestContractView(
            Long id, Long seasonId, String seasonLabel, Long filiereId, String filiereNameAr,
            Integer diplomaYear, LocalDate workStartDate, BigDecimal hourlyRateMad, Integer monthsPerYear,
            BigDecimal hoursPerMonth, Integer beneficiariesCount, BigDecimal estimatedAnnualCostMad) {
    }
}
